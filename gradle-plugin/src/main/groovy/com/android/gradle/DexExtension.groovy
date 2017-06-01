package com.android.gradle;

/**
 * Created by tudi on 17/6/1.
 */

public class DexExtension {

    int thresholdInMainThread = 16;  // 16ms

    int thresholdInOtherThread = 500;  // 500ms

    // 待排除不做插桩类列表，如
    // [ com/android/test/MainActivity.class, xx ]
    List<String> excludeClasses;

    // 待排除不做插桩jar列表,如
    // []
    List<String> excludeJars;

    @Override
    public String toString() {
        return "DexExtension{" +
                "thresholdInMainThread=" + thresholdInMainThread +
                ", thresholdInOtherThread=" + thresholdInOtherThread +
                ", excludeClasses=" + excludeClasses +
                ", excludeJars=" + excludeJars +
                '}';
    }

}
