import java.util.Locale
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import org.gradle.api.Project

val Project.localProperties: Map<String, String>
    get() {
        val localPropertiesFile = rootProject.file("local.properties")
        if (!localPropertiesFile.exists()) {
            return emptyMap()
        }
        val p = Properties()
        localPropertiesFile
            .inputStream()
            .use { p.load(it) }
        return p.entries.associate { it.key.toString() to it.value.toString() }
    }

fun Project.findAndroidSdk() =
    getAndroidSdkPathString()?.let { Path(it) }

private fun Project.getAndroidSdkPathString(): String? =
    project.findProperty("sdk.dir") as String?
        ?: project.localProperties["sdk.dir"]
        ?: System.getenv("ANDROID_SDK_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_SDK")

fun Project.findAndroidNdk() =
    getAndroidNdkPathString()
        ?.let { Path(it) }
        ?.takeIf { it.exists() }
        ?: findAndroidSdk()
            ?.resolve("ndk")
            ?.listDirectoryEntries()
            ?.find { it.isDirectory() }

private fun Project.getAndroidNdkPathString(): String? =
    project.findProperty("ndk.dir") as String?
        ?: project.localProperties["ndk.dir"]
        ?: System.getenv("ANDROID_NDK_HOME")
        ?: System.getenv("ANDROID_NDK_ROOT")
        ?: System.getenv("ANDROID_NDK")

fun <T> MutableList<T>.addAll(vararg elements: T) {
    addAll(elements)
}

fun String.decapitalized() =
    replaceFirstChar { it.lowercase(Locale.getDefault()) }