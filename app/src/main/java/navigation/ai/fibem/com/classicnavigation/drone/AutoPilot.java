package navigation.ai.fibem.com.classicnavigation.drone;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.encog.app.analyst.util.AnalystUtility;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.neural.networks.BasicNetwork;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import navigation.ai.fibem.com.classicnavigation.data.MotionData;
import navigation.ai.fibem.com.classicnavigation.data.Thresholder;

/**
 * Uses a neural network to control the JS Drone based on frame by frame input
 */
public class AutoPilot {
    private JSDrone mDrone;
    private Context mContext;
    private PilotCallback mCallback;

    private BasicNetwork mNetwork;
    private AnalystUtility mUtil;

    private boolean mStopped = false;
    private double[] netInput;
    private MLData input;
    private double[] denorm;

    private MotionData motionData = new MotionData();

    // The next frame for pilot to process
    private byte[] nextFrame;

    // Synchronization
    private final Object stopSync = new Object();
    private final Object frameSync = new Object();
    private final Object motionSync = new Object();

    /**
     * Creates a new auto-pilot for the JSDrone
     *
     * @param drone    The drone to control
     * @param context  Context for loading pilot information
     * @param callback Call back for drone initialization information
     */
    public AutoPilot(JSDrone drone, Context context, PilotCallback callback) {
        mDrone = drone;
        mContext = context;
        mCallback = callback;

        loadNetworkData();
    }

    private void loadNetworkData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PilotNetwork pNetwork = PilotNetwork.getInstance(mContext);
                    mNetwork = pNetwork.getNetwork();
                    mUtil = pNetwork.getUtility();

                    netInput = new double[mNetwork.getInputCount()];
                    input = new BasicMLData(mNetwork.getInputCount());
                    denorm = new double[mNetwork.getOutputCount()];
                } catch (Exception e) {
                    mCallback.onErrorInitializing(e);
                }

                mCallback.onPilotInitialized();
            }
        }).start();
    }

    public void setNextFrame(byte[] frame) {
        synchronized (frameSync) {
            this.nextFrame = frame;
        }
    }

    /**
     * Moves the drone based on the last recorded frame and motion information
     */
    public void move() {
        synchronized (stopSync) {
            if (mStopped) {
                return;
            }
        }

        long start = System.currentTimeMillis();
        byte[] image;

        synchronized (frameSync) {
            image = this.nextFrame;
            this.nextFrame = null;
        }

        // No frame, no motion
        if (image == null) {
            mDrone.setFlag((byte) 0);
            motionData.updateMotion((byte) 0, (byte) 0);
            return;
        }

        // Convert from bytes to image (and load as grayscale)
        Mat img = Imgcodecs.imdecode(new MatOfByte(image), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        Imgproc.resize(img, img, new Size(32, 24));     // Resize
        Thresholder.Threshold(img, img);                // Threshold

        // Convert to double array for network
        netInput[0] = motionData.getPrevTurnSpeed();
        netInput[1] = motionData.getPrevForwardSpeed();
        byte[] imgArray = toByteArray(img);

        for (int i = 0; i < imgArray.length; i++)
            netInput[i + 2] = imgArray[i];

        // Normalize for input to network
        mUtil.encode(true, false, netInput, input);

        // Feed to network
        MLData output = mNetwork.compute(input);

        // Denormalize network output
        mUtil.decode(false, true, denorm, output);

        final byte newTurn = (byte) denorm[0];
        final byte newForward = (byte) denorm[1];

        // Update data or update drone directly
        synchronized (motionSync) {
            mDrone.setSpeed(newForward);
            mDrone.setTurn(newTurn);
            mDrone.setFlag((byte) 1);
            motionData.updateMotion(newForward, newTurn);
        }

        long stop = System.currentTimeMillis();
        Log.d("AutoPilot", "Move time: " + (stop - start) + "ms");

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mCallback.onMotionUpdated(motionData.copy());
            }
        });
    }

    public void stop() {
        byte newTurn = (byte) 0;
        byte newForward = (byte) 0;

        synchronized (motionSync) {
            mDrone.setFlag((byte) 0);
            motionData.updateMotion(newForward, newTurn);
        }

        synchronized (stopSync) {
            mStopped = true;
        }
    }

    /**
     * Change from stopped state to allow network output to influence motion
     */
    public void reset() {
        synchronized (stopSync) {
            mStopped = false;
        }
    }

    private static byte[] toByteArray(Mat img) {
        int bufferSize = img.channels() * img.cols() * img.rows();
        byte[] buffer = new byte[bufferSize];
        img.get(0, 0, buffer);

        return buffer;
    }

    public interface PilotCallback {
        /**
         * Called to indicate that the auto pilot was successfully initialized
         */
        void onPilotInitialized();

        /**
         * Called when there is an error initializing the auto pilot
         *
         * @param ex The exception that was thrown
         */
        void onErrorInitializing(Exception ex);

        void onMotionUpdated(MotionData data);
    }
}