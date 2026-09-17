pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads a JDK 17 toolchain if the machine does not already have one, so a
    // fresh checkout builds without anyone having to install a specific JDK first.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "torph-compose"

include(":torph-core", ":torph-compose", ":demo", ":benchmark")
