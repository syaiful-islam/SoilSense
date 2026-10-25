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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // usb-serial-for-android is only published on JitPack; restrict JitPack to that group.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.mik3y") }
        }
    }
}

rootProject.name = "SoilSense"

include(":app")
include(":core:modbus")
