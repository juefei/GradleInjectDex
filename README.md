# GradleInjectDex 
gradle打包插件用于字节码插桩

## GradleInjectDex介绍
我们在日常开发过程中，经常会有字节码注入的需求，比如，最近几年热门的热修复技术QQ空间dex插桩防止class verify 无痕埋点AOP方案等等。经常会有各种方案文章提及如何使用字节码注入技术实现自己的业务需求，但是一般都侧重字节码注入后达到的业务效果，还没有系统性介绍打包阶段字节码注入技术细节。
本次从工程实践的角度，详细阐述如何开发一个gradle plugin，以及如何使用ASM AOP框架修改字节码，已达到运行阶段特定条件下执行定制代码功能。


* gradle-plugin工程

gradle-plugin工程是用groovy语言开发的gradle插件，包括gradle插件标准工程结构，以及如何打包发布到maven仓库。<br />
本插件工程包括两个插件：<br />
&emsp;&emsp;<font color=#ff0000>1.统计每个tasks执行时间。</font><br />
&emsp;&emsp;<font color=#ff0000>2.统计应用内每个方法执行耗时。</font><br />
&emsp;&emsp;<font color=#ff0000>3.借助ASM自动生成插桩代码。</font>

* app工程

一个demo工程，介绍如何使用gradle-plugin工程发布的插件，在打包阶段使用ASM AOP框架修改字节码，在每个方法前后注入代码。

## 如何使用

* 修改发布gradle-plugin

./gradlew :gradle-plugin:uploadArchives

* 使用gradle-plugin

添加：

<pre><code>
dependencies {
    classpath 'com.android.tools.build:gradle:2.2.3'
    classpath 'com.android.gradle.plugin:tasktime:1.10.9.189-SNAPSHOT'   // 增加插件库路径
}

apply plugin: 'plugin.tasktime'

apply plugin: 'plugin.transform'

tranformConfig {
    debugMode = true
    dexConfig {
        thresholdInMainThread = 20   // 方法在主线程执行时间阈值，大于此值将打印输出  
        thresholdInOtherThread = 600  // 方法在子线程执行时间阈值，大于此值将打印输出  
        excludeClasses = ["com/android/test/TestBuild.class", "xxx.class"]  // 不做代码注入的类
        excludeJars = ["com.android.support:appcompat-v7"]  // groupbyId:artifactId // 不做代码注入的jar包  
    }
}
</code></pre>

执行：

./gradlew clean assembleRelease 
