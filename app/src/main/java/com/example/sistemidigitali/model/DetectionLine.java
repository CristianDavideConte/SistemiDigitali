package com.example.sistemidigitali.model;

public class DetectionLine {
    private final float startX, startY;
    private final float endX, endY;
    private final String info;
    private final int lineColor;
    private final int textColor;
    private final float startLineSize, endLineSize;

    public DetectionLine(float startX, float startY, float endX, float endY, String info, int lineColor, int textColor, float startLineSize, float endLineSize) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.info = info;
        this.textColor = textColor;
        this.lineColor = lineColor;
        this.startLineSize = startLineSize;
        this.endLineSize = endLineSize;
    }

    public float getStartX() {
        return startX;
    }

    public float getStartY() {
        return startY;
    }

    public float getEndX() {
        return endX;
    }

    public float getEndY() {
        return endY;
    }

    public String getInfo() {
        return info;
    }

    public int getTextColor() {
        return textColor;
    }

    public int getLineColor() {
        return lineColor;
    }

    public float getStartLineSize() {
        return startLineSize;
    }

    public float getEndLineSize() {
        return endLineSize;
    }
}
