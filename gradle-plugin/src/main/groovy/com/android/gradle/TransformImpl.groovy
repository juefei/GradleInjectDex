package com.android.gradle

import com.android.build.api.transform.*
import groovy.io.FileVisitResult
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class TransformImpl extends Transform {
    private final static Logger logger = Logging.getLogger(TransformImpl);

    private Project project;

    public TransformImpl(Project project) {
        this.project = project;
        DexProcessor.transformExt = project.tranformConfig;
    }

    @Override
    String getName() {
        return "HookDex";
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return EnumSet.of(QualifiedContent.DefaultContentType.CLASSES);
    }


    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return EnumSet.of(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES);
    }


    @Override
    boolean isIncremental() {
        return false;
    }


    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs,
                   TransformOutputProvider outputProvider, boolean isIncremental)
                    throws IOException, TransformException, InterruptedException {
        /**
         * 遍历输入文件
         */
        inputs.each { TransformInput input ->
            /**
             * 遍历目录
             */
            input.directoryInputs.each { DirectoryInput directoryInput ->

                File dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes,
                                                                directoryInput.scopes, Format.DIRECTORY);

                //这里进行字节码注入处理 TODO
                logger.debug "process directory = ${directoryInput.file.absolutePath}"
                File tmpDestDir = new File(directoryInput.file.parentFile, "asm")
                if(!tmpDestDir.exists()) {
                    tmpDestDir.mkdirs()
                }
                processDirectory(directoryInput.file, tmpDestDir);
                FileUtil.copyDirectory(tmpDestDir, dest)
            }

            /**
             * 遍历jar
             */
            input.jarInputs.each { JarInput jarInput ->

                String destName = jarInput.name;
                if (destName.endsWith(".jar")) {
                    destName = destName.substring(0, destName.length() - 4);
                }
                File dest = outputProvider.getContentLocation(destName, jarInput.contentTypes,
                                                                jarInput.scopes, Format.JAR);

                //处理jar进行字节码注入处理 TODO
                logger.error("process jar = " + jarInput.file.absolutePath);
                if(DexProcessor.shouldprocessJar(jarInput.file.absolutePath)) {
                    File tmpDest = new File(jarInput.file.parentFile, jarInput.file.name + ".tmp");
                    if(tmpDest.exists()) {
                        tmpDest.delete();
                    }
                    tmpDest.createNewFile();
                    DexProcessor.processJar(jarInput.file, tmpDest);
                    FileUtil.copyFile(tmpDest, dest,true);
                } else {
                    logger.error("ignore process jar = " + jarInput.file.absolutePath);
                    FileUtil.copyFile(jarInput.file, dest, true)
                }
            }
        }
    }

    private void processDirectory(File sourceDir, File destDir) {

        DexProcessor.prepareClass(destDir);

        sourceDir.traverse { inputFile ->
            if (!inputFile.isDirectory()) {
                String relativePath = FileUtil.relativize(sourceDir, inputFile)
                File outputFile = new File(destDir, relativePath)
                if(DexProcessor.shouldprocessClass(relativePath)) {
                    def bytes = DexProcessor.processClass(inputFile, relativePath)
                    FileUtil.copyBytesToFile(bytes, outputFile)
                } else {
                    logger.error("ignore process classFile = " + inputFile.absolutePath)
                    FileUtil.copyFile(inputFile, outputFile, true)
                }
            }

            return FileVisitResult.CONTINUE
        }
    }

}