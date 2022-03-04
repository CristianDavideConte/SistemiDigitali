package com.example.sistemidigitali.customEvents;

import android.content.Context;
import android.graphics.Matrix;

import com.example.sistemidigitali.model.DetectionLine;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.List;

public class UpdateDetectionsRectsEvent {

    private final Context context;
    private final List<Detection> detections;
    private final boolean flipNeeded;
    private final Matrix transformMatrix;
    private final List<DetectionLine> detectionLines;

    public UpdateDetectionsRectsEvent(Context context, List<Detection> detections, boolean flipNeeded, Matrix transformMatrix, List<DetectionLine> detectionLines) {
        this.context = context;
        this.detections = detections;
        this.detectionLines = detectionLines;
        this.flipNeeded = flipNeeded;
        this.transformMatrix = transformMatrix;
    }

    public Context getContext() {
        return context;
    }

    public List<Detection> getDetections() {
        return detections;
    }

    public List<DetectionLine> getDetectionLines() {
        return detectionLines;
    }

    public boolean isFlipNeeded() {
        return flipNeeded;
    }

    public Matrix getTransformMatrix() {
        return transformMatrix;
    }
}
