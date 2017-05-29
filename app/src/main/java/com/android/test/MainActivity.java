package com.android.test;

import android.app.Activity;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String className = getClassName();

        TestBuild build = new TestBuild();
        build.setStartTime(className);
        build.setEndTime(className);
    }

    public String getClassName() {
        return new StringBuffer().append("aaaaaa ").append(true).append(MainActivity.class.getSimpleName()).toString();
    }

}
