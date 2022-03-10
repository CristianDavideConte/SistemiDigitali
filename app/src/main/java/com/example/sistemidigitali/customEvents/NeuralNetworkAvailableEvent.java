package com.example.sistemidigitali.customEvents;

import android.content.Context;

public class NeuralNetworkAvailableEvent {
    private Context context;

    public NeuralNetworkAvailableEvent(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }
}
