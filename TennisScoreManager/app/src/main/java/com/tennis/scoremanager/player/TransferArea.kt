// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import android.util.Base64
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.transfer.TransferBundle
import com.tennis.scoremanager.transfer.TransferServer
import com.tennis.scoremanager.tv.TvServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Il trasferimento tra telefoni col QR: questo telefono apre un piccolo server con le partite scelte (con le foto
 * e le schede dei loro giocatori), l'altro legge il QR e le aggiunge al suo storico.
 * Quello che serve del resto dell'app è in [PlayerHost].
 */
class TransferArea(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Indirizzo da mostrare nel QR mentre questo telefono invia; null = non sta inviando. */
    val transferUrl = MutableStateFlow<String?>(null)
    /** Quante volte l'altro telefono ha già scaricato il pacchetto (per dire "Inviato"). */
    val transferServed = MutableStateFlow(0)
    internal var transferServer: TransferServer? = null

    /** Le partite che si possono inviare: finite e sospese, dalla più recente. */
    fun sendableMatches(): List<MatchRecord> = (storage.loadFinished() + storage.loadUnfinished()).distinctBy { it.id }.sortedByDescending { it.updatedAt }

    /**
     * Apre il server e prepara il QR con le partite [ids] scelte da chi invia (null = tutte). Con le partite viaggiano
     * solo le foto e le schede dei loro giocatori. false = nessuna rete locale (Wi-Fi o hotspot) per l'altro telefono.
     */
    fun startSending(ids: Set<String>? = null): Boolean {
        stopSending()
        val host = TvServer.localAddresses().firstOrNull() ?: return false
        val server = TransferServer(
            payload = {
                val all = (storage.loadFinished() + storage.loadUnfinished()).filter { ids == null || it.id in ids }
                // Le foto viaggiano con le partite (piccole: qualche decina di kB l'una), solo dei giocatori di queste.
                val keys = all.flatMap { m -> Side.entries.flatMap { PlayerStats.names(m.setup, it) } }.map { PlayerStats.key(it) }.toSet()
                val photos = storage.photoKeys().filter { it in keys }.associateWith { k -> Base64.encodeToString(storage.photoFile(k).readBytes(), Base64.NO_WRAP) }
                val matchPhotos = all.associate { m ->
                    m.id to storage.matchPhotos(m.id).map { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
                }.filterValues { it.isNotEmpty() }
                storage.json.encodeToString(
                    TransferBundle.serializer(), TransferBundle(
                        matches = all, photos = photos, matchPhotos = matchPhotos,
                        players = all.flatMap { com.tennis.scoremanager.data.Players.slots(it.setup) }.mapNotNull { playerById(it.id) }.distinctBy { it.id }
                            .map { com.tennis.scoremanager.data.PlayerProfile(it.id, it.name, it.firstName, it.lastName, it.nickname, it.hand, aliases = it.aliases) },
                    ),
                ).toByteArray()
            },
            onServed = { transferServed.value++ },
        )
        val port = runCatching { server.start() }.getOrNull() ?: return false
        transferServer = server
        transferServed.value = 0
        transferUrl.value = TransferServer.url(host, port, server.token)
        return true
    }

    fun stopSending() {
        transferServer?.stop()
        transferServer = null
        transferUrl.value = null
    }

    /** Testo letto dal QR dell'altro telefono: scarica storico e partite sospese e li aggiunge a questo telefono. */
    fun receiveFrom(text: String) {
        val s = strings
        if (!TransferServer.isTransferUrl(text)) {
            toast(s.transferBadCode)
            return
        }
        scope.launch {
            val result = withContext(io) {
                runCatching {
                    val bundle = storage.json.decodeFromString(TransferBundle.serializer(), TransferServer.fetch(text).decodeToString())
                    // Una foto arrivata non sostituisce quella che questo telefono ha già per lo stesso nome.
                    bundle.photos.forEach { (k, b64) ->
                        if (!storage.photoFile(k).exists()) runCatching { storage.savePhoto(k, Base64.decode(b64, Base64.DEFAULT)) }
                    }
                    // Foto ricordo: solo per le partite che qui non ne hanno già.
                    bundle.matchPhotos.forEach { (id, list) ->
                        if (storage.matchPhotos(id).isEmpty()) list.forEach { b64 ->
                            runCatching { storage.addMatchPhoto(id, Base64.decode(b64, Base64.DEFAULT)) }
                        }
                    }
                    importRecords(bundle.matches, bundle.players)
                }.getOrNull()
            }
            toast(
                when {
                    result == null -> s.transferFailed
                    result.first + result.second == 0 -> s.transferEmpty
                    else -> s.historyImported(result.first, result.second)
                },
            )
            refreshHistory()
            refreshSaved()
            photosVersion.value++
        }
    }
}
