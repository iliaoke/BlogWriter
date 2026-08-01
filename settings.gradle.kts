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
        // compose-markdown 通过 JitPack 分发
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "BlogWriter"
include(":app")
