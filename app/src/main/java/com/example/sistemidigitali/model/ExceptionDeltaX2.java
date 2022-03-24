package com.example.sistemidigitali.model;

public class ExceptionDeltaX2 extends Exception{
    private double errorValue;
    public ExceptionDeltaX2(double errorValue) {
        super();
        this.errorValue = errorValue;
    }

    public double getErrorValue() {
        return errorValue;
    }

    public void setErrorValue(double errorValue) {
        this.errorValue = errorValue;
    }
}
