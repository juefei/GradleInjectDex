package com.android.test;

/**
 * Created by tudi on 17/5/23.
 */

import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

    public static void getCostTime(String var0, String var1, String var2) {
        try {
            String var3 = var0 + var1 + var2;
            long var4 = ((Long)endTimes.get(var3)).longValue() - ((Long)startTimes.get(var3)).longValue();
            System.out.println(var3 + " cost: " + var4 + " ms!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeTopCostMethod() {
        System.out.println("writeTopCostMethod sort by bundleName");
        Map<String, List<String>> bundleCostMap = new HashMap<>();
        for(Map.Entry<Object, Object> mapping : startTimes.entrySet()) {
            String key = (String) mapping.getKey();
            if(endTimes.get(key) == null) {
                System.out.println("writeTopCostMethod ignore key = " + key);
                continue;
            }

            long costTime = ((Long) endTimes.get(key)) - ((Long) mapping.getValue());
            String bundleName = key.split("@")[0];
            if(bundleCostMap.containsKey(bundleName)) {
                List<String> list = bundleCostMap.get(bundleName);
                if(list != null) {
                    list.add(key + "%" + costTime);
                }
            } else {
                List<String> list = new ArrayList<>();
                list.add(key + "%" + costTime);
                bundleCostMap.put(bundleName, list);
            }
        }

        File fileDir = new File(Environment.getExternalStorageDirectory(), "costTimeTrack");
        if(fileDir.exists()) {
            fileDir.delete();
        }
        fileDir.mkdirs();
        if(fileDir.getUsableSpace()/1024/1024 < 10) {
            System.out.println("writeTopCostMethod space is failed!");
        }

        System.out.println("writeTopCostMethod sort by costTime in the same bundle");
        for(Map.Entry<String, List<String>> entry : bundleCostMap.entrySet()) {
            List<String> list = entry.getValue();
            Collections.sort(list, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    long costTime1 = Long.parseLong(o1.split("%")[1]);
                    long costTime2 = Long.parseLong(o2.split("%")[1]);
                    return (int)(costTime2 - costTime1);
                }
            });

            System.out.println(String.format("bundle[%s] top%d methods print start\n", entry.getKey(), 50));
            int i = 0;
            for(String value : list) {
                System.out.println(value);

                i++;
                if(i == 50) {
                    break;
                }
            }
            System.out.println(String.format("bundle[%s] top%d methods print end\n", entry.getKey(), 50));


            File file = new File(fileDir, entry.getKey() + ".txt");
            FileWriter writer;
            BufferedWriter bw;
            try {
                writer = new FileWriter(file);
                bw = new BufferedWriter(writer);
                for(String value : list) {
                    bw.write(value.split("%")[0] + " " + value.split("%")[1] + "ms\n");
                }
                bw.close();
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
