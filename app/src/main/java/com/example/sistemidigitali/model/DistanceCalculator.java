package com.example.sistemidigitali.model;

import android.graphics.Bitmap;
import android.util.SizeF;

import com.example.sistemidigitali.views.CameraProviderView;

import org.opencv.android.Utils;
import org.opencv.calib3d.StereoMatcher;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.ximgproc.DisparityWLSFilter;

public class DistanceCalculator {

    private Mat disparityMap;
    private Bitmap disparityBitmap;

    public DistanceCalculator() {
        this.disparityMap = null;
        this.disparityBitmap = null;
    }

    public Mat getDisparityMap() {
        return disparityMap;
    }

    public synchronized Bitmap getDisparityBitmap(Bitmap frame1, Bitmap frame2) {
        if(this.disparityMap == null) {
            Mat frame1Mat = new Mat(frame1.getWidth(), frame1.getHeight(), CvType.CV_8UC1);
            Mat frame2Mat = new Mat(frame2.getWidth(), frame2.getHeight(), CvType.CV_8UC1);
            Utils.bitmapToMat(frame1, frame1Mat);
            Utils.bitmapToMat(frame2, frame2Mat);

            Mat disparityMat = createDisparityMap(frame1Mat, frame2Mat);
            disparityMat.convertTo(disparityMat, CvType.CV_8UC1);

            this.disparityMap = disparityMat;
            this.disparityBitmap = Bitmap.createBitmap(this.disparityMap.cols(), this.disparityMap.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(this.disparityMap, disparityBitmap);
        }

        return this.disparityBitmap;
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

        double z1 =  (effective_focal_length * distance_in_pixel * 0.26) / colors1[0];
        double z2 =  (effective_focal_length * distance_in_pixel * 0.26) / colors2[0];
        double distance = Math.sqrt(Math.pow(p2.x-p1.x,2)+Math.pow(p2.y-p1.y,2)+Math.pow(z2-z1,2));

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

        final int minDisparity = -16;
        final int numDisparity = left.width() / 16 + left.width() % 16;
        final int blockSize = 11; //odd number >= 1
        final int numberOfChannels = 1;

        //https://docs.opencv.org/3.4/javadoc/org/opencv/calib3d/StereoSGBM.html#create(int,int,int,int,int,int,int,int,int,int,int)
        StereoSGBM stereoAlgo = StereoSGBM.create(
                minDisparity, // min Disparities
                numDisparity, // num Disparities
                blockSize,    // block size
                8 * numberOfChannels * blockSize * blockSize, // p1
                16 * numberOfChannels * blockSize * blockSize, // p2
                0,   // disp12MaxDiff
                63,     // prefilterCap
                5,  // uniqueness ratio
                0, // sreckleWindowSize
                32,    // spreckle Range
                StereoSGBM.MODE_SGBM_3WAY);
        // create the DisparityMap - SLOW: O(Width*height*numDisparity)

        stereoAlgo.compute(left, right, disparity);
        Core.normalize(disparity, disparity, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1);



        return disparity;
    }

}
