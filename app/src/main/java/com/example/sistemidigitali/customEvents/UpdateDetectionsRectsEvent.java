package com.example.sistemidigitali.customEvents;

import android.content.Context;
import android.graphics.Matrix;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.List;

public class UpdateDetectionsRectsEvent {

    private Context context;
    private List<Detection> detections;
    private boolean flipNeeded;
    private Matrix transformMatrix;

    public UpdateDetectionsRectsEvent(Context context, List<Detection> detections, boolean flipNeeded, Matrix transformMatrix) {
        this.context = context;
        this.detections = detections;
        this.flipNeeded = flipNeeded;
        this.transformMatrix = transformMatrix;
    }

    public Context getContext() {
        return context;
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
