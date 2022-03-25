package com.example.sistemidigitali.customEvents;

import android.graphics.Bitmap;

import java.util.List;

public class PictureTakenEvent {

    private String error;
    private List<Bitmap> frames;
    private boolean isFromGallery;

    public PictureTakenEvent(List<Bitmap> frames, String error, boolean isFromGallery) {
        this.frames = frames;
        this.error = error;
        this.isFromGallery = isFromGallery;
    }

    public List<Bitmap> getFrames() {
        return frames;
    }
    public String getError() {
        return error;
    }
    public boolean isFromGallery() {
        return isFromGallery;
    }
}
