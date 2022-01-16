package com.example.sistemidigitali.enums;

public enum MaskTypeEnum {

    SRGM("Surgical Mask"),
    NMDM("Non Medical Mask"),
    DRWV("Disposable Respirator With Valve"),
    DRNV("Disposable Respirator Without Valve");

    private String name;

    MaskTypeEnum(String name) {
        this.name = name;
    }

    public String getFullName() {
        return this.name;
    }
}