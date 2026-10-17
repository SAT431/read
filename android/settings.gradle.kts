pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "read"
include(":app")
include(":core:model", ":core:runtime", ":core:designsystem", ":core:ui")
include(":data:local")
include(":feature:study", ":feature:workspace", ":feature:explorer")
include(":core:catalog", ":feature:library")
include(":data:remote")
include(":feature:assistant")
