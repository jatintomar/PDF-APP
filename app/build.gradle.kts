plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pdfutility.tools"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pdfutility.tools"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")

    configurations.all {
        resolutionStrategy {
            force("androidx.core:core:1.10.1")
            force("androidx.core:core-ktx:1.10.1")
            force("androidx.annotation:annotation-experimental:1.3.0")
        }
    }
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    
    // Coroutines for background threading
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Lifecycle extensions for coroutine scopes in Activities/Fragments
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

    // PDFBox for Android (Perfect for Lock, Unlock, Merge, Split)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // High performance PDF Viewer
    implementation("com.artifex.mupdf:viewer:1.28.0a")

    // AndroidX ExifInterface for Image Processing & EXIF metadata handling
    implementation("androidx.exifinterface:exifinterface:1.3.6")
}
