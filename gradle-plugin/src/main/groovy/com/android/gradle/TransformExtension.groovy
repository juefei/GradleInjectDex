package com.android.gradle;

/**
 * Created by tudi on 17/5/27.
 */

public class TransformExtension {

    // 是否开启日志调试
    boolean debugMode = false;

    // 待排除不做插桩类列表，如
    // [ com/android/test/MainActivity.class, xx ]
    List<String> excludeClasses;

    // 待排除不做插桩jar列表,如
    // []
    List<String> excludeJars;


    @Override
    public String toString() {
        return "TransformExtension{" +
                "debugMode=" + debugMode +
                ", excludeClasses=" + excludeClasses +
                ", excludeJars=" + excludeJars +
                '}';
    }
}
