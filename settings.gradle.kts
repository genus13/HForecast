pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "HForecast"

include(
    ":app",
    ":weather-core",
    ":weather-ensemble",
    ":weather-providers",
    ":weather-verification",
    ":database",
    ":location",
    ":settings",
    ":environment",
    ":astronomy",
    ":ui",
)
