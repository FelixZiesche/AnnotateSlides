import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Release builds get their version from the git tag (see .github/workflows/release.yml):
//   ./gradlew assembleRelease -PappVersionName=1.2.3 -PappVersionCode=10203
val appVersionName = (findProperty("appVersionName") as String?) ?: "1.0.0-dev"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1

// Release signing comes from environment variables (GitHub secrets), never from the repo.
val releaseKeystorePath: String? = System.getenv("ANNOTATE_KEYSTORE_PATH")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shezik.drawanywhere"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.ziesche.annotateslides"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("ANNOTATE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANNOTATE_KEY_ALIAS") ?: "annotateslides"
                keyPassword = System.getenv("ANNOTATE_KEY_PASSWORD")
                    ?: System.getenv("ANNOTATE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Disable extra signing block 'Dependency metadata'
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.text)
    implementation(libs.ui)
    implementation(libs.androidx.ui.unit)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)
    implementation(libs.compose.color.picker.android)

    // Transitive dependencies
    androidTestImplementation(libs.androidx.monitor)
    androidTestImplementation(libs.junit)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.animation.core)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.ui.geometry)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.kotlinx.coroutines.core)
}

// Disable baseline.prof for reproducibility
tasks.whenTaskAdded {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}
