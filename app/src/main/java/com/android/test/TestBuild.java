package com.android.test;

/**
 * Created by tudi on 17/5/23.
 */

import java.util.HashMap;
import java.util.Map;

public class TestBuild {
    private static Map<Object, Object> startTimes = new HashMap<>();
    private static Map<Object, Object> endTimes = new HashMap<>();

    public TestBuild() {

    }

    public static void setStartTime(String var0) {
        long var1 = System.currentTimeMillis();
        Long var2 = Long.valueOf(var1);
        startTimes.put((Object)var0, (Object)var2);
    }

    public static void setEndTime(String var0) {
        long var1 = System.currentTimeMillis();
        Long var2 = Long.valueOf(var1);
        endTimes.put((Object)var0, (Object)var2);
    }

    public static long getCostTime(String var0, String var1, String var2) {
        String var3 = var0 + var1 + var2;
        long var4 = ((Long)endTimes.get(var3)).longValue() - ((Long)startTimes.get(var3)).longValue();
        System.out.println(var3 + " cost: " + var4 + " ms!");
        return var4;
    }

}
