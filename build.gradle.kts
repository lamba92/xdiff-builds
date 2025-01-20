@file:OptIn(ExperimentalPathApi::class)

import org.gradle.internal.os.OperatingSystem
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.walk

plugins {
    `xdiff-builds`
}

group = "io.github.lamba92"

val githubRef =
    System.getenv("GITHUB_EVENT_NAME")
        ?.takeIf { it == "release" }
        ?.let { System.getenv("GITHUB_REF") }
        ?.removePrefix("refs/tags/")
        ?.removePrefix("v")

version =
    when {
        githubRef != null -> githubRef
        else -> "1.0-SNAPSHOT"
    }

tasks {

    register<Delete>("clean") {
        delete(layout.buildDirectory)
    }

    register<Zip>("testMergeZips") {
        val os = OperatingSystem.current()
        val zipTasks = when {
            os.isMacOsX -> listOf(appleZip, androidZip)
            os.isLinux -> listOf(linuxZip, androidZip)
            else -> listOf(androidZip)
        }

        from("xdiff") {
            into("headers/include")
            include("**/*.h")
        }
        includeEmptyDirs = false
        dependsOn(zipTasks)
        archiveBaseName = "xdiff-test"
        destinationDirectory = layout.buildDirectory.dir("archives")
        zipTasks.forEach { from(it.flatMap { it.archiveFile }.map { zipTree(it) }) }
    }

    register<Zip>("mergeZips") {
        archiveBaseName = "xdiff"
        destinationDirectory = layout.buildDirectory.dir("archives")

        from("leveldb/include") {
            into("headers/include")
            include("**/*.h")
        }

        // Merge all zips in the project directory when running in CI
        Path(".").walk()
            .filter { it.name.startsWith("xdiff-") && it.name.endsWith(".zip") }
            .forEach { from(zipTree(it)) }
    }
}