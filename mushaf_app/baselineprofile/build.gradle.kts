import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Runs against the app on a device: generates its Baseline Profile and measures startup and page
// turning with and without it. Nothing here ships in the app.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.mushaf.reader.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Profile generation without root needs Android 13 (API 33); Macrobenchmark needs 23+.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // An emulator's timings are not a phone's: fine for comparing two runs, not as absolutes.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

baselineProfile {
    // The connected emulator or phone; no Gradle-managed device is set up for this project.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// The baselineprofile plugin collects the generated profile from the test results file, and with
// AGP 9.4's test engine that file does not list it, so :app:generateReleaseBaselineProfile ends
// with "no rules generated". The generator's output is still on disk: this copies it to where the
// plugin would have put it. Regenerate with
//   ./gradlew :baselineprofile:updateAppBaselineProfile
// (a connected Android 13+ device or emulator; the app's release build must be installable on it).
val updateAppBaselineProfile = tasks.register("updateAppBaselineProfile") {
    group = "baseline profile"
    description = "Runs the profile generator on the connected device and stores its profile in :app"
    dependsOn("connectedNonMinifiedReleaseAndroidTest")
    val results = layout.buildDirectory.dir("outputs/connected_android_test_additional_output/nonMinifiedRelease")
    val target = rootProject.layout.projectDirectory.dir("app/src/release/generated/baselineProfiles")
    doLast {
        val generated = results.get().asFile.walkTopDown()
            .filter { it.name == "BaselineProfileGenerator_generate-startup-prof.txt" }
            .maxByOrNull { it.lastModified() }
            ?: throw GradleException("The generator wrote no profile; see its test report.")
        val dir = target.asFile.apply { mkdirs() }
        // With includeInStartupProfile = true the generator writes one file, named for the startup
        // profile. Its rules are the Baseline Profile; a separate startup-prof.txt would only matter
        // with dexLayoutOptimization, which this app does not turn on.
        generated.copyTo(dir.resolve("baseline-prof.txt"), overwrite = true)
        logger.lifecycle("Baseline Profile: ${generated.readLines().size} rules -> ${dir.relativeTo(rootDir)}")
    }
}
