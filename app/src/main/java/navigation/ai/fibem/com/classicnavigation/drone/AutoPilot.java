package navigation.ai.fibem.com.classicnavigation.drone;

import android.content.Context;

import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.versatile.NormalizationHelper;
import org.encog.neural.networks.BasicNetwork;
import org.encog.persist.EncogDirectoryPersistence;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import navigation.ai.fibem.com.classicnavigation.R;
import navigation.ai.fibem.com.classicnavigation.data.MotionData;

/**
 * Uses a neural network to control the JS Drone based on frame by frame input
 */
public class AutoPilot {
    private JSDrone mDrone;
    private Context mContext;
    private PilotReadyCallback mCallback;

    private BasicNetwork mNetwork;
    private NormalizationHelper mNormHelper;

    private double[] netInput;

    private MotionData motionData = new MotionData();

    // The next frame for pilot to process
    private byte[] nextFrame;
    private final Object sync = new Object();

    /**
     * Creates a new auto-pilot for the JSDrone
     *
     * @param drone    The drone to control
     * @param context  Context for loading pilot information
     * @param callback Call back for drone initialization information
     */
    public AutoPilot(JSDrone drone, Context context, PilotReadyCallback callback) {
        mDrone = drone;
        mContext = context;
        mCallback = callback;

        try {
            loadNetworkData();
        } catch (Exception ex) {
            callback.onErrorInitializing(ex);
            return;
        }

        callback.onPilotInitialized();
    }

    private void loadNetworkData()
            throws IOException, ClassNotFoundException {
        // Load the neural network
        mNetwork = (BasicNetwork) EncogDirectoryPersistence.loadObject(mContext.getResources().
                openRawResource(R.raw.network));

        netInput = new double[mNetwork.getInputCount()];

        // Load the normalization helper
        InputStream is = mContext.getResources().openRawResource(R.raw.helper);
        ObjectInputStream objIn = new ObjectInputStream(is);
        mNormHelper = (NormalizationHelper) objIn.readObject();
    }

    public void setNextFrame(byte[] frame) {
        synchronized (sync) {
            this.nextFrame = frame;
        }
    }

    /**
     * Moves the drone based on the last recorded frame and motion information
     */
    public void move() {
        byte[] image;

        synchronized (sync) {
            image = this.nextFrame;
            this.nextFrame = null;
        }

        // No motion
        if (image == null) {
            mDrone.setFlag((byte) 0);
            motionData.updateMotion((byte) 0, (byte) 0);
            return;
        }

        // Convert from bytes to image (and load as grayscale)
        Mat img = Imgcodecs.imdecode(new MatOfByte(image), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        Imgproc.resize(img, img, new Size(32, 24));     // Resize

        // Convert to double array for network
        netInput[0] = motionData.getPrevTurnSpeed();
        netInput[1] = motionData.getPrevForwardSpeed();
        byte[] imgArray = toByteArray(img);

        for (int i = 0; i < imgArray.length; i++)
            netInput[i + 2] = imgArray[i];

        // Feed to network
        MLData input = new BasicMLData(netInput);
        MLData output = mNetwork.compute(input);

        // Denormalize -- Surely there has to be a better way than using strings?
        String[] outStr = mNormHelper.denormalizeOutputVectorToString(output);

        byte newTurn = (byte) (Double.parseDouble(outStr[0]));
        byte newForward = (byte) (Double.parseDouble(outStr[1]));

        // Update data or update drone directly
        mDrone.setSpeed(newForward);
        mDrone.setTurn(newTurn);
        mDrone.setFlag((byte) 1);
        motionData.updateMotion(newForward, newTurn);
    }

    private static byte[] toByteArray(Mat img) {
        int bufferSize = img.channels() * img.cols() * img.rows();
        byte[] buffer = new byte[bufferSize];
        img.get(0, 0, buffer);

        return buffer;
    }

    public interface PilotReadyCallback {
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
    }
}