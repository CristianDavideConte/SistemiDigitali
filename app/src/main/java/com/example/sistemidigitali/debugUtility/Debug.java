package com.example.sistemidigitali.debugUtility;

import android.util.Log;

public class Debug {

    public Debug(){}

    public static void print(Object... objs) {
        for(Object obj : objs) {
            System.out.print(obj.toString());
        }
    }

    public static void println(Object... objs) {
        StringBuilder s = new StringBuilder();
        for(Object obj : objs) {
            s.append(obj).append(" ");
        }
        Log.e("DEBUG", s.toString());
    }
}
