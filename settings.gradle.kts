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

include(":torph-core", ":torph-compose")

// The demo app and the benchmark module are only interesting when this build is the
// one you are working on. When it is pulled into someone else's build with
// `includeBuild`, leaving them out keeps their configuration time (and their Android
// SDK requirements) to just the two library modules. Pass -Ptorph.samples=true to
// include them anyway.
val isIncludedBuild = gradle.parent != null
val includeSamples = (startParameter.projectProperties["torph.samples"] ?: "").toBoolean()
if (!isIncludedBuild || includeSamples) {
    include(":demo", ":benchmark")
}
