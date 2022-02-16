package com.example.sistemidigitali.customEvents;

import android.graphics.Bitmap;
import android.graphics.Point;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PictureTakenEvent {

    private String error;
    private List<Bitmap> frames;
    private Map<Bitmap, float[]> framesPositions;

    public PictureTakenEvent(List<Bitmap> frames, String error) {
        this.frames = frames;
        this.error = error;
        this.framesPositions = new HashMap<>();
    }

    public PictureTakenEvent(List<Bitmap> frames, String error, Map framesPositions) {
        this.frames = frames;
        this.error = error;
        this.framesPositions = framesPositions;
    }

    public List<Bitmap> getFrames() {
        return frames;
    }

    public Map<Bitmap, float[]> getFramesPositions() {
        return framesPositions;
    }

    public String getError() {
        return error;
    }

    public void setFramesPositions(Map<Bitmap, float[]> framesPositions) {
        this.framesPositions = framesPositions;
    }
}
