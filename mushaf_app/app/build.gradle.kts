import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Signing credentials are read from keystore.properties (gitignored) — never hardcoded.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.mushaf.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mushaf.reader"
        minSdk = 24
        targetSdk = 35
        versionCode = 31
        versionName = "0.4.7"
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
            isMinifyEnabled = false
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
    debugImplementation(libs.androidx.ui.tooling)
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
