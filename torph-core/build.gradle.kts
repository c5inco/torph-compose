plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

group = property("GROUP") as String
version = property("VERSION_NAME") as String

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    withSourcesJar()
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

tasks.test { useJUnit() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "torph-core"
            pom {
                name.set("Torph for Jetpack Compose: core")
                description.set("Text segmentation, place-value number matching and diffing behind Torph for Jetpack Compose. An unofficial port of lochie/torph.")
            }
        }
    }
}
