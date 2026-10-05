plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "yy.daniel.yyplayerforandroid"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "yy.daniel.yyplayerforandroid"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Explicit QA build only: trust a freshly generated localhost certificate for self-owned fixtures.
    // Normal debug/release builds retain the platform trust store and contain no QA endpoints/certificates.
    if (providers.gradleProperty("onlineQa").orNull == "true") {
        sourceSets.getByName("debug").manifest.srcFile("build/online-qa/AndroidManifest.xml")
        sourceSets.getByName("debug").res.srcDir("build/online-qa/res")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
