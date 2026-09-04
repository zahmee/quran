import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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

android {
    namespace = "com.mushaf.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mushaf.reader"
        minSdk = 24
        targetSdk = 36
        versionCode = 45
        versionName = "0.6.4"
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
// parent folder (D:\new project\quran_01) named QuranAlQari-<versionName>.apk.
// A plain task with an ad-hoc copy {} is used (not a Copy task) so the repo root is
// not declared as a tracked task output — that would overlap the whole build tree.
tasks.register("copyReleaseApk") {
    description = "Copies the signed release APK to ../ as QuranAlQari-<versionName>.apk"
    group = "distribution"
    doLast {
        copy {
            from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
            into(rootDir.parentFile)
            rename { "QuranAlQari-${android.defaultConfig.versionName}.apk" }
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("copyReleaseApk")
}
