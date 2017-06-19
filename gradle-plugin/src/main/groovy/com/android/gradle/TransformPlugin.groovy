package com.android.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApkVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
/**
 * Transform插件入口类，支持标准Android工程和带有插件化框架Android工程（包含多个模块bundle）。
 *
 * 带有插件化框架Android工程构建过程中，主要task执行顺序:
 *
 *  prepareXXXX(groupId+artifactId)Library  (output：build/intermediates/exploded-aar/)
 *      |
 *      |
 *      V
 *  prepareAwbsDebug  (output：build/intermediates/exploded-awb/)
 *      |
 *      |
 *      V
 *  compileDebugJavaWithJavac (output：build/intermediates/classes/debug, build/intermediates/dependency-cache/debug)
 *      |
 *      |
 *      V
 *  javacAwbsDebug[compileAwbDebugJavaWithJavac]  (output：build/intermediates/awb-classes/debug, build/intermediates/awb-dependency-cache/debug)
 *      |
 *      |
 *      V
 *  transformClassesWithInjectMainDexForDebug （output：build/intermediates/transforms/InjectMainDex/debug）
 *      |
 *      |
 *      V
 *  transformClassesWithJarOptForDebug (output: build/intermediates/transforms/jarOpt/debug, build/intermediates/awb-jaropt)
 *      |
 *      |
 *      V
 *  transformClassesAndResourcesWithProguardForDebug （output：build/intermediates/transforms/proguard/debug, build/intermediates/classes-proguard/debug）
 *      |
 *      |
 *      V
 *  transformClassesWithDexForDebug （output：build/intermediates/transforms/dex/debug）
 *      |
 *      |
 *      V
 *  packageDebugAwbs ( dex+package output：build/intermediates/awb-dex/debug)
 *      |
 *      |
 *      V
 *  packageDebug （output：build/outputs/apk/tmallandroid-debug-unaligned.apk）
 *
 */
public class TransformPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        def isApp = project.plugins.hasPlugin(AppPlugin) ||
                project.plugins.hasPlugin("com.taobao.android.application");
        if (isApp) {
            // 创建扩展参数，接受插桩定制
            project.extensions.create('tranformConfig', TransformExtension);
            project.extensions.tranformConfig.extensions.create('dexConfig', DexExtension);

            //遍历class文件和jar文件，在这里可以进行class文件asm文件替换
            def android = project.extensions.getByType(AppExtension);
            def transformMainDex = new TransformMainDex(project);
            android.registerTransform(transformMainDex);
        } else {
            project.logger.error("project has no AppPlugin，do nothing!");
        }

        // 1.在解析setting.gradle之后，开始解析build.gradle之前，这里如果要干些事情（如更改build.gradle脚本内容），
        // 可以写在beforeEvaluate
        project.beforeEvaluate {
            // TODO
        }

        // 2.在所有build.gradle解析完成后，开始执行task之前，此时所有的脚本已经解析完成，task，plugins等所有信息可以获取，
        // task的依赖关系也已经生成，如果此时需要做一些事情，可以写在afterEvaluate.
        // 每个task都可以定义doFirst，doLast，用于定义在此task执行之前或之后执行的代码.
        project.afterEvaluate {

            project.android.applicationVariants.each { ApkVariant variant ->

                def injectMainDexTask = project.tasks.findByName("transformClassesWithInjectMainDexFor${variant.name.capitalize()}")
                if(injectMainDexTask) {
//                    if ("release".equalsIgnoreCase(variant.name)) {
//                        injectMainDexTask.doLast {
//                            def transformAwb = new TransformAwb(project, variant.name);
//                            transformAwb.transform();
//                        }
//                    }
                }

                def jarOptTask = project.tasks.findByName("transformClassesWithJarOptFor${variant.name.capitalize()}")
                if(jarOptTask) {
                    jarOptTask.doLast {
                        def transformAwbV2 = new TransformAwbV2(project, variant.name);
                        transformAwbV2.transform();
                    }
                }

//                def proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}")
//                if (proguardTask) {
//                    if ("debug".equalsIgnoreCase(variant.name)) {
//                        proguardTask.doLast {
//                            def transformAwbV2 = new TransformAwbV2(project, variant.name);
//                            transformAwbV2.transform();
//                        }
//                    }
//                }
//
//                def dexTask = project.tasks.findByName("transformClassesWithDexFor${variant.name.capitalize()}")
//                if (dexTask) {
//                    project.logger.error "dexTask => ${dexTask.name}"
//
//                    dexTask.inputs.files.files.each { File file ->
//                        project.logger.error "file inputs=>${file.absolutePath}"
//                    }
//                    dexTask.outputs.files.files.each { File file ->
//                        project.logger.error "file outputs=>${file.absolutePath}"
//                    }
//                    dexTask.getTaskDependencies().getDependencies(dexTask).each { Task task ->
//                        project.logger.error "TaskDependency=>${task.getName()}"
//                    }
//                }
//
//                // plugin： com.taobao.android.application
////                def prepareAwbsXXXTask = project.tasks.findByName("prepareAwbs${variant.name.capitalize()}")
////                if(prepareAwbsXXXTask) {
////                    project.logger.error "prepareAwbsXXXTask => ${prepareAwbsXXXTask.name}"
////                    prepareAwbsXXXTask.inputs.files.files.each { File file ->
////                        project.logger.error "file inputs=>${file.absolutePath}"
////                    }
////                    prepareAwbsXXXTask.outputs.files.files.each { File file ->
////                        project.logger.error "file outputs=>${file.absolutePath}"
////                    }
////                    prepareAwbsXXXTask.getTaskDependencies().getDependencies(prepareAwbsXXXTask).each { Task task ->
////                        project.logger.error "TaskDependency=>${task.getName()}"
////                    }
////                }
////
////                def compileXXXJavaWithJavacTask = project.tasks.findByName("compile${variant.name.capitalize()}JavaWithJavac")
////                if(compileXXXJavaWithJavacTask) {
////                    project.logger.error "compileXXXJavaWithJavacTask => ${compileXXXJavaWithJavacTask.name}"
////                    compileXXXJavaWithJavacTask.inputs.files.files.each { File file ->
////                        project.logger.error "file inputs=>${file.absolutePath}"
////                    }
////                    compileXXXJavaWithJavacTask.outputs.files.files.each { File file ->
////                        project.logger.error "file outputs=>${file.absolutePath}"
////                    }
////                    compileXXXJavaWithJavacTask.getTaskDependencies().getDependencies(compileXXXJavaWithJavacTask).each { Task task ->
////                        project.logger.error "TaskDependency=>${task.getName()}"
////                    }
////                }
////
//                def javacAwbsXXXTask = project.tasks.findByName("javacAwbs${variant.name.capitalize()}")
//                if(javacAwbsXXXTask) {
//                    project.logger.error "javacAwbsXXXTask => ${javacAwbsXXXTask.name}"
//                    javacAwbsXXXTask.inputs.files.files.each { File file ->
//                        project.logger.error "file inputs=>${file.absolutePath}"
//                    }
//                    javacAwbsXXXTask.outputs.files.files.each { File file ->
//                        project.logger.error "file outputs=>${file.absolutePath}"
//                    }
//                    javacAwbsXXXTask.getTaskDependencies().getDependencies(javacAwbsXXXTask).each { Task task ->
//                        project.logger.error "TaskDependency=>${task.getName()}"
//                    }
//                }
//
//                def compileAwbXXXJavaWithJavacTask = project.tasks.findByName("compileAwb${variant.name.capitalize()}JavaWithJavac")
//                if(compileAwbXXXJavaWithJavacTask) {
//                    project.logger.error "compileAwbXXXJavaWithJavacTask => ${compileAwbXXXJavaWithJavacTask.name}"
//                    compileAwbXXXJavaWithJavacTask.inputs.files.files.each { File file ->
//                        project.logger.error "file inputs=>${file.absolutePath}"
//                    }
//                    compileAwbXXXJavaWithJavacTask.outputs.files.files.each { File file ->
//                        project.logger.error "file outputs=>${file.absolutePath}"
//                    }
//                    compileAwbXXXJavaWithJavacTask.getTaskDependencies().getDependencies(compileAwbXXXJavaWithJavacTask).each { Task task ->
//                        project.logger.error "TaskDependency=>${task.getName()}"
//                    }
//                }
////
////                def packageXXXAwbsTask = project.tasks.findByName("package${variant.name.capitalize()}Awbs")
////                if(packageXXXAwbsTask) {
////                    project.logger.error "packageXXXAwbsTask => ${packageXXXAwbsTask.name}"
////                    packageXXXAwbsTask.inputs.files.files.each { File file ->
////                        project.logger.error "file inputs=>${file.absolutePath}"
////                    }
////                    packageXXXAwbsTask.outputs.files.files.each { File file ->
////                        project.logger.error "file outputs=>${file.absolutePath}"
////                    }
////                    packageXXXAwbsTask.getTaskDependencies().getDependencies(packageXXXAwbsTask).each { Task task ->
////                        project.logger.error "TaskDependency=>${task.getName()}"
////                    }
////                }
////
////                def packageXXXTask = project.tasks.findByName("package${variant.name.capitalize()}")
////                if(packageXXXTask) {
////                    project.logger.error "packageXXXTask => ${packageXXXTask.name}"
////                    packageXXXTask.inputs.files.files.each { File file ->
////                        project.logger.error "file inputs=>${file.absolutePath}"
////                    }
////                    packageXXXTask.outputs.files.files.each { File file ->
////                        project.logger.error "file outputs=>${file.absolutePath}"
////                    }
////                    packageXXXTask.getTaskDependencies().getDependencies(packageXXXTask).each { Task task ->
////                        project.logger.error "TaskDependency=>${task.getName()}"
////                    }
////                }

                // 自定义ProGuardTask
//                task proguard(type: proguard.gradle.ProGuardTask, dependsOn: 'obfuscatedJar') {
//                    // You should probably import a more compact ProGuard-style configuration
//                    // file for all static settings, but we're specifying them all here, for
//                    // the sake of the example.
//                    configuration 'proguard.txt'
//
//                    // Specify the input jars, output jars, and library jars.
//                    // We'll filter out the Ant classes, Gradle classes, keeping
//                    // everything else.
//                    injars "$buildDir/libs/aaa.jar"
//                    injars "$buildDir/libs/bbb.jar"
//                    outjars "$buildDir/libs/obfuscate/"
//
//                    libraryjars "${System.getProperty('java.home')}/lib/rt.jar"
//
//                    // Write out an obfuscation mapping file, for de-obfuscating any stack traces
//                    // later on, or for incremental obfuscation of extensions.
//
//                    printmapping 'proguard.map'
//
//                    // keepparameternames
//                    renamesourcefileattribute 'SourceFile'
//                    keepattributes 'Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,EnclosingMethod'
//
//                    // Preserve all annotations.
//                    keepattributes '*Annotation*'
//
//                    // Preserve the special static methods that are required in all enumeration
//                    // classes.
//                    keepclassmembers allowshrinking:true, 'enum * { public static **[] values(); public static ** valueOf(java.lang.String); }'
//                }
            }
        }
    }

}