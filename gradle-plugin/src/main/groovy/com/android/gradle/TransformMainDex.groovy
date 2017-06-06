package com.android.gradle

import com.android.build.api.transform.*
import groovy.io.FileVisitResult
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

final class TransformMainDex extends Transform {
    private final static Logger logger = Logging.getLogger(TransformMainDex);

    private Project project;

    public TransformMainDex(Project project) {
        this.project = project;
        DexProcessor.setDexExtension(project.tranformConfig.dexConfig);
    }

    @Override
    String getName() {
        return "InjectMainDex";
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

        inputs.each { TransformInput input ->
            /**
             * 遍历目录
             */
            input.directoryInputs.each { DirectoryInput directoryInput ->

                File dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes,
                                                                directoryInput.scopes, Format.DIRECTORY);
                //这里进行字节码注入处理 TODO
                logger.debug "process directory = ${directoryInput.file.absolutePath}"
                File tmpDestDir = new File(directoryInput.file.parentFile, "asm");
                if(!tmpDestDir.exists()) {
                    tmpDestDir.mkdirs();
                }
                DexProcessor.prepareClass(tmpDestDir);

                processDirectory(directoryInput.file, tmpDestDir);
                FileUtil.copyDirectory(tmpDestDir, dest);
                tmpDestDir.deleteDir();
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
                    File tmpDest = File.createTempFile(jarInput.file.name, ".tmp", jarInput.file.parentFile);
                    DexProcessor.processJar(jarInput.file, tmpDest);
                    FileUtil.copyFile(tmpDest, dest, true);
                    tmpDest.delete();
                } else {
                    logger.error("ignore process jar = " + jarInput.file.absolutePath);
                    FileUtil.copyFile(jarInput.file, dest, true)
                }
            }
        }
    }

    private void processDirectory(File sourceDir, File destDir) {

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