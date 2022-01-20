package com.example.sistemidigitali.enums;

import android.graphics.Color;

public enum MaskTypeEnum {

    SRGM("Surgical Mask"),
    NMDM("Non-Medical Mask"),
    DRWV("Respirator with Valve"),
    DRNV("Respirator without Valve"),
    TEST("Test");

    private String name;

    MaskTypeEnum(String name) {
        this.name = name;
    }

    public String getFullName() {
        return this.name;
    }
}