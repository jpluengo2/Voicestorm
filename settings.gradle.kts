pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        //maven("https://maven.google.com")
        //maven("https://plugins.gradle.org/m2/")
        //maven("https://jitpack.io")
        // Add this line for Google ML Kit dependencies
        //maven("https://mlkit.google.com/maven/")
    }
}

rootProject.name = "Voicestorm"
include(":app")
