package com.android.gradle

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState
import org.gradle.util.Clock;

/**
 * Created by tudi on 17/5/16.
 */

public class TimeListener implements TaskExecutionListener, BuildListener {
    private Clock clock
    private times = []

    @Override
    void beforeExecute(Task task) {
        clock = new org.gradle.util.Clock()

//        task.project.logger.error "taskName:${task.name} "
//        task.project.logger.error "inputs.files.files:-----------start------- "
//        if (task.inputs.files.files) {
//            task.inputs.files.files.each {
//                task.project.logger.error "${it.absolutePath} "
//            }
//        }
//        task.project.logger.error "inputs.files.files: -----------end---------- "
//
//        task.project.logger.error "TaskDependency: -----------start---------- "
//        task.getTaskDependencies().getDependencies(task).each { Task tmptask ->
//            task.project.logger.error "TaskDependency: ${tmptask.getName()}"
//        }
//        task.project.logger.error "TaskDependency: -----------end----------\n "
    }

    @Override
    void afterExecute(Task task, TaskState taskState) {
        def ms = clock.timeInMs
        times.add([ms, task.path])
        task.project.logger.error "${task.path} spend ${ms}ms"

//        task.project.logger.error "taskName:${task.name}"
//        task.project.logger.error "outputs.files.files: ------------start----------------- "
//        if(task.outputs.files.files) {
//            task.outputs.files.files.each {
//                task.project.logger.error "${it.absolutePath} "
//            }
//        }
//        task.project.logger.error "outputs.files.files: ---------------end------------------\n "
    }

    @Override
    void buildFinished(BuildResult result) {
        println "Task spend time too long(>=50ms):"
        for (time in times) {
            if (time[0] >= 50) {
                printf "%7sms  %s\n", time
            }
        }
    }

    @Override
    void buildStarted(Gradle gradle) {}

    @Override
    void projectsEvaluated(Gradle gradle) {}

    @Override
    void projectsLoaded(Gradle gradle) {}

    @Override
    void settingsEvaluated(Settings settings) {}
}
