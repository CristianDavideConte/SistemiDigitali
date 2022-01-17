package com.example.sistemidigitali.enums;

public enum MaskTypeEnum {

    SRGM("Surgical Mask"),
    NMDM("Non-Medical Mask"),
    DRWV("Disposable Respirator with Valve"),
    DRNV("Disposable Respirator without Valve");

    private String name;

    MaskTypeEnum(String name) {
        this.name = name;
    }

    public String getFullName() {
        return this.name;
    }
}