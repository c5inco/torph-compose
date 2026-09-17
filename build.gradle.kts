plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// The two modules that get published. `:demo` and `:benchmark` are not libraries.
val libraryProjects = listOf(":torph-core", ":torph-compose")

val torphGroup = property("GROUP") as String
val torphVersion = property("VERSION_NAME") as String

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
