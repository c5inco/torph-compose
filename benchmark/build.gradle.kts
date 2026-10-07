plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "des.c5inco.torph.benchmark"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Emulators and debug-signed builds are fine for relative comparisons; real numbers need a device.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY,UNLOCKED,ACTIVITY-MISSING"
    }
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }
    targetProjectPath = ":demo"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.androidx.junit)
    // Nothing here uses Espresso, which was only here to lift androidx.test:runner. Pin the runner
    // directly: left to benchmark-macro it resolves to 1.5.2, older than :torph-compose uses.
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}

// The benchmark build type runs the macrobenchmarks; nonMinifiedRelease is the variant the
// baselineprofile plugin generates the profile against (R8 renaming would make it useless).
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" || it.buildType == "nonMinifiedRelease" }
}
