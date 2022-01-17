package com.example.sistemidigitali.enums;

import android.graphics.Color;

import com.example.sistemidigitali.enums.ColorsEnum;

public enum WearingModeEnum {

    MRNW("Mask not Worn", ColorsEnum.RED.getColor(), Color.WHITE),
    MRCW("Mask Correctly Worn", ColorsEnum.GREEN.getColor(), Color.WHITE),
    MSFC("Mask Folded above the Chin", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRHN("Mask Hanging from an Ear", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRFH("Mask on the Forehead", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRTN("Mask on the Tip of the Nose", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRNC("Mask Under the Chin", ColorsEnum.YELLOW.getColor(), Color.BLACK),
    MRNN("Mask Under the Nose", ColorsEnum.YELLOW.getColor(), Color.BLACK);

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
