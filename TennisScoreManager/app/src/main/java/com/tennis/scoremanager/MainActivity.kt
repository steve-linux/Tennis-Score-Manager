package com.tennis.scoremanager

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.ui.AppRoot
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val controller: MatchController get() = (application as TsmApp).controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val options by controller.options.collectAsState()
            TsmTheme {
                CompositionLocalProvider(LocalStrings provides stringsFor(options.lang)) {
                    AppRoot(controller)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    controller.screen.collect { s ->
                        // Schermo sempre acceso in attesa dell'inizio e durante la partita.
                        if (s == Screen.MATCH || s == Screen.START) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                launch {
                    controller.toasts.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.envTick.value++
        if (controller.options.value.mode == PlayMode.BANDS) controller.ble.reconnectAll()
    }

    override fun onStop() {
        super.onStop()
        controller.onBackground()
    }
}
