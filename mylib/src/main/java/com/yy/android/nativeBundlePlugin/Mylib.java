package com.yy.android.nativeBundlePlugin;

/**
 * Created by Administrator on 2017/3/22.
 */

public class Mylib {

    public static String TAG = "Mylib";

    public static boolean isInit = false;

    static {
        try {
            System.loadLibrary("mylib");
            isInit = true;
        }
        catch (Throwable e) {
            e.printStackTrace();
            isInit = false;
        }
    }

    public void test() {

    }
}
