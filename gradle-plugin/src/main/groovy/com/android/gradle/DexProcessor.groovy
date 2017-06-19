package com.android.gradle

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter
import sun.misc.IOUtils

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
/**
 *
 一般来说，对一个普通的java文件字节码操作流程:

 1、javac Test.java 生成Test.class文件

 2、使用ClassWriter和ClassReader修改Test.class文件

 3、将修改后的class文件保存为新的文件即可

 */
public class DexProcessor {
    private final static Logger logger = Logging.getLogger(DexProcessor);

    private static DexExtension dexExtension;

    public static void setDexExtension(DexExtension dexExt) {
        dexExtension = dexExt;
        logger.debug("dexExtension => ${dexExtension}")
    }

    // com/android/test/MainActivity.class
    public static boolean shouldprocessClass(String className) {
        if(!className.endsWith(".class")) {
            return false;
        }

        // xx/BuildConfig.class R$dimen.class R$id.class R$integer.class R$drawable.class
        // R$layout.class  R$string.class  R$style.class R$styleable.class
        if(className.endsWith("BuildConfig.class") || className.endsWith("R.class") || className.contains("R\$")) {
            return false;
        }

        if(dexExtension != null && dexExtension.excludeClasses != null) {
            if(dexExtension.excludeClasses.contains(className)) {
                logger.error("excludeClass => ${className}");
                return false;
            }
        }

        return true;
    }

    public static byte[] processClass(File file, String className) {
        // 1.首先我们使用ClassReader读取class文件为asm code，
        // 2.然后定义一个Visitor用来处理字节码；当classReader调用accept的时候,Visitor就会按一定的顺序调用 里边重载的方法
        ClassReader classReader = new ClassReader(file.bytes);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new CostMethodClassVisitor(classWriter, className, "maindex");
        classReader.accept(cv, ClassReader.EXPAND_FRAMES);
        byte[] code = classWriter.toByteArray();
        return code;
    }

    // xx/Library/Android/android-sdk/extras/android/m2repository/com/android/support/support-annotations/25.3.1/support-annotations-25.3.1.jar
    // xx/app/build/intermediates/exploded-aar/com.android.support/support-v4/25.3.1/jars/classes.jar
    public static boolean shouldprocessJar(String jarName) {
        if(dexExtension != null && dexExtension.excludeJars != null) {
            String formatJarName = jarName.replace('/', '.');
            for(String excludeJar : dexExtension.excludeJars) {
                excludeJar = excludeJar.replace(':', '.');
                if(formatJarName.contains(excludeJar)) {
                    logger.error("excludeJar => ${jarName}");
                    return false;
                }
            }
        }

        return true;
    }

    // xx/build/intermediates/exploded-awb/com.tmall.wireless/tmallandroid_weapp_bundle/1.0.2.10/jars/classes.jar
    // xx/build/intermediates/awb-jaropt/com.taobao.android-taobao_dexmerge/1.0.2-opt.jar
    // xx/build/intermediates/classes-proguard/debug/com.taobao.android-taobao_dexmerge/obfuscated.jar
    public static boolean shouldprocessBundle(String jarName) {
        if(dexExtension != null && dexExtension.excludeBundles != null) {
            String formatJarName;
            if(jarName.contains("awb-jaropt") || jarName.contains("classes-proguard")) {
                formatJarName = jarName.replace('-', '.');
            } else {
                formatJarName = jarName.replace('/', '.');
            }
            for(String excludeJar : dexExtension.excludeBundles) {
                excludeJar = excludeJar.replace(':', '.');
                if(formatJarName.contains(excludeJar)) {
                    logger.error("excludeBundle => ${jarName}");
                    return false;
                }
            }
        }

        return true;
    }

    private static String getBundleName(String path) {
        if(path.contains("awb-jaropt") || path.contains("classes-proguard")) {
            String[] targetArray = path.split("/");
            if(targetArray.length > 2) {
                String target = targetArray[targetArray.length - 2];
                return target.replace("-", ":");
            }
        } else {
            String regex = "(repository|exploded-aar|exploded-awb)\\/((.*))\\/\\d";
            Pattern pattern = Pattern.compile(regex);
            Matcher ruleMatcher = pattern.matcher(path);
            if (ruleMatcher.find()) {
                if(ruleMatcher.groupCount() > 2) {
                    String target = ruleMatcher.group(2);
                    String[] targetArray = target.split("/");
                    if(targetArray.length == 2) {   // com.android.support/support-v4
                        target = target.replace("/", ":");
                    } else if(targetArray.length > 2) {  // com/android/support/support-annotations
                        target = "";
                        for(int i = 0; i < targetArray.length - 1; i++){
                            target += targetArray[i];
                            if(i != targetArray.length - 2) {
                                target += ".";
                            } else {
                                target += ":";
                            }
                        }
                        target += targetArray[targetArray.length - 1];
                    }

                    return target;
                }
            }
        }

        return "unknown:bundle:" + path;
    }

    public static void processJar(File inputJar, File outPutJar) {
        JarOutputStream target = null;
        JarFile jarfile = null;
        try{
            target = new JarOutputStream(new FileOutputStream(outPutJar));
            jarfile = new JarFile(inputJar);
            logger.debug("Jarfile:"+jarfile.getName());
            Enumeration<? extends JarEntry> entryList = jarfile.entries();
            while(entryList.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) entryList.nextElement();
                logger.debug("JarEntry:" + jarEntry.getName());
                JarEntry newEntry = new JarEntry(jarEntry.getName());
                target.putNextEntry(newEntry);
                if(!jarEntry.isDirectory()) {
                    if(jarEntry.getName().endsWith(".class")) {
                        ClassReader classReader = new ClassReader(getBytes(jarfile, jarEntry));
                        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                        ClassVisitor cv = new CostMethodClassVisitor(classWriter, jarEntry.getName(),
                                getBundleName(inputJar.absolutePath));
                        classReader.accept(cv, ClassReader.EXPAND_FRAMES);
                        byte[] bytes = classWriter.toByteArray();
                        target.write(bytes);
                    } else {
                       target.write(getBytes(jarfile, jarEntry));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.error("processJar => " + e.getMessage());
        } finally {
            try {
                if (target != null) {
                    target.closeEntry();
                    target.close();
                }
                if(jarfile != null) {
                    jarfile.close();
                }
            } catch (Throwable e) {
            }
        }
    }

    // from JarFile.java
    private static byte[] getBytes(ZipFile jarfile, ZipEntry entry) throws IOException {
        InputStream inputStream = jarfile.getInputStream(entry);
        Throwable throwable = null;
        byte[] bytes;
        try {
            bytes = IOUtils.readFully(inputStream, (int)entry.getSize(), true);
        } catch (Throwable var13) {
            throwable = var13;
            throw var13;
        } finally {
            if(inputStream != null) {
                if(throwable != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable var12) {
                        throwable.addSuppressed(var12);
                    }
                } else {
                    inputStream.close();
                }
            }
        }

        return bytes;
    }

    /**
     * define method cost class visitor
     */
    static class CostMethodClassVisitor extends ClassVisitor {
        private String className;
        private String bundleName;

        public CostMethodClassVisitor(ClassVisitor classVisitor, String className, String bundleName) {
            super(Opcodes.ASM5, classVisitor);
            this.className = className;
            this.bundleName = bundleName;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                         String[] exceptions) {
            MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
            MethodVisitor customMethodVisitor = new AdviceAdapter(Opcodes.ASM5, methodVisitor, access, name, desc) {

                boolean inject = true;

                private boolean isInject() {
                    boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
                    if(isInterface) {
                        inject = false
                    } else if (name.equals("<init>")) {
                        inject = false;
                    } else if (name.equals("setStartTime") || name.equals("setEndTime") || name.equals("getCostTime")){
                        inject = false;
                    }

                    return inject;
                }

                @Override
                public void visitCode() {
                    // 方法体内开始时调用
//                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
//                    mv.visitLdcInsn("========start=========");
//                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                    super.visitCode();
                }

                @Override
                void visitInsn(int opcode) {
                    // 每执行一个指令都会调用
//                    if (opcode == Opcodes.RETURN) {
//                        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
//                        mv.visitLdcInsn("========end=========");
//                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
//                    }
                    super.visitInsn(opcode);
                }

//                // 当我们在某个方法写上注解的时候，它会执行这个方法，我们可以在这个方法判断是否是我们注解做一些事情，比如重置一个标志位；
//                @Override
//                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
//                    if (Type.getDescriptor(Cost.class).equals(desc)) {
//                        inject = true;
//                    }
//                    return super.visitAnnotation(desc, visible);
//                }

                // 方法进入调用
                @Override
                protected void onMethodEnter() {
                    super.onMethodEnter();
                    if(isInject()) {
//                        logger.error("onMethodEnter# name: " + name + "==>des: " + desc);
                        //  相当于com.android.gradle.TimeUtil.setStartTime("name");
                        String key = bundleName + "@" + className + "#" + name + "#" + desc;
                        mv.visitLdcInsn(key);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/android/gradle/TimeUtil", "setStartTime",
                                "(Ljava/lang/String;)V", false);
                    }
                }

                // 方法结束时调用
                @Override
                protected void onMethodExit(int opcode) {
                    super.onMethodExit(opcode);
                    if(isInject()) {
//                        logger.error("onMethodExit# name: " + name + "==>des: " + desc);
                        //  相当于com.android.gradle.TimeUtil.setEndTime("name");
                        String key = bundleName + "@" + className + "#" + name + "#" + desc;
                        mv.visitLdcInsn(key);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/android/gradle/TimeUtil", "setEndTime",
                                "(Ljava/lang/String;)V", false);

                        if(dexExtension != null && dexExtension.debugEnabled) {
                            mv.visitLdcInsn(bundleName + "@" + className);
                            mv.visitLdcInsn(name);
                            mv.visitLdcInsn(desc);
                            //相当于com.android.test.TimeUtil.getCostTime("com/blueware/agent/TestTime","testTime");
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/android/gradle/TimeUtil",
                                    "getCostTime", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
                        }
                    }
                }
            };

            return customMethodVisitor;
            //return super.visitMethod(i, s, s1, s2, strings);
        }
    }

    /**
     * 使用ASM生成一个类 com/android/gradle/TimeUtil.class
     *
     public class TimeUtil {
        private static Map<Object, Object> endTimes;
        private static Map<Object, Object> startTimes;

        static {
            startTimes = new HashMap();
            endTimes = new HashMap();
        }

        public static void getCostTime(String str, String str2, String str3) {
            try {
                String str4 = str + "#" + str2 + "#" + str3;
                long longValue = ((Long) endTimes.get(str4)).longValue() - ((Long) startTimes.get(str4)).longValue();
                if (Looper.myLooper() == Looper.getMainLooper() && longValue >= 20) {
                    System.out.println(" Thread: " + Thread.currentThread().getName() + " load1: " + str4 + " cost: " + longValue + " ms!");
                } else if (Looper.myLooper() != Looper.getMainLooper() && longValue >= 500) {
                    System.out.println(" Thread: " + Thread.currentThread().getName() + " load2: " + str4 + " cost: " + longValue + " ms!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void setEndTime(String str) {
            endTimes.put(str, Long.valueOf(System.currentTimeMillis()));
        }

        public static void setStartTime(String str) {
            startTimes.put(str, Long.valueOf(System.currentTimeMillis()));
        }
     }
     *
     */
    public static void prepareClass(File destDir) {

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "com/android/gradle/TimeUtil", null,
                "java/lang/Object", null);
        cw.visitSource("TimeUtil.java", null);

        MethodVisitor mw;
        FieldVisitor fw;

        // 0. add field
        fw = cw.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "startTimes", "Ljava/util/Map;",
                "Ljava/util/Map<Ljava/lang/Object;Ljava/lang/Object;>;", null);
        fw.visitEnd();
        fw = cw.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "endTimes", "Ljava/util/Map;",
                "Ljava/util/Map<Ljava/lang/Object;Ljava/lang/Object;>;", null);
        fw.visitEnd();

        // 1. add constructor
        mw = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V",
                null, null);
        mw.visitCode();
        mw.visitVarInsn(Opcodes.ALOAD, 0); // this 入栈
        mw.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>",
                "()V", false);
        mw.visitInsn(Opcodes.RETURN);
        mw.visitMaxs(0, 0);
        mw.visitEnd();

        // 2. add setStartTime
        mw = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "setStartTime",
                "(Ljava/lang/String;)V", null, null);
        mw.visitCode();
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        mw.visitVarInsn(Opcodes.LSTORE, 1);  // 将栈顶引用型数值存入指定本地变量；用于存储变量
        mw.visitVarInsn(Opcodes.LLOAD, 1);  // 将第一个引用类型本地变量推送至栈顶；在类中；aload0通常指向this指针
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mw.visitVarInsn(Opcodes.ASTORE, 2);
        mw.visitFieldInsn(Opcodes.GETSTATIC, "com/android/gradle/TimeUtil", "startTimes", "Ljava/util/Map;");
//        mw.visitVarInsn(Opcodes.ALOAD, 0);
        mw.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mw.visitInsn(Opcodes.DUP);
        mw.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        mw.visitVarInsn(Opcodes.ALOAD, 0);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn("&");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getName", "()Ljava/lang/String;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mw.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Object");
        mw.visitVarInsn(Opcodes.ALOAD, 2);
        mw.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Object");
        mw.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        mw.visitInsn(Opcodes.RETURN);
        mw.visitMaxs(0, 0);
        mw.visitEnd();
//
        // 3. add setEndTime
        mw = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "setEndTime",
                "(Ljava/lang/String;)V", null, null);
        mw.visitCode();
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        mw.visitVarInsn(Opcodes.LSTORE, 1);
        mw.visitVarInsn(Opcodes.LLOAD, 1);
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mw.visitVarInsn(Opcodes.ASTORE, 2);
        mw.visitFieldInsn(Opcodes.GETSTATIC, "com/android/gradle/TimeUtil", "endTimes", "Ljava/util/Map;");
//        mw.visitVarInsn(Opcodes.ALOAD, 0);
        mw.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mw.visitInsn(Opcodes.DUP);
        mw.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        mw.visitVarInsn(Opcodes.ALOAD, 0);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn("&");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getName", "()Ljava/lang/String;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mw.visitVarInsn(Opcodes.ALOAD, 2);
        mw.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Object");
        mw.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        mw.visitInsn(Opcodes.RETURN);
        mw.visitMaxs(0, 0);
        mw.visitEnd();

        // 4. add getCostTime
        mw = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "getCostTime",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", null, null);
        mw.visitCode();

        Label lTryBlockStart = new Label();
        Label lTryBlockEnd = new Label();
        Label lHandler = new Label();
        mw.visitTryCatchBlock(lTryBlockStart, lTryBlockEnd, lHandler, "java/lang/Exception");
        mw.visitLabel(lTryBlockStart);

        // String key = className#methodName#methodDesc-threadName;
        mw.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mw.visitInsn(Opcodes.DUP);
        mw.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        mw.visitVarInsn(Opcodes.ALOAD, 0);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn("#");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitVarInsn(Opcodes.ALOAD, 1);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn("#");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitVarInsn(Opcodes.ALOAD, 2);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn("&");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getName", "()Ljava/lang/String;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mw.visitVarInsn(Opcodes.ASTORE, 3);

        // ((Long)endTimes.get(key)).longValue()-((Long)startTimes.get(key)).longValue()
        mw.visitFieldInsn(Opcodes.GETSTATIC, "com/android/gradle/TimeUtil", "endTimes", "Ljava/util/Map;");
        mw.visitVarInsn(Opcodes.ALOAD, 3);
        mw.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        mw.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        mw.visitFieldInsn(Opcodes.GETSTATIC, "com/android/gradle/TimeUtil", "startTimes", "Ljava/util/Map;");
        mw.visitVarInsn(Opcodes.ALOAD, 3);
        mw.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        mw.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        mw.visitInsn(Opcodes.LSUB);
        mw.visitVarInsn(Opcodes.LSTORE, 4);

        // if(Looper.myLooper() == Looper.getMainLooper() && costTime >= 16L)
        Label iftrue1 = new Label();
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "android/os/Looper", "myLooper", "()Landroid/os/Looper;", false);
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "android/os/Looper", "getMainLooper", "()Landroid/os/Looper;", false);
        mw.visitJumpInsn(Opcodes.IF_ACMPNE, iftrue1);
        mw.visitVarInsn(Opcodes.LLOAD, 4);
        if(dexExtension != null && dexExtension.thresholdInMainThread > 0) {
            mw.visitLdcInsn(new Long(dexExtension.thresholdInMainThread));
        } else {
            mw.visitLdcInsn(new Long(16l));
        }
        mw.visitInsn(Opcodes.LCMP);
        mw.visitJumpInsn(Opcodes.IFLT, iftrue1);

        //  System.out.println(key + " cost:" + exclusive + "ms");
        mw.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mw.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mw.visitInsn(Opcodes.DUP);
        mw.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V",
                false);
        mw.visitLdcInsn(" Thread: ");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getName", "()Ljava/lang/String;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn(" load1: ");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitVarInsn(Opcodes.ALOAD, 3);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn(" cost: ");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitVarInsn(Opcodes.LLOAD, 4);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn(" ms!");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        mw.visitInsn(Opcodes.RETURN);
        mw.visitLabel(iftrue1);

        // if(Looper.myLooper() != Looper.getMainLooper() && costTime >= 500L)
        Label iftrue2 = new Label();
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "android/os/Looper", "myLooper", "()Landroid/os/Looper;", false);
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "android/os/Looper", "getMainLooper", "()Landroid/os/Looper;", false);
        mw.visitJumpInsn(Opcodes.IF_ACMPEQ, iftrue2);
        mw.visitVarInsn(Opcodes.LLOAD, 4);
        if(dexExtension != null && dexExtension.thresholdInOtherThread > 0) {
            mw.visitLdcInsn(new Long(dexExtension.thresholdInOtherThread));
        } else {
            mw.visitLdcInsn(new Long(500l));
        }
        mw.visitInsn(Opcodes.LCMP);
        mw.visitJumpInsn(Opcodes.IFLT, iftrue2);

        mw.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mw.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mw.visitInsn(Opcodes.DUP);
        mw.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V",
                false);
        mw.visitLdcInsn(" Thread: ");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getName", "()Ljava/lang/String;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn(" load2: ");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitVarInsn(Opcodes.ALOAD, 3);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn(" cost: ");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitVarInsn(Opcodes.LLOAD, 4);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;",
                false);
        mw.visitLdcInsn(" ms!");
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        mw.visitInsn(Opcodes.RETURN);
        mw.visitLabel(iftrue2);

        mw.visitLabel(lTryBlockEnd);
        Label lCatchBlockEnd = new Label();
        mw.visitJumpInsn(Opcodes.GOTO, lCatchBlockEnd);
        mw.visitLabel(lHandler);
        mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/RuntimeException", "printStackTrace", "()V", false);
        mw.visitLabel(lCatchBlockEnd);

//        mw.visitVarInsn(Opcodes.LLOAD, 4);
//        mw.visitInsn(Opcodes.LRETURN);   // 从当前方法返回对象引用
        mw.visitInsn(Opcodes.RETURN);
        mw.visitMaxs(0, 0);
        mw.visitEnd();

        // 5. set static field
        mw = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mw.visitCode();
        mw.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mw.visitInsn(Opcodes.DUP);
        mw.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        mw.visitFieldInsn(Opcodes.PUTSTATIC, "com/android/gradle/TimeUtil", "startTimes", "Ljava/util/Map;");
        mw.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mw.visitInsn(Opcodes.DUP);
        mw.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        mw.visitFieldInsn(Opcodes.PUTSTATIC, "com/android/gradle/TimeUtil", "endTimes", "Ljava/util/Map;");
        mw.visitInsn(Opcodes.RETURN);
        mw.visitMaxs(0, 0);
        mw.visitEnd();

        cw.visitEnd();

        // 6. save class
        final byte[] code = cw.toByteArray();
        File destFile = new File(destDir, "com/android/gradle/TimeUtil.class");
        if(!destFile.parentFile.exists()) {
            destFile.parentFile.mkdirs();
        }
        destFile.createNewFile();
        OutputStream out = new FileOutputStream(destFile);
        out.write(code);
        out.close();
    }

}

