plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.sush.phosphor"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.sush.phosphor"
        minSdk = 27
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Release signing comes from the environment or ~/.gradle/gradle.properties; no keys live
    // in this repository. Without them, assembleRelease produces an unsigned APK.
    val keystore = providers.gradleProperty("PHOSPHOR_KEYSTORE").orElse(providers.environmentVariable("PHOSPHOR_KEYSTORE")).orNull
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = providers.gradleProperty("PHOSPHOR_KEYSTORE_PASSWORD").orElse(providers.environmentVariable("PHOSPHOR_KEYSTORE_PASSWORD")).orNull
            keyAlias = providers.gradleProperty("PHOSPHOR_KEY_ALIAS").orElse(providers.environmentVariable("PHOSPHOR_KEY_ALIAS")).orNull
            keyPassword = providers.gradleProperty("PHOSPHOR_KEY_PASSWORD").orElse(providers.environmentVariable("PHOSPHOR_KEY_PASSWORD")).orNull
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
