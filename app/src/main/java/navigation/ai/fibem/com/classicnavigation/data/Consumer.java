package navigation.ai.fibem.com.classicnavigation.data;

import android.util.Log;
import android.util.Pair;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class Consumer extends Thread {
    private static final String TAG = "Consumer";

    private MotionRecorder recorder;
    private File outDir;
    private List<MotionRecorder.QueueUpdateListener> listeners;

    private int imageNumber;
    private int runNumber;
    private boolean shouldFinish = false;

    public Consumer(MotionRecorder recorder, File outDir, int runNumber, List<MotionRecorder.QueueUpdateListener> listeners) {
        this.recorder = recorder;
        this.outDir = outDir;
        this.listeners = listeners;

        this.imageNumber = 0;
        this.runNumber = runNumber;

        this.setDaemon(false);      // Not a background thread
    }

    @Override
    public void run() {
        while (true) {
            try {
                Pair<MotionData, byte[]> item = recorder.getItem();

                // Nothing in queue - wait...
                if (item == null) {
                    synchronized (this) {
                        wait();
                    }
                    if (shouldFinish) {
                        finish();
                        return;
                    }
                } else {
                    process(item);
                    if (shouldFinish) {
                        finish();
                        return;
                    }
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void finish() {
        // Process everything left in the queue
        Pair<MotionData, byte[]> file = recorder.getItem();
        while (file != null) {
            process(file);
            file = recorder.getItem();
        }

        // Notify listeners
        for (MotionRecorder.QueueUpdateListener listener : listeners) {
            listener.onFinishedConsumption();
        }
    }

    /**
     * Gray, resize, threshold image then store
     *
     * @param file
     */
    private void process(Pair<MotionData, byte[]> pair) {
        MotionData motion = pair.first;
        byte[] data = pair.second;

        String fileName = String.format(Locale.US, "run%d_%d_%d_%d_%d_%d.png",
                runNumber, imageNumber,
                motion.getPrevTurnSpeed(), motion.getPrevForwardSpeed(),
                motion.getTurnSpeed(), motion.getForwardSpeed());
        File file = new File(outDir, fileName);

        Mat img = Imgcodecs.imdecode(new MatOfByte(data), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        Imgproc.resize(img, img, new Size(32, 24));
        Thresholder.Threshold(img, img);
        Imgcodecs.imwrite(file.getAbsolutePath(), img);
        Log.i(TAG, "Wrote data: " + fileName);

        imageNumber++;

        // Notify listeners
        for (MotionRecorder.QueueUpdateListener listener : listeners) {
            listener.onItemConsumed();
        }
    }

    /**
     * No more items will be produced. Consume all remaining and terminate
     */
    public void wrapUp() {
        shouldFinish = true;
    }
}