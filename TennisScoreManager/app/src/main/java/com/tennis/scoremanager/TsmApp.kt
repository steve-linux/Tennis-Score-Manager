package com.tennis.scoremanager

import android.app.Application
import com.tennis.scoremanager.ble.BleManager
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.voice.Announcer
import com.tennis.scoremanager.voice.VoicePack

class TsmApp : Application() {

    lateinit var controller: MatchController
        private set

    override fun onCreate() {
        super.onCreate()
        val voice = VoicePack(this)
        controller = MatchController(this, Storage(this), BleManager(this), voice, Announcer(this, voice))
    }
}
