pluginManagement {
    repositories {
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
        // usb-serial-for-android is published on JitPack (group com.github.mik3y).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RS485Scope"
include(":app")
