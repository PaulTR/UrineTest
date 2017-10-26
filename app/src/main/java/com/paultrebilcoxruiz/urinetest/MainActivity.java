package com.paultrebilcoxruiz.urinetest;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.google.android.things.contrib.driver.button.Button;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends Activity implements ImageReader.OnImageAvailableListener {
    private static final String PIN_BLUE = "BCM21";
    private Button mButton;

    private Handler mCameraBackgroundHandler;
    private HandlerThread mCameraBackgroundThread;
    private AnalysisCamera mCamera;

    private DatabaseReference databaseRef;

    private static final String RESULTS_URL = "https://iot-urine-test.firebaseio.com/";

    private static final String LEUKOCYTE_SMALL = "c3b1b1";
    private static final String LEUKOCYTE_MODERATE = "bba7c0";
    private static final String LEUKOCYTE_LARGE = "b597b9";

    private static final String NITRATE_LOWER_BOUND = "cfc7d6";
    private static final String NITRATE_UPPER_BOUND = "bc9fbd";

    private static final String PH_6_0 = "bb9358";
    private static final String PH_6_5 = "b89d5a";
    private static final String PH_7_0 = "aaa444";
    private static final String PH_7_5 = "a7ab4d";
    private static final String PH_8_0 = "99a75c";
    private static final String PH_8_5 = "588679";

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initDatabase();
        initCamera();

        try {
            mButton = new Button(PIN_BLUE, Button.LogicState.PRESSED_WHEN_HIGH);

            mButton.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean b) {
                    if (!b) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mCamera.takePicture();
                            }
                        }, 65000);
                    }
                }
            });
        } catch( IOException e ) {

        }
    }

    private void initDatabase() {
        databaseRef = FirebaseDatabase.getInstance().getReferenceFromUrl(RESULTS_URL);
    }

    private void initCamera() {
        mCameraBackgroundThread = new HandlerThread("CameraInputThread");
        mCameraBackgroundThread.start();
        mCameraBackgroundHandler = new Handler(mCameraBackgroundThread.getLooper());

        mCamera = AnalysisCamera.getInstance();
        mCamera.initializeCamera(this, mCameraBackgroundHandler, this);
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Image image = imageReader.acquireLatestImage();
        ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
        final byte[] imageBytes = new byte[imageBuf.remaining()];
        imageBuf.get(imageBytes);
        image.close();

        onPictureTaken(imageBytes);
    }

    private void onPictureTaken(byte[] bytes) {
        if (bytes != null) {
            Bitmap bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
            Bitmap leukocytesSquare = getSquare(bitmapImage, 1);
            Bitmap nitriteSquare = getSquare(bitmapImage, 2);
            Bitmap phSquare = getSquare(bitmapImage, 3);

            String leukocytesColor = getColorHex(getDominantColor(leukocytesSquare));
            String nitrateColor = getColorHex(getDominantColor(nitriteSquare));
            String phColor = getColorHex(getDominantColor(phSquare));

            uploadResults(getLeukocytesReading(leukocytesColor), getNitrateReading(nitrateColor), getPhLevel(phColor));
            Log.e("Test", "leukocytes: " + getLeukocytesReading(leukocytesColor));
            Log.e("Test", "nitrates: " + getNitrateReading(nitrateColor));
            Log.e("Test", "ph: " + getPhLevel(phColor));
        }
    }

    private void uploadResults(String leukocytes, String nitrate, String ph) {
        Results results = new Results();
        results.setLeukocytes(leukocytes);
        results.setNitrates(nitrate);
        results.setPh(ph);

        DatabaseReference ref = databaseRef.push();
        ref.setValue(results);
    }

    private String getNitrateReading(String nitrateColor) {

        if( nitrateColor.toLowerCase().compareTo(NITRATE_LOWER_BOUND) >= 0
                && nitrateColor.toLowerCase().compareTo(NITRATE_UPPER_BOUND) <= 0 ) {
            return "POSITIVE";
        } else {
            return "NEGATIVE";
        }
    }

    private String getPhLevel(String phColor) {
        if( phColor.toLowerCase().compareTo(PH_6_0) < 0 ) {
            return "5.0";
        } else if( phColor.toLowerCase().compareTo(PH_6_0) >= 0 && phColor.toLowerCase().compareTo(PH_6_5) > 0 ) {
            return "6.0";
        } else if( phColor.toLowerCase().compareTo(PH_6_5) >= 0 && phColor.toLowerCase().compareTo(PH_7_0) > 0 ) {
            return "6.5";
        } else if( phColor.toLowerCase().compareTo(PH_7_0) >= 0 && phColor.toLowerCase().compareTo(PH_7_5) > 0 ) {
            return "7.0";
        } else if( phColor.toLowerCase().compareTo(PH_7_5) >= 0 && phColor.toLowerCase().compareTo(PH_8_0) > 0 ) {
            return "7.5";
        } else if( phColor.toLowerCase().compareTo(PH_8_0) >= 0 && phColor.toLowerCase().compareTo(PH_8_5) > 0) {
            return "8.0";
        } else if( phColor.toLowerCase().compareTo(PH_8_5) >= 0 ) {
            return "8.5";
        }

        return "UNKNOWN";
    }

    private String getLeukocytesReading(String color) {
        if( color.toLowerCase().compareTo(LEUKOCYTE_SMALL) <= 0 ) {
            return "TRACE";
        } else if( color.toLowerCase().compareTo(LEUKOCYTE_SMALL) >= 0 && color.toLowerCase().compareTo(LEUKOCYTE_MODERATE) <= 0) {
            return "SMALL";
        } else if( color.toLowerCase().compareTo(LEUKOCYTE_MODERATE) >= 0 && color.toLowerCase().compareTo(LEUKOCYTE_LARGE) <= 0 ) {
            return "MODERATE";
        } else if( color.toLowerCase().compareTo(LEUKOCYTE_LARGE) >= 0 ) {
            return "LARGE";
        }

        return "UNKNOWN";
    }

    private String getColorHex(int color) {
        return String.format("%06X", (0xFFFFFF & color));
    }

    public static int getDominantColor(Bitmap bitmap) {
        Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
        final int color = newBitmap.getPixel(0, 0);
        newBitmap.recycle();
        return color;
    }

    //Seriously improve this. Work on cutting out the squares in each pass initially rather than
    //continuously hunting.
    private Bitmap getSquare(Bitmap bitmapImage, int num) {
        for( int height = 0; height < bitmapImage.getHeight(); height++ ) {
            for (int width = 0; width < bitmapImage.getWidth(); width++) {
                if (isWhiteish(bitmapImage, width, height)) {
                    continue;
                } else {
                    //Check a 50 x 50 diagonal for the square
                    for( int i = 0, j = 0; i < 50 && j < 50; i++, j++ ) {
                        if( isWhiteish(bitmapImage, width + i, height + j) ) {
                            break;
                        }

                        if( i == 9 ) {
                           if( num == 1 ) {
                               return Bitmap.createBitmap(bitmapImage, width, height, 50, 50);
                           } else {
                               bitmapImage = trimToNextAllWhiteSpace(bitmapImage, height);
                               return getSquare(bitmapImage, --num);
                           }
                        }
                    }
                }
            }
        }
        return bitmapImage;
    }

    private boolean isWhiteish(Bitmap image, int width, int height) {
        return Color.red(image.getPixel(width, height)) >= 210
                && Color.green(image.getPixel(width, height)) >= 210
                && Color.blue(image.getPixel(width, height)) >= 210;
    }

    private Bitmap trimToNextAllWhiteSpace(Bitmap bitmap, int height) {
        int numOfAllWhiteRowsInSequence = 0;
        for( int i = height; i < bitmap.getHeight(); i++ ) {
            if( numOfAllWhiteRowsInSequence >= 3 ) {
                return Bitmap.createBitmap(bitmap, 0, i, bitmap.getWidth(), bitmap.getHeight() - i);
            }
            for( int j = 0; j < bitmap.getWidth(); j++ ) {
                if( !isWhiteish(bitmap, j, i) ) {
                    numOfAllWhiteRowsInSequence = 0;
                    break;
                } else if( j == bitmap.getWidth()-1 ) {
                    numOfAllWhiteRowsInSequence++;
                }
            }
        }

        return bitmap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mCameraBackgroundThread.quitSafely();
        mCamera.shutDown();
        try {
            mButton.close();
        } catch( IOException e ) {

        }
    }
}
