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
    // LiteRT (analisi del servizio) solo per i processori ARM dei telefoni: le versioni x86 (emulatori, qualche
    // Chromebook) pesano 7 MB. Su x86 l'app si installa lo stesso, manca solo l'analisi del movimento.
    packaging {
        jniLibs.excludes += setOf("lib/x86/libtensorflowlite_jni.so", "lib/x86_64/libtensorflowlite_jni.so")
    }

    defaultConfig {
        applicationId = "io.github.steve_linux.tsm"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "2.7.0"
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
        // Robolectric (test dell'interfaccia sul PC): servono risorse e manifest veri dell'app.
        unitTests.isIncludeAndroidResources = true
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
    implementation(project(":courtcore"))
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
    // Analisi del servizio: MoveNet (posizione del corpo) con LiteRT ricompilato dai sorgenti, senza Google Play Services.
    implementation(libs.litert.fdroid)
    implementation(libs.litert.api)  // le interfacce Java (InterpreterApi, Tensor, DataType), solo Java
    implementation(libs.rootencoder) {  // telefono-telecamera: registrazione e diretta RTMP con la grafica del punteggio
        // Senza la diretta WebRTC (WHIP), che l'app non usa: porta BouncyCastle, e anche se R8 ne toglie le classi i suoi
        // file di dati (.properties, 1,4 MB) restavano nell'APK di rilascio, 1,2 MB più pesante.
        // GenericStream non la tocca: la usa solo WhipStream, che l'app non chiama e R8 toglie.
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    implementation(libs.media3.exoplayer)  // lettore dei video delle partite, con i capitoli punto per punto
    implementation(libs.media3.ui)
    implementation(libs.media3.transformer)  // montaggio degli highlights sul telefono
    implementation(libs.media3.effect)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Test dell'interfaccia senza telefono: l'app vera dentro Robolectric. Solo test, niente nell'APK di rilascio.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
