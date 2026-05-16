import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    kotlin("plugin.serialization") version "2.0.20"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// Load local.properties safely
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "neth.iecal.questphone"
    compileSdk = 36

    defaultConfig {
        applicationId = "neth.iecal.questphone"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "2.7.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API key injected at build time from local.properties — never hardcoded in source
        buildConfigField(
            "String", "KAI_API_KEY",
            "\"${localProps.getProperty("KAI_API_KEY", "")}\""
        )
    }

    signingConfigs {
        create("release") {
            val ksFile = rootProject.file("app/${localProps.getProperty("KEYSTORE_FILE", "questphone_release.jks")}")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProps.getProperty("KEY_ALIAS", "questphone")
                keyPassword = localProps.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            versionNameSuffix = "-fdroid"
            buildConfigField("Boolean", "IS_FDROID", "true")
        }
        create("play") {
            dimension = "distribution"
            versionNameSuffix = "-play"
            buildConfigField("Boolean", "IS_FDROID", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true

    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    splits {
        abi {
            // Disabled — produce a single universal APK so versionCode is consistent
            // across installs and updates work correctly on device.
            isEnable = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.hilt.work)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation (libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation.compose)
    implementation(kotlin("reflect"))


    implementation(libs.kotlinx.datetime)

    implementation(libs.multiplatform.settings)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.postgrest.kt)

    implementation(libs.ktor.client.okhttp)

    implementation (libs.androidx.work.runtime.ktx)

    implementation(libs.compose.markdown)

    implementation (libs.androidx.camera.core)
    implementation (libs.androidx.camera.camera2)
    implementation (libs.androidx.camera.lifecycle)
    implementation (libs.androidx.camera.view)
    implementation (libs.guava)

    implementation (libs.coil.compose)
    implementation(libs.coil.gif)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    ksp(libs.androidx.room.compiler)
//    implementation (libs.androidx.ui.text.google.fonts)

    add("playImplementation", platform("com.google.firebase:firebase-bom:34.3.0"))
    add("playImplementation", "com.google.firebase:firebase-messaging")
//    add("fdroidImplementation",libs.onnxruntime.android)
//    add("fdroidImplementation",project(":ai"))

    implementation(project(":data"))
    implementation(project(":core"))
    implementation(project(":backend"))
}
