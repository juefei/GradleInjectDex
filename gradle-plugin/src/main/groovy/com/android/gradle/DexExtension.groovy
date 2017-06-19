package com.android.gradle;

/**
 * Created by tudi on 17/6/1.
 */

public class DexExtension {

    boolean debugEnabled = true;

    int thresholdInMainThread = 16;  // 16ms

    int thresholdInOtherThread = 500;  // 500ms

    // 待排除不做插桩类列表，如
    // [ com/android/test/MainActivity.class, xx ]
    List<String> excludeClasses;

    // 待排除不做插桩jar列表,如
    // [com.android.support:recyclerview-v7]
    List<String> excludeJars;

    // 待排除不做插桩bundle列表，如
    // [com.taobao.android:taobao_dexmerge]
    List<String> excludeBundles;

    @Override
    public String toString() {
        return "DexExtension{" +
                "debugEnabled=" + debugEnabled
                "thresholdInMainThread=" + thresholdInMainThread +
                ", thresholdInOtherThread=" + thresholdInOtherThread +
                ", excludeClasses=" + excludeClasses +
                ", excludeJars=" + excludeJars +
                ", excludeJars=" + excludeBundles +
                '}';
    }

}
