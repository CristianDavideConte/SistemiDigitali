package com.example.sistemidigitali.customEvents;

import android.content.Context;

import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.example.sistemidigitali.model.CustomObjectDetector;

public class NeuralNetworkAvailableEvent {
    private Context context;

    public NeuralNetworkAvailableEvent(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }
}
