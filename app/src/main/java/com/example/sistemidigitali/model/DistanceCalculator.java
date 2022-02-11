package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.SizeF;

import com.example.sistemidigitali.debugUtility.Debug;
import com.example.sistemidigitali.views.CameraProviderView;

import org.opencv.android.Utils;
import org.opencv.calib3d.StereoBM;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVInterface;

import java.util.Arrays;

public class DistanceCalculator {

    private Mat disparity;

    public DistanceCalculator() {

    }

    public Mat getDisparity() {
        return disparity;
    }

    public Bitmap getDisparityMap(Bitmap frame1, Bitmap frame2) {
        Mat frame1Mat = new Mat(frame1.getWidth(), frame1.getHeight(), CvType.CV_8UC1);
        Mat frame2Mat = new Mat(frame2.getWidth(), frame2.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(frame1, frame1Mat);
        Utils.bitmapToMat(frame2, frame2Mat);

        Mat disparityMat = createDisparityMap(frame1Mat, frame2Mat);
        //Mat disparityMat = createDisparityMap(getResized(frame1Mat, 1.5F), getResized(frame2Mat, 1.5F));
        disparityMat.convertTo(disparityMat, CvType.CV_8UC1);

        this.disparity = disparityMat;

        Bitmap disparityBitmap = Bitmap.createBitmap(disparityMat.cols(), disparityMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(disparityMat, disparityBitmap);
        disparityMat.depth();

        disparityMat.get(3,2);
        return disparityBitmap;
    }

    public double getDistance(Point p1, Point p2, float distance_in_pixel, Mat disparity) {
        float[] focal_length = CameraProviderView.cameraCharacteristics.get(CameraProviderView.cameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF sensor_size = CameraProviderView.cameraCharacteristics.get(CameraProviderView.cameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float effective_focal_length = (float) Math.sqrt(sensor_size.getHeight() * sensor_size.getHeight() + sensor_size.getWidth() * sensor_size.getWidth());

        //disparity = disparity.astype(np.float32) / 16.0;
        //double z1 = (effective_focal_length * distance_in_pixel * 0.26) / disparity.get( (int) Math.round(p1.x), (int) Math.round(p1.y));
        //double z2 = (effective_focal_length * distance_in_pixel * 0.26) / disparity.get(p2.x,p2.y);

        disparity.convertTo(disparity, CvType.CV_8UC1);
        double[] colors1 = disparity.get((int) Math.round(p1.x),(int)Math.round(p1.y));
        double[] colors2 = disparity.get((int) Math.round(p2.x),(int)Math.round(p2.y));

        println("INIT", colors1.length, colors2.length);
        Arrays.stream(colors1).forEach(Debug::println);
        println("--------------------------");
        Arrays.stream(colors2).forEach(Debug::println);

        double z1 =  (effective_focal_length * distance_in_pixel * 0.26) / colors1[0];
        double z2 =  (effective_focal_length * distance_in_pixel * 0.26) / colors2[0];
        double distance = Math.sqrt(Math.pow(p2.x-p1.x,2)+Math.pow(p2.y-p1.y,2)+Math.pow(z2-z1,2));
        println(z2,
                z1,
                Math.pow(z2-z1,2),
                Math.sqrt(-2),
                Math.sqrt(Math.pow(100000000000000000000000000F,1000000000000000000000000000000000000F))
        );

        return distance;
    }

    private Mat getResized(Mat image, float scalingFactor) {
        int height = (int) (image.height() / scalingFactor);
        int width  = (int) (image.width()  / scalingFactor);

        Size scale = new Size(width, height);
        Mat resizeImage = new Mat();

        Imgproc.resize(image, resizeImage, scale, 0,0, Imgproc.INTER_AREA);
        return resizeImage;
    }

    private Mat getCropped(Mat image, float zoomingFactor, int traslationX, int traslationY) {
        int height = (int) (image.height() / zoomingFactor);
        int width  = (int) (image.width()  / zoomingFactor);

        int traslationXDirection = traslationX > 0 ? 1 : -1;
        int traslationYDirection = traslationY > 0 ? 1 : -1;
        traslationX = Math.min(traslationX * traslationXDirection, (image.width()  - width)  / 2);
        traslationY = Math.min(traslationY * traslationYDirection, (image.height() - height) / 2);

        int left = image.width()  / 2 - width  / 2 + traslationX * traslationXDirection;
        int top  = image.height() / 2 - height / 2 + traslationY * traslationYDirection;

        Rect crop = new Rect(left, top, width, height);
        return new Mat(image, crop);
    }

    private Mat createDisparityMap(Mat rectLeft, Mat rectRight){
        // Converts the images to a proper type for stereoMatching
        Mat left = new Mat();
        Mat right = new Mat();

        Imgproc.cvtColor(rectLeft, left, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.cvtColor(rectRight, right, Imgproc.COLOR_RGBA2GRAY);

        // Create a new image using the size and type of the left image
        Mat disparity = new Mat(left.size(), rectLeft.type());

        int numDisparity = (int)(left.size().width / 16);
        int SADWindowSize = 11;

        //https://docs.opencv.org/3.4/javadoc/org/opencv/calib3d/StereoSGBM.html#create(int,int,int,int,int,int,int,int,int,int,int)
        StereoSGBM stereoAlgo = StereoSGBM.create(
                0,    // min DIsparities
                numDisparity, // numDisparities
                SADWindowSize,   // SADWindowSize
                8*SADWindowSize*SADWindowSize,   // 8*number_of_image_channels*SADWindowSize*SADWindowSize   // p1
                8*SADWindowSize*SADWindowSize,  // 8*number_of_image_channels*SADWindowSize*SADWindowSize  // p2

                -1,   // disp12MaxDiff
                63,   // prefilterCap
                10,   // uniqueness ratio
                0, // sreckleWindowSize
                32, // spreckle Range
                0); // full DP
        // create the DisparityMap - SLOW: O(Width*height*numDisparity)

        stereoAlgo.compute(left, right, disparity);

        Core.normalize(disparity, disparity, 0, 256, Core.NORM_MINMAX);

        return disparity;
    }
}
