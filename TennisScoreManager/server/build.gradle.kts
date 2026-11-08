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

// Il kit per il Raspberry Pi (o un PC con Debian/Ubuntu): jar, script d'installazione e servizio systemd.
// Si copia sul Pi, si scompatta e si lancia "sudo ./install.sh".
val serverKit by tasks.registering(Tar::class) {
    archiveFileName.set("tsm-server-kit.tar.gz")
    compression = Compression.GZIP
    destinationDirectory.set(layout.buildDirectory.dir("kit"))
    into("tsm-server") {
        from(fatJar)
        from("deploy") {
            include("install.sh", "reset-admin.sh", "setup-https.sh", "tsm-duckdns", "tsm-tunnel")
            filePermissions { unix("rwxr-xr-x") }
        }
        from("deploy") { include("tsm-server.service", "tsm-server.default", "tsm-duckdns.service", "tsm-duckdns.timer", "tsm-tunnel.service", "tsm-tunnel.path", "tsm-tunnel.timer") }
    }
}
tasks.named("assemble") { dependsOn(serverKit) }
