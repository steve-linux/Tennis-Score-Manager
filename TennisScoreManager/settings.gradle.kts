// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Solo per RootEncoder (telecamera e diretta RTMP, Apache 2.0), che si pubblica su JitPack: compilato dai
        // sorgenti, nient'altro arriva da qui.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.pedroSG94.*") }
        }
    }
}

rootProject.name = "TennisScoreManager"
include(":app")
