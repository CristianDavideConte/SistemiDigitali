package com.example.sistemidigitali.customEvents;

import android.content.Context;

import com.example.sistemidigitali.model.CustomDepthEstimator;

public class CustomDepthEstimatorAvailableEvent {
    private Context context;
    private CustomDepthEstimator depthEstimator;

    public CustomDepthEstimatorAvailableEvent(Context context, CustomDepthEstimator depthEstimator) {
        this.context = context;
        this.depthEstimator = depthEstimator;
    }

    public Context getContext() {
        return context;
    }
    public CustomDepthEstimator getDepthEstimator() {
        return depthEstimator;
    }
}
