// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.SrvTunnel
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Il dialogo con l'aiutante tsm-tunnel, che gira come root: il server non può toccare il sistema. Scrive solo "on" o
 * "off" in [request] e legge lo stato da [status]; se quel file manca, sul computer l'aiutante non è installato.
 */
class Tunnel(private val request: File, private val status: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): SrvTunnel {
        if (!status.isFile) return SrvTunnel()
        val s = runCatching { json.decodeFromString(SrvTunnel.serializer(), status.readText()) }.getOrDefault(SrvTunnel(state = "WORKING"))
        return s.copy(available = true)
    }

    fun set(on: Boolean) = request.writeText(if (on) "on\n" else "off\n")
}
