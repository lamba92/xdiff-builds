@file:OptIn(ExperimentalPathApi::class)

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.walk
import org.gradle.internal.os.OperatingSystem

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

fun Zip.commonSpec() {
    from("xdiff") {
        into("headers/include")
        include("**/*.h")
    }
    includeEmptyDirs = false
    destinationDirectory = layout.buildDirectory.dir("archives")
}

tasks {

    register<Delete>("clean") {
        delete(layout.buildDirectory)
    }

    register<Zip>("testMergeZips") {
        commonSpec()
        val os = OperatingSystem.current()
        val zipTasks = when {
            os.isMacOsX -> listOf(appleZip, androidZip)
            os.isLinux -> listOf(linuxZip, androidZip)
            else -> listOf(androidZip)
        }
        dependsOn(zipTasks)
        archiveBaseName = "xdiff-test"
        zipTasks.forEach { from(it.flatMap { it.archiveFile }.map { zipTree(it) }) }
    }

    register<Zip>("mergeZips") {
        commonSpec()
        archiveBaseName = "xdiff"
        // Merge all zips in the project directory when running in CI
        Path(".").walk()
            .filter { it.name.startsWith("xdiff-") && it.name.endsWith(".zip") }
            .forEach { from(zipTree(it)) }
    }
}