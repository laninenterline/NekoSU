pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
val binaryenRepo = System.getenv("NEKO_BINARYEN_MAVEN")

dependencyResolutionManagement {
    repositories {
        binaryenRepo?.let { maven { url = uri(it) } }
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
rootProject.name = "nekosu-webui-cmp"
include(":composeApp")
