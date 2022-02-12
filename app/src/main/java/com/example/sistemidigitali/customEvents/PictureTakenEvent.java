package com.example.sistemidigitali.customEvents;

import android.graphics.Bitmap;

import java.util.List;

public class PictureTakenEvent {

    private String error;
    private List<Bitmap> frames;

    public PictureTakenEvent(List<Bitmap> frames, String error) {
        this.frames = frames;
        this.error = error;
    }

    public List<Bitmap> getFrames() {
        return frames;
    }

    public String getError() {
        return error;
    }
}
