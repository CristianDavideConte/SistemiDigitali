package com.example.sistemidigitali.model;

public class DetectionLine {
    private final float startX, startY;
    private final float endX, endY;
    private final String info;
    private final int lineColor;
    private final int textColor;
    private final float startLineWidthMultiplier, endLineWidthMultiplier;

    public DetectionLine(float startX, float startY, float endX, float endY, String info, int lineColor, int textColor, float startLineWidthMultiplier, float endLineWidthMultiplier) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.info = info;
        this.textColor = textColor;
        this.lineColor = lineColor;
        this.startLineWidthMultiplier = startLineWidthMultiplier;
        this.endLineWidthMultiplier = endLineWidthMultiplier;
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

    public float getStartLineWidthMultiplier() {
        return startLineWidthMultiplier;
    }

    public float getEndLineWidthMultiplier() {
        return endLineWidthMultiplier;
    }
}
