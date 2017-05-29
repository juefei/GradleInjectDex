package com.android.gradle

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import groovy.io.FileVisitResult

class FileUtil {
    private final static Logger logger = Logging.getLogger(FileUtil);

    public static void copyFile(File source, File destFile, boolean replaceExisting = false) {
        if (!replaceExisting && destFile.exists()) {
            return
        }

        if(!destFile.parentFile.exists()) {
            destFile.parentFile.mkdirs()
        }

//        logger.error("copyFile " + source.absolutePath + " to " + destination.absolutePath + " success!")

        source.withDataInputStream { sourceStream ->
            destFile.withDataOutputStream { destStream ->
                destStream << sourceStream
            }
        }

        destFile.lastModified = source.lastModified()
    }

    public static String relativize(final File parent, final File child) {
        final URI relativeUri = parent.toURI().relativize(child.toURI())
        return relativeUri.toString()
    }

    public static void copyDirectory(File sourceDir, File destinationDir) {
//        logger.error("copyDirectory " + sourceDir.absolutePath + " to " + destinationDir.absolutePath)
        sourceDir.traverse { inputFile ->
            if (!inputFile.isDirectory()) {
                String relativePath = relativize(sourceDir, inputFile)
//                logger.error("copyDirectory relativePath = " + relativePath)
                File outputFile = new File(destinationDir, relativePath)
                copyFile(inputFile, outputFile, true)
            }

            return FileVisitResult.CONTINUE
        }
    }

    public static void copyBytesToFile(byte[] bytes, File destFile) {
        if(!destFile.parentFile.exists()) {
            destFile.parentFile.mkdirs()
        }

        try {
            destFile.setBytes(bytes)
        } catch (IOException e) {
            e.printStackTrace()
        }

        logger.error("copyBytesToFile " + destFile.absolutePath);
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] readContent = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(readContent)) != -1) {
            output.write(readContent, 0, bytesRead);
        }
    }

}