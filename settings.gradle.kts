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
        // Agora's own Maven repo — required for the real-time voice chat SDK
        // (Phase 5). Not on Maven Central.
        maven { url = uri("https://download.agora.io/sdk/release/") }
    }
}

rootProject.name = "GlobalMMORPG"
include(":app")
