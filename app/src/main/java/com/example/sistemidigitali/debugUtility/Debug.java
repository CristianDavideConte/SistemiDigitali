package com.example.sistemidigitali.debugUtility;

import android.util.Log;

public class Debug {

    public Debug(){}

    public static void print(Object... objs) {
        StringBuilder s = new StringBuilder();
        for(Object obj : objs) {
            s.append(obj).append(" ");
        }
        System.out.print(s.toString());
    }

    public static void println(Object... objs) {
        StringBuilder s = new StringBuilder();
        for(Object obj : objs) {
            s.append(obj).append(" ");
        }
        System.out.println(s.toString());
    }
}
