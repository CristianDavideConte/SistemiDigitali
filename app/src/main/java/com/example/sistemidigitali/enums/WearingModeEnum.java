package com.example.sistemidigitali.enums;

import android.graphics.Color;

import com.example.sistemidigitali.enums.ColorsEnum;

public enum WearingModeEnum {

    MRNW("Mask Not Worn", ColorsEnum.RED.getColor(), Color.WHITE),
    MRCW("Mask Correctly Worn", ColorsEnum.GREEN.getColor(), Color.WHITE),
    MSFC("Mask Folded Above The Chin", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRHN("Mask Hanging From An Ear", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRFH("Mask On The Forehead", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRTN("Mask On The Tip Of The Nose", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRNC("Mask Under The Chin", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRNN("Mask Under The Nose", ColorsEnum.YELLOW.getColor(), Color.BLACK);

    private String name;
    private int backgroundColor;
    private int textColor;

    WearingModeEnum(String name, int backgroundColor, int textColor) {
        this.name = name;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
    }

    public String getFullName() {
        return this.name;
    }
    public int getBackgroundColor() { return this.backgroundColor; }
    public int getTextColor() {
        return textColor;
    }
}
