// AGP 9 ships Kotlin 2.2.10; use the aligned newer KGP for Miuix 0.9.4.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
