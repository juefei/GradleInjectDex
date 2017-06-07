package com.android.gradle

import groovy.io.FileVisitResult
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * 针对awb格式的包进行字节码注入处理。
 *
 * 按照打包流程分析，直接针对混淆之后的class文件处理，此时class文件比较全面，不会遗漏：
 *    build/intermediates/classes-proguard/debug
 */
final class TransformAwbV2 {

    private final static Logger logger = Logging.getLogger(TransformAwbV2);

    private Project project;

    private String buildType;

    public TransformAwbV2(Project project, String buildType) {
        this.project = project;
        this.buildType = buildType;
        DexProcessor.setDexExtension(project.tranformConfig.dexConfig);
    }

    void transform() {
        /**
         * 遍历jar文件
         */
        def inputJarDir = new File(project.buildDir, "/intermediates/classes-proguard/${buildType}");
        if(inputJarDir.exists()) {
            inputJarDir.traverse { inputFile ->
                if (!inputFile.isDirectory() && inputFile.getName().endsWith(".jar")) {
                    logger.error("TransformAwbV2# proguardjarFile = " + inputFile.absolutePath);
                    if(DexProcessor.shouldprocessJar(inputFile.absolutePath)) {
                        File outputFile = File.createTempFile(inputFile.getName(), ".tmp", inputFile.getParentFile());
                        DexProcessor.processJar(inputFile, outputFile);
                        if(!outputFile.renameTo(inputFile)){
                            logger.error("TransformAwbV2# ${outputFile.absolutePath} renameTo " +
                                            "${inputFile.absolutePath} failed! try again...");
                            outputFile.renameTo(inputFile);
                        }
                    } else {
                        logger.error("TransformAwbV2# ignore process proguardjarFile = " + inputFile.absolutePath)
                    }
                }

                return FileVisitResult.CONTINUE
            }
        } else {
            logger.error("TransformAwbV2# inputProguardJarDir not exists");
        }
    }

}