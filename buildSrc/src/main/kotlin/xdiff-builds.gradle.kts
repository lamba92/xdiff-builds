import kotlin.io.path.absolutePathString
import kotlin.io.path.relativeTo
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.internal.os.OperatingSystem

val xdiffBuildDir = layout.buildDirectory.dir("compilations/xdiff")

val xdiffBuildDirPath = xdiffBuildDir.map { it.asFile.toPath() }

data class BuildConfig(
    val isDebug: Boolean,
    val isShared: Boolean,
)

val matrix = buildList {
    add(BuildConfig(isDebug = false, isShared = false))
    add(BuildConfig(isDebug = false, isShared = true))
    add(BuildConfig(isDebug = true, isShared = false))
    add(BuildConfig(isDebug = true, isShared = true))
}

fun withMatrix(
    platformName: String,
    action: MutableList<TaskProvider<BuildXdiff>>.(
        release: Boolean,
        isShared: Boolean,
        baseTaskName: String,
        dirPath: (String) -> String
    ) -> Unit
) = buildList {
    matrix.forEach { (isDebug, isShared) ->
        val taskName = buildString {
            append("buildXdiff")
            when {
                isShared -> append("Shared")
                else -> append("Static")
            }
            when {
                isDebug -> append("Debug")
                else -> append("Release")
            }
            append(platformName.capitalized())
        }
        val dirPath = { arch: String ->
            buildString {
                append("${platformName.decapitalized()}/")
                when {
                    isShared -> append("shared/")
                    else -> append("static/")
                }
                append("$arch/")
                when {
                    isDebug -> append("debug/")
                    else -> append("release/")
                }
            }
        }
        action(isDebug, isShared, taskName, dirPath)
    }
}

val xdiffSourcesDir = layout.projectDirectory.dir("xdiff")

// Windows ARM64
val winArm64Tasks =
    withMatrix("windows") { isDebug, isShared, baseTaskName, dirPath ->
        add(tasks.register<BuildXdiff>("${baseTaskName}Arm64") {
            onlyIf { OperatingSystem.current().isWindows }
            windowsCmakeName = "MinGW Makefiles"
            debug = isDebug
            shared = isShared
            cCompiler = "clang"
            systemName = "Windows"
            systemProcessorName = "ARM64"
            val ext = when {
                isShared -> "dll"
                else -> "lib"
            }
            outputDir(xdiffBuildDir.map { it.dir(dirPath("arm64")) }, "xdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })
    }

tasks.register<Zip>("windowsArm64Zip") {
    group = "build"
    dependsOn(winArm64Tasks)
    winArm64Tasks.forEach { task ->
        from(task.flatMap { it.outputArtifact }) {
            eachFile {
                path = file.toPath().relativeTo(xdiffBuildDirPath.get()).toString()
            }
        }
    }
    archiveBaseName = "xdiff-windows-arm64"
    destinationDirectory = layout.buildDirectory.dir("archives")
}

// Windows x64
val winX64Tasks =
    withMatrix("windows") { isDebug, isShared, baseTaskName, dirPath ->
        val ext = when {
            isShared -> "dll"
            else -> "a"
        }
        add(tasks.register<BuildXdiff>("${baseTaskName}X64") {
            debug = isDebug
            shared = isShared
            cCompiler = "x86_64-w64-mingw32-gcc-posix"
            systemName = "Windows"
            systemProcessorName = "x86_64"
            outputDir(xdiffBuildDir.map { it.dir(dirPath("x64")) }, "libxdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })
    }

tasks.register<Zip>("windowsX64Zip") {
    group = "build"
    dependsOn(winX64Tasks)
    winX64Tasks.forEach { task ->
        from(task.flatMap { it.outputArtifact }) {
            eachFile {
                path = file.toPath().relativeTo(xdiffBuildDirPath.get()).toString()
            }
        }
    }
    archiveBaseName = "xdiff-windows-x64"
    destinationDirectory = layout.buildDirectory.dir("archives")
}

// Linux
val linuxTasks =
    withMatrix("linux") { isDebug, isShared, baseTaskName, dirPath ->
        val ext = when {
            isShared -> "so"
            else -> "a"
        }
        add(tasks.register<BuildXdiff>("${baseTaskName}X64") {
            debug = isDebug
            shared = isShared
            cCompiler = "gcc-8"
            outputDir(xdiffBuildDir.map { it.dir(dirPath("x64")) }, "libxdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })

        add(tasks.register<BuildXdiff>("${baseTaskName}Arm64") {
            debug = isDebug
            shared = isShared
            cCompiler = "aarch64-linux-gnu-gcc-8"
            outputDir(xdiffBuildDir.map { it.dir(dirPath("arm64")) }, "libxdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })

        add(tasks.register<BuildXdiff>("${baseTaskName}Armv7a") {
            debug = isDebug
            shared = isShared
            cCompiler = "arm-linux-gnueabihf-gcc-8"
            outputDir(xdiffBuildDir.map { it.dir(dirPath("armv7a")) }, "libxdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })
    }

tasks.register<Zip>("linuxZip") {
    dependsOn(linuxTasks)
    linuxTasks.forEach { task ->
        from(task.flatMap { it.outputArtifact }) {
            eachFile {
                path = file.toPath().relativeTo(xdiffBuildDirPath.get()).toString()
            }
        }
    }
    archiveBaseName = "xdiff-linux"
    destinationDirectory = layout.buildDirectory.dir("archives")
}

// Android
val androidTasks =
    withMatrix("android") { isDebug, isShared, baseTaskName, dirPath ->
        val stlType = when {
            isShared -> "c++_shared"
            else -> "c++_static"
        }
        val ext = when {
            isShared -> "so"
            else -> "a"
        }
        add(tasks.register<BuildXdiff>("${baseTaskName}Arm64") {
            systemName = "Android"
            androidNdkPath = findAndroidNdk()?.absolutePathString()
            systemVersion = 35
            androidStlType = stlType
            androidAbi = "arm64-v8a"
            shared = isShared
            debug = isDebug
            outputDir(xdiffBuildDir.map { it.dir(dirPath("arm64")) }, "libxdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })

        add(tasks.register<BuildXdiff>("${baseTaskName}ArmV7a") {
            systemName = "Android"
            androidNdkPath = findAndroidNdk()?.absolutePathString()
            systemVersion = 35
            androidStlType = stlType
            androidAbi = "armeabi-v7a"
            shared = isShared
            debug = isDebug
            outputDir(xdiffBuildDir.map { it.dir(dirPath("armv7a")) }, "libxdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })

        add(tasks.register<BuildXdiff>("${baseTaskName}X86") {
            systemName = "Android"
            androidNdkPath = findAndroidNdk()?.absolutePathString()
            systemVersion = 35
            androidStlType = stlType
            androidAbi = "x86"
            shared = isShared
            debug = isDebug
            outputDir(xdiffBuildDir.map { it.dir(dirPath("x86")) }, "libxdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })

        add(tasks.register<BuildXdiff>("${baseTaskName}X64") {
            systemName = "Android"
            androidNdkPath = findAndroidNdk()?.absolutePathString()
            systemVersion = 35
            androidStlType = stlType
            androidAbi = "x86_64"
            shared = isShared
            debug = isDebug
            outputDir(xdiffBuildDir.map { it.dir(dirPath("x64")) }, "libxdiff.$ext")
            sourcesDir = xdiffSourcesDir
        })
    }

tasks.register<Zip>("androidZip") {
    group = "build"
    dependsOn(androidTasks)
    androidTasks.forEach { task ->
        from(task.flatMap { it.outputArtifact }) {
            eachFile {
                path = file.toPath().relativeTo(xdiffBuildDirPath.get()).toString()
            }
        }
    }
    archiveBaseName = "xdiff-android"
    destinationDirectory = layout.buildDirectory.dir("archives")
}

// Apple
data class AppleTarget(
    val arch: String,
    val sysName: String,
    val sysRoot: String,
)

val appleTargets = listOf(
    // mac
    AppleTarget("x86_64", "Darwin", "macosx"),
    AppleTarget("arm64", "Darwin", "macosx"),

    // ios
    AppleTarget("arm64", "iOS", "iphoneos"),
    AppleTarget("arm64", "iOS", "iphonesimulator"),
    AppleTarget("x86_64", "iOS", "iphonesimulator"),

    // tvos
    AppleTarget("arm64", "tvOS", "appletvos"),
    AppleTarget("arm64", "tvOS", "appletvsimulator"),
    AppleTarget("x86_64", "tvOS", "appletvsimulator"),

    // watchos
    AppleTarget("arm64", "watchOS", "watchos"),
    AppleTarget("arm64", "watchOS", "watchsimulator"),
    AppleTarget("x86_64", "watchOS", "watchsimulator"),
)

val appleTasks = appleTargets.flatMap { (arch, sysName, sysRoot) ->
    withMatrix(sysRoot) { isDebug, isShared, baseTaskName, dirPath ->

        if (isShared && sysRoot != "macosx") return@withMatrix
        val extension = when {
            isShared -> "dylib"
            else -> "a"
        }
        add(tasks.register<BuildXdiff>("${baseTaskName}${arch.capitalized()}") {
            onlyIf { OperatingSystem.current().isMacOsX }
            shared = isShared
            debug = isDebug
            systemName = sysName
            osxArch = arch
            osxSysroot = sysRoot
            outputDir(xdiffBuildDir.map { it.dir(dirPath(arch)) }, "libxdiff.$extension")
            sourcesDir = xdiffSourcesDir
        })
    }
}

tasks.register<Zip>("appleZip") {
    group = "build"
    dependsOn(appleTasks)
    appleTasks.forEach { task ->
        from(task.flatMap { it.outputArtifact }) {
            eachFile {
                path = file.toPath().relativeTo(xdiffBuildDirPath.get()).toString()
            }
        }
    }
    archiveBaseName = "xdiff-apple"
    destinationDirectory = layout.buildDirectory.dir("archives")
}
