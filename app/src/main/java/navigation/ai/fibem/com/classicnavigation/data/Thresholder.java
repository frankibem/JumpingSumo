package navigation.ai.fibem.com.classicnavigation.data;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class Thresholder {
    public static final int MAXVAL = 255;
    public static final int THRESHOLD = 160;

    public static void Threshold(Mat src, Mat dst) {
        Imgproc.threshold(src, dst, THRESHOLD, MAXVAL, Imgproc.THRESH_BINARY);
    }
}