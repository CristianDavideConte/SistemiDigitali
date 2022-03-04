package com.example.sistemidigitali.model;

import com.example.sistemidigitali.enums.ColorsEnum;

public class DetectionLine {
    private final float startX, startY;
    private final float endX, endY;
    private final ColorsEnum lineColor;
    private final ColorsEnum textColor;
    private final String info;

    public DetectionLine(float startX, float startY, float endX, float endY, ColorsEnum lineColor, ColorsEnum textColor, String info) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.textColor = textColor;
        this.lineColor = lineColor;
        this.info = info;
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

    public ColorsEnum getTextColorType() {
        return textColor;
    }

    public ColorsEnum getLineColorType() {
        return lineColor;
    }

    public String getInfo() {
        return info;
    }
}
