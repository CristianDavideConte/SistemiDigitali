package com.example.sistemidigitali.debugUtility;

public class Debug {

    public Debug(){}

    public static void print(Object... objs) {
        String s = "";
        for(Object obj : objs) {
            s += obj + " ";
        }
        System.out.print(s);
    }

    public static void println(Object... objs) {
        String s = "";
        for(Object obj : objs) {
            s += obj + " ";
        }
        System.out.println(s);
    }
}
