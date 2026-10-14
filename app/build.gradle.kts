import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Upstream Linux CI artifact; one verified FFmpeg and its pinned libc++ for both JNI bridges.
val mpvArchive = configurations.create("mpvArchive") { isTransitive = false }
dependencies { add(mpvArchive.name, "dev.jdtech.mpv:libmpv:1.0.0@aar") }
val extractMpv = tasks.register<Copy>("extractMpv") {
    val archive = mpvArchive.singleFile
    from(zipTree(archive)); into(layout.buildDirectory.dir("generated/mpv"))
    include("jni/arm64-v8a/*.so", "jni/x86_64/*.so")
    exclude("**/libplayer.so")
    doFirst {
        val digest = MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == "df146592480fc8418415a06b1f1a1d6318b0088e21f52254b0e9a82b61ca8fa2") { "libmpv artifact checksum mismatch" }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(extractMpv) }

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

        ndk {
            // arm64-v8a is the shipping ABI; x86_64 keeps the emulator path working.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                // The portable core is C++17 and shares one libc++ with the JNI bridge.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=none")
            }
        }
    }

    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("../native/CMakeLists.txt")
            version = "3.22.1"
        }
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        compose = true
    }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/mpv/jni").get().asFile)
    // Explicit QA build only: trust a freshly generated localhost certificate for self-owned fixtures.
    // Normal debug/release builds retain the platform trust store and contain no QA endpoints/certificates.
    if (providers.gradleProperty("onlineQa").orNull == "true") {
        sourceSets.getByName("debug").manifest.srcFile("build/online-qa/AndroidManifest.xml")
        sourceSets.getByName("debug").res.srcDir("build/online-qa/res")
    }
}

// CMake imports the very same libc++ file as jniLibs; no arbitrary pickFirst between NDK versions.
tasks.matching { it.name.startsWith("configureCMake") }.configureEach { dependsOn(extractMpv) }

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
    implementation(libs.media3.session)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
