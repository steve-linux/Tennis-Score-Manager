// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tennis.scoremanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.steve_linux.tsm"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "2.5.0"
    }

    buildTypes {
        release {
            // R8: riduce e ottimizza il codice, toglie le risorse inutilizzate
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        compose = true
        buildConfig = true  // BuildConfig.VERSION_NAME in fondo alla pagina 1
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    // F-Droid: niente blocco "dependency metadata" (cifrato con la chiave di Google) nell'APK firmato
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)  // QR del tabellone TV
    implementation(libs.zxing.embedded) { isTransitive = false }  // lettura del QR per trasferire lo storico
    implementation(libs.rootencoder)  // telefono-telecamera: registrazione e diretta RTMP con la grafica del punteggio
    implementation(libs.media3.exoplayer)  // lettore dei video delle partite, con i capitoli punto per punto
    implementation(libs.media3.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
