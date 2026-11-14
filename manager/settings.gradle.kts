@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

val nekoLocalMaven = System.getenv("NEKO_MAVEN_REPO")

dependencyResolutionManagement {
    repositories {
        nekoLocalMaven?.let { maven { url = uri(it) } }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "NekoSU"
include(":app")
include(":baselineprofile")
include(":lint-rules")
