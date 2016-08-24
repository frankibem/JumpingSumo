package navigation.ai.fibem.com.classicnavigation.data;

import android.os.Environment;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class MotionRecorder {
    private static final String TAG = "MotionRecorder";

    /**
     * Number of frames to skip
     */
    private int framesToSkip;
    private int framesSkipped = 0;

    private int runNumber;
    private int imageNumber = 0;
    private boolean checkFolder = true;
    private String runFolderName;
    private File runFolder;

    /**
     * Create a default recorder which starts runs from 1 and skips 10 frames
     */
    public MotionRecorder() {
        this(1, 10);
    }

    /**
     * Create a recorder which starts from the specified run number
     *
     * @param skipCount The number of frames to skip
     */
    public MotionRecorder(int skipCount) {
        this(1, skipCount);
    }

    /**
     * Create a recorder which starts from the specified run number
     *
     * @param initialRunNumber Run number to start from
     * @param skipCount        The number of frames to skip
     */
    public MotionRecorder(int initialRunNumber, int skipCount) {
        this.runNumber = initialRunNumber;
        this.framesToSkip = skipCount;
        runFolderName = "run " + runNumber;
    }

    /**
     * Increments the run number and saves new data in a new folder
     */
    public void reset() {
        runNumber++;
        runFolderName = "run " + runNumber;
        imageNumber = 0;
        checkFolder = true;
    }

    /**
     * Save the given data to the device's external storage
     *
     * @param motion The motion of the JS drone
     * @param data   The byte representation of the current frame
     * @remark If the run folder already exists, its contents will be deleted and overwritten
     */
    public synchronized void record(MotionData motion, byte[] data)
            throws IOException {
        if (checkFolder) {
            File external = Environment.getExternalStorageDirectory();
            runFolder = new File(external, runFolderName);
            Log.i(TAG, "Writing images to folder: " + runFolder.getAbsolutePath());

            if (runFolder.exists()) {
                for (File file : runFolder.listFiles()) {
                    file.delete();
                }
            } else {
                if (!runFolder.mkdir()) {
                    throw new IOException("Could not create run folder");
                }
            }
            checkFolder = false;
        }

        if (framesSkipped == 0) {
            if (data == null) {
                Log.i("MotionRecorder", "frame data is null");
                return;
            }

            String fileName = String.format(Locale.US, "run%d_%d_%d_%d_%d_%d.png",
                    runNumber, imageNumber,
                    motion.getPrevTurnSpeed(), motion.getPrevForwardSpeed(),
                    motion.getTurnSpeed(), motion.getForwardSpeed());
            File file = new File(runFolder, fileName);

            Mat img = Imgcodecs.imdecode(new MatOfByte(data), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
            Imgproc.resize(img, img, new Size(32, 24));
            Imgcodecs.imwrite(file.getAbsolutePath(), img);
            Log.i(TAG, "Wrote data: " + fileName);

            imageNumber++;
        } else {
            framesSkipped = (framesSkipped + 1) % framesToSkip;
        }
    }
}