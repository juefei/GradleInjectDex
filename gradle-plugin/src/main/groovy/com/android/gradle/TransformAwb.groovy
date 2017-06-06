package com.android.gradle

import groovy.io.FileVisitResult
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * 针对awb格式的包进行字节码注入处理。
 *
 * 按照打包流程分析，class文件所在目录分别是：
 *    /intermediates/awb-classes/
 *    /intermediates/exploded-awb/
 */
final class TransformAwb {

    private final static Logger logger = Logging.getLogger(TransformAwb);

    private Project project;

    private String buildType;

    public TransformAwb(Project project, String buildType) {
        this.project = project;
        this.buildType = buildType;
        DexProcessor.setDexExtension(project.tranformConfig.dexConfig);
    }

    void transform() {
        /**
         * 遍历class文件
         */
        def inputClassDir = new File(project.buildDir, "/intermediates/awb-classes/${buildType}/");
        if(inputClassDir.exists()) {
            inputClassDir.traverse { inputFile ->
                if (!inputFile.isDirectory()) {
                    String relativePath = FileUtil.relativize(inputClassDir, inputFile);
                    logger.debug("TransformAwb# classFile = ${inputFile.absolutePath}, relativePath = ${relativePath}");
                    if(DexProcessor.shouldprocessClass(relativePath)) {
                        def bytes = DexProcessor.processClass(inputFile, relativePath);
                        File outputFile = File.createTempFile(inputFile.getName(), ".tmp", inputFile.getParentFile());
                        FileUtil.copyBytesToFile(bytes, outputFile);
                    } else {
                        logger.debug("ignore process classFile = " + inputFile.absolutePath)
                    }
                }

                return FileVisitResult.CONTINUE
            }
        } else {
            logger.error("TransformAwb# inputClassDir not exists");
        }

        /**
         * 遍历jar文件
         */
        def inputJarDir = new File(project.buildDir, "/intermediates/exploded-awb/");
        if(inputJarDir.exists()) {
            inputJarDir.traverse { inputFile ->
                if (!inputFile.isDirectory() && inputFile.getName().endsWith("classes.jar")) {
                    logger.error("TransformAwb# jarFile = " + inputFile.absolutePath);
                    if(DexProcessor.shouldprocessJar(inputFile.absolutePath)) {
                        File outputFile = File.createTempFile(inputFile.getName(), ".tmp", inputFile.getParentFile());
                        DexProcessor.processJar(inputFile, outputFile);
                        if(!outputFile.renameTo(inputFile)){
                            logger.error("TransformAwb# ${outputFile.absolutePath} renameTo " +
                                            "${inputFile.absolutePath} failed! try again...");
                            outputFile.renameTo(inputFile);
                        }
                    } else {
                        logger.error("ignore process jarFile = " + inputFile.absolutePath)
                    }
                }

                return FileVisitResult.CONTINUE
            }
        } else {
            logger.error("TransformAwb# inputJarDir not exists");
        }
    }

}