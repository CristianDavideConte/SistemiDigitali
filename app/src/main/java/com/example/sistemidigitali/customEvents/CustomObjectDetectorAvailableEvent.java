package com.example.sistemidigitali.customEvents;

import android.content.Context;

import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.example.sistemidigitali.model.CustomObjectDetector;

public class CustomObjectDetectorAvailableEvent {
    private CustomObjectDetectorType type;
    private Context context;
    private CustomObjectDetector objectDetector;

    public CustomObjectDetectorAvailableEvent(Context context, CustomObjectDetector objectDetector, CustomObjectDetectorType type) {
        this.context = context;
        this.objectDetector = objectDetector;
        this.type = type;
    }

    public Context getContext() {
        return context;
    }
    public CustomObjectDetector getObjectDetector() {
        return objectDetector;
    }
    public CustomObjectDetectorType getType() {
        return type;
    }
}
