import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.play.publisher)
}

// Signing credentials are read from keystore.properties (gitignored) — never hardcoded.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Play Developer API credentials, same rule: play-service-account.json is gitignored.
val playCredentialsFile = rootProject.file("play-service-account.json")

// Cloud backup's Firebase project, from the google-services.json the Firebase console hands out
// (gitignored too). Its values become the string resources Firebase initialises itself from —
// the one job the google-services plugin would do — so a fresh clone without the file still
// builds, with cloud backup simply hidden.
val googleServicesFile = file("google-services.json")
// Debug builds can point cloud backup at the Firebase Local Emulator Suite instead:
//   ./gradlew :app:installDebug -PfirebaseEmulatorHost=10.0.2.2
val firebaseEmulatorHost: String? = providers.gradleProperty("firebaseEmulatorHost").orNull
val firebaseResources: Map<String, String> = if (googleServicesFile.exists()) {
    val root = JsonSlurper().parse(googleServicesFile) as Map<*, *>
    val project = root["project_info"] as Map<*, *>
    val client = (root["client"] as List<*>).map { it as Map<*, *> }.first { client ->
        val info = client["client_info"] as Map<*, *>
        (info["android_client_info"] as Map<*, *>)["package_name"] == "com.mushaf.reader"
    }
    mapOf(
        "google_app_id" to (client["client_info"] as Map<*, *>)["mobilesdk_app_id"] as String,
        "google_api_key" to ((client["api_key"] as List<*>).first() as Map<*, *>)["current_key"] as String,
        "gcm_defaultSenderId" to project["project_number"] as String,
        "project_id" to project["project_id"] as String,
    )
} else if (firebaseEmulatorHost != null) {
    // No real project needed against the local emulators: a "demo-" project id never leaves them.
    mapOf(
        "google_app_id" to "1:000000000000:android:0000000000000000",
        "google_api_key" to "demo-api-key",
        "gcm_defaultSenderId" to "000000000000",
        "project_id" to "demo-natlu",
    )
} else {
    emptyMap()
}

android {
    namespace = "com.mushaf.reader"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mushaf.reader"
        minSdk = 24
        targetSdk = 36
        versionCode = 52
        versionName = "0.8.0"

        firebaseResources.forEach { (name, value) -> resValue("string", name, value) }
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "null")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (firebaseEmulatorHost != null) {
                buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$firebaseEmulatorHost\"")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Falls back to unsigned if keystore.properties is absent (e.g. on a fresh clone / CI).
            signingConfig = if (keystorePropertiesFile.exists())
                signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin 2.4 removed the String-based android.kotlinOptions DSL (it is a hard compile error
// now), so the JVM target lives here. Kept at 17 to match compileOptions above.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Uploads a signed AAB to Google Play over the Developer API — no browser involved.
// Falls back to disabled if play-service-account.json is absent (fresh clone / CI),
// mirroring how the release signingConfig degrades without keystore.properties.
play {
    enabled.set(playCredentialsFile.exists())
    if (playCredentialsFile.exists()) {
        serviceAccountCredentials.set(playCredentialsFile)
    }
    defaultToAppBundles.set(true)
    track.set("internal")
    // DRAFT: the build lands in Play as a draft and reaches no tester until it is
    // rolled out by hand. Switch to COMPLETED once a release should go out directly.
    releaseStatus.set(ReleaseStatus.DRAFT)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // In-app updates: the Play Store app does the download; nothing here talks to the network.
    implementation(libs.play.app.update.ktx)
    // Play Core still drags in fragment 1.1.0, which predates the ActivityResult APIs the update
    // flow is launched with (lint fails the release build over it). Pin a current one.
    implementation(libs.androidx.fragment)
    // Cloud backup: password sign-in plus one document store. Nothing reaches the network until
    // the reader creates an account or signs in.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // The daily cloud backup runs while the app is closed.
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.ui.tooling)
    // Local JVM tests only — the pure logic (juz layout, Arabic folding, day arithmetic) is kept
    // free of Android types precisely so it can be tested without a device or Robolectric.
    testImplementation(libs.junit)
    // The real org.json, because android.jar's stub throws on every call. The backup codec is
    // pure JSON over gzip, so this is what lets it be tested without a device.
    testImplementation(libs.json)
}

// ── Distribution: collect signed release builds the "old way" ────────────────────
// After every release assembly, drop a versioned copy of the signed APK into the
// parent folder (D:\new project\quran_01) named Natlu-<versionName>.apk.
// A plain task with an ad-hoc copy {} is used (not a Copy task) so the repo root is
// not declared as a tracked task output — that would overlap the whole build tree.
tasks.register("copyReleaseApk") {
    description = "Copies the signed release APK to ../ as Natlu-<versionName>.apk"
    group = "distribution"
    doLast {
        copy {
            from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
            into(rootDir.parentFile)
            rename { "Natlu-${android.defaultConfig.versionName}.apk" }
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("copyReleaseApk")
}
