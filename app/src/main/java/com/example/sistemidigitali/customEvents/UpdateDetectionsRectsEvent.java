package com.example.sistemidigitali.customEvents;

import android.graphics.Matrix;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.List;

public class UpdateDetectionsRectsEvent {

    private List<Detection> detections;
    private boolean flipNeeded;
    private Matrix transformMatrix;

    public UpdateDetectionsRectsEvent(List<Detection> detections, boolean flipNeeded, Matrix transformMatrix) {
        this.detections = detections;
        this.flipNeeded = flipNeeded;
        this.transformMatrix = transformMatrix;
    }

    public List<Detection> getDetections() {
        return detections;
    }

    public boolean isFlipNeeded() {
        return flipNeeded;
    }

    public Matrix getTransformMatrix() {
        return transformMatrix;
    }
}
