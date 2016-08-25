package navigation.ai.fibem.com.classicnavigation.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.parrot.arsdk.arcontroller.ARFrame;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import navigation.ai.fibem.com.classicnavigation.data.Thresholder;

public class JSVideoView extends ImageView {
    private static final String TAG = "JSVideoView";
    private final Handler mHandler;
    private Bitmap mBmp;

    public JSVideoView(Context context) {
        super(context);

        // Needed because setImageBitmap should be called on the main thread
        mHandler = new Handler(context.getMainLooper());
        customInit();
    }

    public JSVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Needed because setImageBitmap should be called on the main thread
        mHandler = new Handler(context.getMainLooper());
        customInit();
    }

    public JSVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // Needed because setImageBitmap should be called on the main thread
        mHandler = new Handler(context.getMainLooper());
        customInit();
    }

    private void customInit() {
        setScaleType(ScaleType.CENTER_CROP);
    }

    public void displayFrame(ARFrame frame) {
        byte[] data = frame.getByteData();
        synchronized (this) {
            mBmp = BitmapFactory.decodeByteArray(data, 0, data.length);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    setImageBitmap(mBmp);
                }
            }
        });
    }

    public void displayFrame(byte[] frame) {
        synchronized (this) {
            mBmp = BitmapFactory.decodeByteArray(frame, 0, frame.length);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    setImageBitmap(mBmp);
                }
            }
        });
    }

    /**
     * Optionally thresholds then displays a frame received from the drone
     *
     * @param frame     The frame received
     * @param threshold True to threshold, false otherwise
     * @return
     */
    public void displayFrame(byte[] frame, boolean threshold) {
        if (!threshold) {
            displayFrame(frame);
        }

        // Load as grayscale then threshold
        Mat img = Imgcodecs.imdecode(new MatOfByte(frame), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        Thresholder.Threshold(img, img);

        synchronized (this) {
            mBmp = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(img, mBmp);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    setImageBitmap(mBmp);
                }
            }
        });
    }
}