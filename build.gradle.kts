plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
}

// The two modules that get published. `:demo` and `:benchmark` are not libraries.
val libraryProjects = listOf(":torph-core", ":torph-compose")

val torphGroup = property("GROUP") as String
val torphVersion = property("VERSION_NAME") as String

// POM metadata both libraries share. Each module sets its own artifactId, name and description.
libraryProjects.forEach { path ->
    project(path) {
        plugins.withId("maven-publish") {
            configure<PublishingExtension> {
                publications.withType<MavenPublication>().configureEach {
                    pom {
                        url.set("https://github.com/c5inco/torph-compose")
                        licenses {
                            license {
                                name.set("MIT License")
                                url.set("https://opensource.org/licenses/MIT")
                            }
                        }
                        developers {
                            developer {
                                id.set("c5inco")
                                name.set("Chris Sinco")
                            }
                        }
                        scm {
                            url.set("https://github.com/c5inco/torph-compose")
                            connection.set("scm:git:https://github.com/c5inco/torph-compose.git")
                            developerConnection.set("scm:git:ssh://git@github.com/c5inco/torph-compose.git")
                        }
                    }
                }
            }
        }
    }
}

tasks.register("publishLocal") {
    group = "torph"
    description = "Builds both libraries and installs them into ~/.m2/repository (mavenLocal)."
    dependsOn(libraryProjects.map { "$it:publishToMavenLocal" })
}

tasks.register("torphCoordinates") {
    group = "torph"
    description = "Prints the Maven coordinates this checkout publishes."
    val summary = """

        Torph for Jetpack Compose $torphVersion

          implementation("$torphGroup:torph-compose:$torphVersion")   // Compose UI + core
          implementation("$torphGroup:torph-core:$torphVersion")      // core only, no Android

        ./gradlew publishLocal installs both into ~/.m2/repository. Add mavenLocal() to
        the repositories in your settings.gradle.kts to resolve them from there.

    """.trimIndent()
    doLast { println(summary) }
}
