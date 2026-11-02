// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

// Il server del circolo (facoltativo): i giocatori invitati prenotano i campi dall'app o dalla pagina web
// (anche da iPhone), la segreteria gli manda il planning. Usa solo il JDK (com.sun.net.httpserver) e
// kotlinx.serialization: un solo file jar, leggero abbastanza per un Raspberry Pi Zero 2 W.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.tennis.scoremanager.server.MainKt")
    applicationName = "tsm-server"
}

dependencies {
    implementation(project(":courtcore"))
    testImplementation(libs.junit)
}

// tsm-server.jar con dentro tutto: si lancia con "java -jar tsm-server.jar".
val fatJar by tasks.registering(Jar::class) {
    archiveFileName.set("tsm-server.jar")
    manifest { attributes["Main-Class"] = "com.tennis.scoremanager.server.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
    }
}
tasks.named("assemble") { dependsOn(fatJar) }
