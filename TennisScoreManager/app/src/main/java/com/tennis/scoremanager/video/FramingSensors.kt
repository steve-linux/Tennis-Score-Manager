// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Obiettivi posteriori che registrano in 1080p, dal più stretto al più largo, senza i tele (per un campo intero non
 * servono) e senza doppioni con lo stesso campo visivo.
 */
object CameraLenses {
    fun list(context: Context): List<Framing.Lens> {
        val cm = context.getSystemService(CameraManager::class.java) ?: return emptyList()
        val all = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList()).mapNotNull { id ->
            runCatching {
                val ch = cm.getCameraCharacteristics(id)
                if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) return@runCatching null
                val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return@runCatching null
                val sensor = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return@runCatching null
                val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
                if (sizes.none { it == Size(1920, 1080) }) return@runCatching null
                Framing.Lens.of(id, focal, sensor.width)
            }.getOrNull()
        }
        if (all.isEmpty()) return emptyList()
        // Il principale è il primo posteriore (di solito "0"): da lui in su, niente tele.
        val main = all.first()
        return all.filter { it.hFov >= main.hFov - 1f }
            .sortedBy { it.hFov }
            .fold(mutableListOf<Framing.Lens>()) { acc, l -> if (acc.none { abs(it.hFov - l.hFov) < 3f }) acc += l; acc }
    }
}

/**
 * Inclinazione del telefono dal sensore di gravità (o dall'accelerometro, filtrato), mediata: col telefono fermo sul
 * cavalletto basta e sbaglia di pochi decimi di grado. [rotation] dà la rotazione dello schermo al momento.
 */
class TiltSensor(context: Context, private val rotation: () -> Int) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val _tilt = MutableStateFlow<Framing.Tilt?>(null)
    val tilt: StateFlow<Framing.Tilt?> = _tilt
    val available: Boolean get() = sensor != null
    private val g = FloatArray(3)
    private var primed = false

    fun start() {
        primed = false
        sensor?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sm?.unregisterListener(this)
        _tilt.value = null
    }

    override fun onSensorChanged(e: SensorEvent) {
        // Media mobile: toglie il tremolio della mano e delle vibrazioni della recinzione.
        val a = if (primed) 0.15f else 1f
        for (i in 0..2) g[i] += a * (e.values[i] - g[i])
        primed = true
        _tilt.value = Framing.tilt(g[0], g[1], g[2], rotation())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
