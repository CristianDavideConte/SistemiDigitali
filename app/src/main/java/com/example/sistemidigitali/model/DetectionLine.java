package com.example.sistemidigitali.model;

public class DetectionLine {
    private final double startX, startY;
    private final double endX, endY;
    private final String info;
    private final int lineColor;
    private final int textColor;
    private final float startLineSize, endLineSize;

    public DetectionLine(double startX, double startY, double endX, double endY, String info, int lineColor, int textColor, float startLineSize, float endLineSize) {
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
        return (float)startX;
    }

    public float getStartY() {
        return (float)startY;
    }

    public float getEndX() {
        return (float)endX;
    }

    public float getEndY() {
        return (float)endY;
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
