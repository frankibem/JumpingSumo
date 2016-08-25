package navigation.ai.fibem.com.classicnavigation.data;

import android.os.Environment;
import android.util.Log;
import android.util.Pair;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class MotionRecorder {
    private static final String TAG = "MotionRecorder";

    private Queue<Pair<MotionData, byte[]>> queue = new ArrayDeque<>();
    private List<QueueUpdateListener> listeners = new ArrayList<>();

    private int framesToSkip;
    private int counter;
    private int runNumber;

    private Consumer consumer;

    public MotionRecorder(int skipCount) {
        this(1, skipCount);
    }

    public MotionRecorder(int initialRunNumber, int skipCount) {
        this.runNumber = initialRunNumber - 1;
        this.framesToSkip = skipCount;
        this.listeners = new ArrayList<>();
    }

    public void reset() {
        counter = 0;
        runNumber++;
        String runFolderName = "run " + runNumber;

        // If folder doesn't exist, create
        File external = Environment.getExternalStorageDirectory();
        File runFolder = new File(external, runFolderName);
        Log.i(TAG, "Writing images to folder: " + runFolder.getAbsolutePath());

        if (!runFolder.exists()) {
            runFolder.mkdir();
        } else {
            // Delete all files inside
            File[] content = runFolder.listFiles();
            for (File file : content) {
                file.delete();
            }
        }

        // Create and start a new consumer
        consumer = new Consumer(this, runFolder, runNumber, listeners);
        consumer.start();
    }

    /**
     * Add an item to the queue
     *
     * @param item The item to add
     */
    public synchronized void addItem(Pair<MotionData, byte[]> item) {
        if (counter == 0) {
            queue.add(item);
            counter++;

            synchronized (consumer) {
                consumer.notify();
            }

            // Notify listeners
            for (QueueUpdateListener listener : listeners) {
                listener.onItemAdded();
            }
        } else {
            if (framesToSkip <= 0) {
                // Negative number or 0, ignore (take all frames)
            } else if (counter == framesToSkip) {
                counter = 0;
            } else {
                counter++;
            }
        }
    }

    /**
     * @return Returns the next item in the queue
     */
    public synchronized Pair<MotionData, byte[]> getItem() {
        return queue.poll();
    }

    /**
     * No more items will be produced. Consume all remaining and terminate
     */
    public void finishRecording() {
        synchronized (consumer) {
            consumer.wrapUp();
            consumer.notify();
        }
    }

    public interface QueueUpdateListener {
        void onItemAdded();

        void onItemConsumed();

        void onFinishedConsumption();
    }

    public void addListener(QueueUpdateListener listener) {
        listeners.add(listener);
    }

    public int getRunNumber() {
        return runNumber;
    }
}