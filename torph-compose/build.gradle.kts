plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

group = property("GROUP") as String
version = property("VERSION_NAME") as String

android {
    namespace = "des.c5inco.torph.compose"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    publishing { singleVariant("release") { withSourcesJar() } }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":torph-core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.text)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "torph-compose"
                pom {
                    name.set("Torph for Jetpack Compose")
                    description.set("Morphing text for Jetpack Compose. An unofficial port of lochie/torph.")
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
