package com.example.sistemidigitali.customEvents;

import android.graphics.Matrix;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.List;

public class UpdateDetectionsRectsEvent {

    private List<Detection> detections;
    private float rectsWidth;
    private float rectsHeight;
    private boolean flipNeeded;
    private Matrix transformMatrix;

    public UpdateDetectionsRectsEvent(List<Detection> detections, float rectsWidth, float rectsHeight, boolean flipNeeded, Matrix transformMatrix) {
        this.detections = detections;
        this.rectsWidth = rectsWidth;
        this.rectsHeight = rectsHeight;
        this.flipNeeded = flipNeeded;
        this.transformMatrix = transformMatrix;
    }

    public List<Detection> getDetections() {
        return detections;
    }

    public float getRectsWidth() {
        return rectsWidth;
    }

    public float getRectsHeight() {
        return rectsHeight;
    }

    public boolean isFlipNeeded() {
        return flipNeeded;
    }

    public Matrix getTransformMatrix() {
        return transformMatrix;
    }
}
