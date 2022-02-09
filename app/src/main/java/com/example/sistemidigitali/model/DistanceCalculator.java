package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class DistanceCalculator {

    public DistanceCalculator() {

    }

    public Bitmap calculate(Bitmap bitmap) {
        Mat src = new Mat();
        Mat dest = new Mat();
        Utils.bitmapToMat(bitmap, src);

        src.submat(new Rect(0, 0, 100, bitmap.getHeight())).copyTo(dest.submat(new Rect(bitmap.getWidth() - 100, 0, 100, bitmap.getHeight())));

        Utils.matToBitmap(dest, bitmap);

        return bitmap;
    }

    private Mat createDisparityMap(Mat rectLeft, Mat rectRight){

        // Converts the images to a proper type for stereoMatching
        Mat left = new Mat();
        Mat right = new Mat();

        Imgproc.cvtColor(rectLeft, left, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(rectRight, right, Imgproc.COLOR_BGR2GRAY);

        // Create a new image using the size and type of the left image
        Mat disparity = new Mat(left.size(), left.type());

        int numDisparity = (int)(left.size().width/8);

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
