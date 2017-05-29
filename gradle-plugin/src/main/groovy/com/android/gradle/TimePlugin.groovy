package com.android.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

public class TimePlugin implements Plugin<Project> {
    void apply(Project project) {
        project.task('testTask') << {
            println "Hello gradle plugin"
        }

        project.gradle.addListener(new TimeListener())
    }
}