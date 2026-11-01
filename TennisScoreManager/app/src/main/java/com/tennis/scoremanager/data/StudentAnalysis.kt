// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.video.ServeClipAnalysis
import kotlinx.serialization.json.Json
import java.io.File

/** Le misure dell'analisi del movimento di un allievo: per la sua scheda e per il report. */
object StudentAnalysis {
    /** Una sessione analizzata: le medie delle misure dei colpi affidabili. */
    sealed interface Means {
        data class Serve(val m: ServePose.Mean) : Means
        data class Ground(val m: ServePose.GroundMean) : Means
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Le medie dell'analisi di una sessione (null se non è analizzata o nessun colpo è affidabile). */
    fun means(storage: Storage, sess: ServeSession): Means? {
        val dir = storage.serveClipsDir(sess)
        val results = sess.serves.mapNotNull { a ->
            val f = a.clip?.let { File(File(dir, it), ServeClipAnalysis.RESULT) }?.takeIf { it.exists() } ?: return@mapNotNull null
            runCatching { json.decodeFromString(ServePoseResult.serializer(), f.readText()) }.getOrNull()
        }
        return if (sess.stroke == TrainedStroke.SERVE) ServePose.mean(results.mapNotNull { it.metrics })?.let { Means.Serve(it) }
        else ServePose.groundMean(results.mapNotNull { it.ground })?.let { Means.Ground(it) }
    }


    /** Le righe del confronto: per il servizio e per gli altri colpi le misure dell'analisi. */
    fun changes(s: com.tennis.scoremanager.ui.Strings, now: Means, before: Means?): List<Students.Change> = when (now) {
        is Means.Serve -> {
            val b = (before as? Means.Serve)?.m
            val m = now.m
            listOf(
                Students.Change(s.poseKneeMetric, b?.kneeMinDeg, m.kneeMinDeg, "°", 0),
                Students.Change(s.poseElbowMetric, b?.elbowContactDeg, m.elbowContactDeg, "°", 0),
                Students.Change(s.poseHeightMetric, b?.contactHeight, m.contactHeight, "", 0),
                Students.Change(s.poseTossMetric, b?.tossArmDeg, m.tossArmDeg, "°", 0),
                Students.Change(s.poseTimingMetric, b?.trophyToContactMs?.toFloat(), m.trophyToContactMs.toFloat(), "ms", 0),
            )
        }
        is Means.Ground -> {
            val b = (before as? Means.Ground)?.m
            val m = now.m
            listOf(
                Students.Change(s.poseInFrontMetric, b?.contactInFront, m.contactInFront, "", 0),
                Students.Change(s.poseElbowMetric, b?.elbowContactDeg, m.elbowContactDeg, "°", 0),
                Students.Change(s.poseKneeMetric, b?.kneeMinDeg, m.kneeMinDeg, "°", 0),
                Students.Change(s.poseBackswingMetric, b?.backswing, m.backswing, "", 0),
                Students.Change(s.poseFinishMetric, b?.finishHeight, m.finishHeight, "", 0),
                Students.Change(s.poseTimingGround, b?.prepToContactMs?.toFloat(), m.prepToContactMs.toFloat(), "ms", 0),
            )
        }
    }
}
