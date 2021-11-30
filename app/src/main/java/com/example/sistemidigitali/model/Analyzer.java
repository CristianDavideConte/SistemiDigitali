package com.example.sistemidigitali.model;

import android.annotation.SuppressLint;
import android.media.Image;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

public class Analyzer implements ImageAnalysis.Analyzer {
    private FaceDetectorOptions options;
    private FaceDetector detector;

    public Analyzer() {
        // Real-time contour detection
        this.options = new FaceDetectorOptions.Builder()
                      .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                      .build();
        this.detector = FaceDetection.getClient(options);
    }

    public void analyze(ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());


            System.out.println("TUTTO OK");
        }
    }
}
