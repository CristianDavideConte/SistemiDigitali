package com.example.sistemidigitali.model;

public class ExceptionDeltaX1 extends Exception{
    private double errorValue;
    public ExceptionDeltaX1(double errorValue) {
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
