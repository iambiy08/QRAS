// 1. Plugins MUST be placed at the very top
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 2. Android Configuration Block
android {
    namespace = "com.example.qras"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.qras"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Enable ViewBinding
    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}


dependencies {
    // Force core-ktx to version 1.15.0 compatible with compileSdk 36
    implementation("androidx.core:core-ktx:1.15.0")

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")

    // ZXing Barcode Scanner Library
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}