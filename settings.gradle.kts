pluginManagement {
    repositories {
        google()           // Required for com.android.* plugins
        mavenCentral()     // For most other plugins
        gradlePluginPortal()  // Fallback for Gradle core/community plugins
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PrivatAid"
