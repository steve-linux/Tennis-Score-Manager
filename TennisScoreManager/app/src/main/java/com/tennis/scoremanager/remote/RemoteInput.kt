// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.remote

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.model.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Telecomando associato a un giocatore: [descriptor] è l'identità stabile di Android, [name] quello che si mostra. */
data class RemoteInfo(val descriptor: String, val name: String)

/**
 * Telecomandini Bluetooth da selfie (o qualsiasi tastiera Bluetooth/USB) al posto dei braccialetti.
 * Android li vede come tastiere: si accoppiano dalle impostazioni Bluetooth del telefono, senza permessi per l'app,
 * e i loro tasti arrivano all'Activity in primo piano (quindi solo a schermo acceso, con l'app davanti).
 * Ogni telecomando è di un giocatore: un clic = punto, doppio clic o pressione lunga = annulla ([PressDetector]).
 */
class RemoteInput(context: Context, private val storage: Storage, private val scope: CoroutineScope) {

    private val app = context.applicationContext
    private val inputManager = app.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val detector = PressDetector()
    private var timer: Job? = null

    private val _assigned = MutableStateFlow(load())
    val assigned: StateFlow<Map<Side, RemoteInfo>> = _assigned

    /** Descrittori dei dispositivi collegati adesso. */
    private val _connected = MutableStateFlow(connectedNow())
    val connected: StateFlow<Set<String>> = _connected

    /** Giocatore per cui si aspetta il tasto da associare (null = nessuna associazione in corso). */
    val capture = MutableStateFlow<Side?>(null)

    /** Ultimo clic ricevuto da un telecomando associato (giocatore, ora): la pagina delle opzioni lo mostra come prova. */
    val lastPress = MutableStateFlow<Pair<Side, Long>?>(null)

    /** Attivo solo nella modalità Telecomandi: altrimenti i tasti fanno quello che fanno sempre. */
    var enabled = false
        set(v) {
            field = v
            if (!v) {
                capture.value = null
                detector.reset()
                timer?.cancel()
            }
        }

    /** Gesto di un giocatore, già riconosciuto. */
    var onGesture: (Side, Gesture) -> Unit = { _, _ -> }

    init {
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refreshConnected()
            override fun onInputDeviceRemoved(deviceId: Int) = refreshConnected()
            override fun onInputDeviceChanged(deviceId: Int) = refreshConnected()
        }, Handler(Looper.getMainLooper()))
    }

    fun startCapture(side: Side) {
        capture.value = side
    }

    fun cancelCapture() {
        capture.value = null
    }

    fun remove(side: Side) {
        set(side, null)
    }

    fun swap() {
        val a = _assigned.value
        set(Side.P1, a[Side.P2])
        set(Side.P2, a[Side.P1])
        detector.reset()
    }

    /**
     * Da Activity.dispatchKeyEvent. true = tasto usato qui (non arriva ad Android: niente volume che cambia).
     * Solo i tasti dei telecomandi associati (o quello da associare) vengono presi, gli altri passano.
     */
    fun onKey(event: KeyEvent): Boolean {
        if (!enabled) return false
        val device = event.device ?: return false
        val wanted = capture.value
        if (wanted != null) {
            if (!canAssign(device, event.keyCode)) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                set(wanted, RemoteInfo(device.descriptor, device.name.orEmpty().ifBlank { "?" }))
                capture.value = null
            }
            return true
        }
        val side = _assigned.value.entries.firstOrNull { it.value.descriptor == device.descriptor }?.key ?: return false
        val dev = side.name
        val gesture = when (event.action) {
            KeyEvent.ACTION_DOWN -> detector.down(dev, event.keyCode, event.eventTime)
            KeyEvent.ACTION_UP -> detector.up(dev, event.keyCode, event.eventTime)
            else -> null
        }
        if (gesture != null) emit(side, gesture)
        schedule()
        return true
    }

    private fun emit(side: Side, g: Gesture) {
        lastPress.value = side to SystemClock.uptimeMillis()
        onGesture(side, g)
    }

    /** Il punto del clic singolo arriva quando scade l'attesa del doppio clic. */
    private fun schedule() {
        timer?.cancel()
        val at = detector.nextDeadline() ?: return
        timer = scope.launch {
            delay((at - SystemClock.uptimeMillis()).coerceAtLeast(0))
            for (dev in detector.due(SystemClock.uptimeMillis())) emit(Side.valueOf(dev), Gesture.POINT)
            schedule()
        }
    }

    /**
     * Tastiere vere, collegate dall'esterno: non i tasti del telefono (volume, indietro) né quelli simulati.
     * Prima di Android 10 non si sa se un dispositivo è esterno: si escludono almeno i tasti di sistema.
     */
    private fun canAssign(device: InputDevice, keyCode: Int): Boolean {
        if (device.isVirtual) return false
        if (Build.VERSION.SDK_INT >= 29) return device.isExternal
        return keyCode !in setOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_MENU,
        )
    }

    private fun set(side: Side, info: RemoteInfo?) {
        val m = _assigned.value.toMutableMap()
        // Un telecomando appartiene a un solo giocatore: se era dell'altro lo si toglie.
        if (info != null) m.entries.removeAll { it.key != side && it.value.descriptor == info.descriptor }
        if (info == null) m.remove(side) else m[side] = info
        _assigned.value = m
        for (s in Side.entries) storage.setRemote(s == Side.P1, m[s]?.descriptor, m[s]?.name)
    }

    private fun load(): Map<Side, RemoteInfo> = buildMap {
        for (s in Side.entries) {
            val d = storage.remoteDescriptor(s == Side.P1) ?: continue
            put(s, RemoteInfo(d, storage.remoteName(s == Side.P1) ?: "?"))
        }
    }

    private fun refreshConnected() {
        _connected.value = connectedNow()
    }

    private fun connectedNow(): Set<String> =
        InputDevice.getDeviceIds().mapNotNull { InputDevice.getDevice(it)?.descriptor }.toSet()
}
