package com.example.sistemidigitali.enums;

import android.graphics.Color;

public enum ColorsEnum {
    GREEN(Color.rgb(29, 198, 144)),
    YELLOW(Color.rgb(255, 230, 91)),
    RED(Color.rgb(240, 72, 84)),
    TEST(Color.rgb(161,106,232));

    private int color;

    ColorsEnum(int color) {
        this.color = color;
    }

    public int getColor() {
        return this.color;
    }
}
