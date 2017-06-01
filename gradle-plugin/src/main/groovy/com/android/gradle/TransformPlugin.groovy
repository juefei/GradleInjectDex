package com.android.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
/**
 * Transform插件入口
 */
public class TransformPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        def isApp = project.plugins.hasPlugin(AppPlugin) || project.plugins.hasPlugin("com.taobao.android.application");
        if (isApp) {
            // 创建扩展参数，接受插桩定制
            project.extensions.create('tranformConfig', TransformExtension);
            project.extensions.tranformConfig.extensions.create('dexConfig', DexExtension);

            //遍历class文件和jar文件，在这里可以进行class文件asm文件替换
            def android = project.extensions.getByType(AppExtension)
            def transform = new TransformImpl(project)
            android.registerTransform(transform)
        } else {
            project.logger.error("project has no AppPlugin，do nothing!");
        }

        project.afterEvaluate {
            project.android.applicationVariants.each { variant ->

                def hookDexTask = project.tasks.findByName("transformClassesWithHookDexFor${variant.name.capitalize()}")
                if(hookDexTask) {
                    project.logger.error "hookDex => ${variant.name.capitalize()}"

                    hookDexTask.inputs.files.files.each { File file->
                        project.logger.error "file inputs=>${file.absolutePath}"
                    }

                    hookDexTask.outputs.files.files.each { File file->
                        project.logger.error "file outputs=>${file.absolutePath}"
                    }
                }

                def proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}")
                if (proguardTask) {
                    project.logger.error "proguard => ${variant.name.capitalize()}"

                    proguardTask.inputs.files.files.each { File file->
                        project.logger.error "file inputs=>${file.absolutePath}"
                    }

                    proguardTask.outputs.files.files.each { File file->
                        project.logger.error "file outputs=>${file.absolutePath}"
                    }
                }

                def dexTask = project.tasks.findByName("transformClassesWithDexFor${variant.name.capitalize()}")
                if (dexTask) {
                    project.logger.error "dex => ${variant.name.capitalize()}"

                    dexTask.inputs.files.files.each { File file->
                        project.logger.error "file inputs=>${file.absolutePath}"
                    }

                    dexTask.outputs.files.files.each { File file->
                        project.logger.error "file outputs=>${file.absolutePath}"
                    }
                }
            }
        }
    }
}