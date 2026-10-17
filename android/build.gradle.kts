// Top-level build file where you can add configuration options common to all sub-projects/modules.
import org.gradle.api.tasks.testing.Test
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Optional local test output root avoids cloud-sync locks without deleting user files.
val folioTestRoot = providers.gradleProperty("folioTestRoot").orNull
val folioBuildRoot = providers.gradleProperty("folioBuildRoot").orNull
    ?: if (rootDir.absolutePath.contains("OneDrive", ignoreCase = true))
        java.io.File(gradle.gradleUserHomeDir, "folio-builds/${rootDir.absolutePath.hashCode().toString(16)}").absolutePath else null
subprojects {
    val modulePath = path.trimStart(':').replace(':', '/')
    if (folioBuildRoot != null) layout.buildDirectory.set(java.io.File(folioBuildRoot, modulePath))
    tasks.withType<Test>().configureEach {
        if (folioTestRoot != null) {
            val directory = java.io.File(folioTestRoot, "$modulePath/$name")
            binaryResultsDirectory.set(java.io.File(directory, "binary"))
            reports.junitXml.outputLocation.set(java.io.File(directory, "xml"))
            reports.html.outputLocation.set(java.io.File(directory, "html"))
        }
    }
}
tasks.register<Copy>("exportDebugApk") {
    dependsOn(":app:assembleDebug")
    from(project(":app").layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.projectDirectory.dir("artifacts"))
    rename { "folio-debug.apk" }
}
