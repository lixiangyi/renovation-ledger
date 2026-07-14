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
        // MPAndroidChart（与 beike_main_project 一致走 JitPack）
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "renovation-ledger"
include(":app")
