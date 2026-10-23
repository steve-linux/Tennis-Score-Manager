// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager

import android.app.Application
import com.tennis.scoremanager.ble.BleManager
import com.tennis.scoremanager.ble.HeartRateManager
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.voice.Announcer
import com.tennis.scoremanager.voice.VoicePack

class TsmApp : Application() {

    lateinit var controller: MatchController
        private set

    override fun onCreate() {
        super.onCreate()
        val voice = VoicePack(this)
        val ble = BleManager(this)
        controller = MatchController(this, Storage(this), ble, HeartRateManager(this, ble), voice, Announcer(this, voice))
    }
}
