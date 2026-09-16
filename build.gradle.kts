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

// Where `publishLocalRepo` writes: a plain Maven repository you can point another
// build at with `maven { url = uri("...") }`, or copy somewhere shared.
val localRepoPath = layout.buildDirectory.dir("maven-repo").get().asFile.absolutePath

tasks.register("publishLocal") {
    group = "torph"
    description = "Builds both libraries and installs them into ~/.m2/repository (mavenLocal)."
    dependsOn(libraryProjects.map { "$it:publishToMavenLocal" })
}

tasks.register("publishLocalRepo") {
    group = "torph"
    description = "Builds both libraries into a Maven repository under build/maven-repo."
    dependsOn(
        ":torph-core:publishMavenPublicationToLocalRepoRepository",
        ":torph-compose:publishReleasePublicationToLocalRepoRepository",
    )
    val message = "Wrote $torphGroup:torph-core and $torphGroup:torph-compose $torphVersion to $localRepoPath"
    doLast { println(message) }
}

tasks.register("torphCoordinates") {
    group = "torph"
    description = "Prints the Maven coordinates and dependency snippets for this checkout."
    val summary = """

        Torph for Jetpack Compose $torphVersion

          implementation("$torphGroup:torph-compose:$torphVersion")   // Compose UI + core
          implementation("$torphGroup:torph-core:$torphVersion")      // core only, no Android

        Publish this checkout with one of:

          ./gradlew publishLocal       -> ~/.m2/repository, consume via mavenLocal()
          ./gradlew publishLocalRepo   -> $localRepoPath

        Or skip publishing entirely and pull this directory into your build with
        includeBuild(). See "Use it in your project" in the README.

    """.trimIndent()
    doLast { println(summary) }
}
