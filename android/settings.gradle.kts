pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // hCaptcha Android SDK（人机验证）托管于 jitpack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "OrangeGO"
include(":app")