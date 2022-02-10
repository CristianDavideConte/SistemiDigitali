package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.opencv.android.Utils;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVInterface;

public class DistanceCalculator {

    public DistanceCalculator() {

    }

    public Bitmap calculate(Bitmap bitmap) {
        Mat bitmapMat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(bitmap, bitmapMat);

        Mat croppedRight = getCropped(bitmapMat, 1.3F, +100, 0);
        Mat croppedLeft  = getCropped(bitmapMat, 1.3F, -100, 0);

        Mat disparity = createDisparityMap(croppedLeft, croppedRight);

        disparity.convertTo(disparity, CvType.CV_8UC4);
        bitmap = Bitmap.createBitmap(disparity.cols(), disparity.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(disparity, bitmap);

        return bitmap;
    }

    public Mat getCropped(Mat image, float zoomingFactor, int traslationX, int traslationY) {
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

        int numDisparity = (int)(left.size().width / 8);
        //int numDisparity = 16;

        StereoSGBM stereoAlgo = StereoSGBM.create(
                0,    // min Disparities
                numDisparity, // numDisparities
                11,   // SADWindowSize
                2*11*11,   // 8*number_of_image_channels*SADWindowSize*SADWindowSize   // p1
                5*11*11,  // 8*number_of_image_channels*SADWindowSize*SADWindowSize  // p2

                -1,   // disp12MaxDiff
                63,   // prefilterCap
                10,   // uniqueness ratio
                0, // speckleWindowSize
                32, // speckle Range
                0); // full DP
        // create the DisparityMap - SLOW: O(Width*height*numDisparity)
        stereoAlgo.compute(left, right, disparity);

        Core.normalize(disparity, disparity, 0, 256, Core.NORM_MINMAX);

        return disparity;
    }

}
