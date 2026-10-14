import java.security.MessageDigest
import com.android.build.api.variant.FilterConfiguration.FilterType

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
        // Explicit device QA coexists with the user's differently signed release and its data.
        val deviceQa = providers.gradleProperty("deviceQa").orNull == "true"
        applicationId = if (deviceQa) "yy.daniel.yyplayerforandroid.qa" else "yy.daniel.yyplayerforandroid"
        manifestPlaceholders["appLabel"] = if (deviceQa) "YYPlayer QA" else "@string/app_name"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = if (providers.gradleProperty("releasePackagingQa").orNull == "true") {
            "yy.daniel.yyplayerforandroid.ReleasePackagingInstrumentation"
        } else {
            "androidx.test.runner.AndroidJUnitRunner"
        }

        externalNativeBuild {
            cmake {
                // Restrict compilation here; packaging ABIs are controlled by splits below.
                abiFilters += listOf("arm64-v8a", "x86_64")
                // The portable core is C++17 and shares one libc++ with the JNI bridge.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=none")
            }
        }
    }

    // Produce standalone APKs per ABI, never a duplicate universal package.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
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
                // AGP 9.4 enables code + resource shrinking; the default scope is the entire app.
                // JNI contracts are kept narrowly in src/release/keepRules/native.keep.
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.single { it.filterType == FilterType.ABI }.identifier
            output.outputFileName.set(output.versionName.map { version ->
                "YYPlayer_for_Android_${version}_${abi}_${variant.buildType}.apk"
            })
        }
        if (variant.buildType == "release") {
            // Favor download size: Android extracts these at install time. ELF alignment stays 16 KB.
            variant.packaging.jniLibs.useLegacyPackaging.set(true)
            variant.packaging.jniLibs.useLegacyPackagingFromBundle.set(true)
        }
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
