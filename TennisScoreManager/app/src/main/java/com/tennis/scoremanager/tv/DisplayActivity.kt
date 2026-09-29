package com.tennis.scoremanager.tv

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * "Usa come tabellone": questo telefono trova da solo il telefono dell'arbitro sulla rete dell'hotspot
 * e mostra il tabellone a schermo intero, in orizzontale, con lo schermo sempre acceso.
 * Con un monitor collegato (cavo USB-C/HDMI) il tabellone va sul monitor con il suo formato 16:9
 * (Presentation) e il telefono resta libero; senza, lo schermo del telefono si può trasmettere
 * a un Chromecast ("Trasmetti schermo").
 */
class DisplayActivity : ComponentActivity() {

    private val controller get() = (application as TsmApp).controller
    private val s: Strings get() = stringsFor(controller.options.value.lang)
    private lateinit var finder: ScoreboardFinder
    private lateinit var displays: DisplayManager

    private val found = MutableStateFlow<FoundScoreboard?>(null)
    private val searching = MutableStateFlow(false)
    private val notFound = MutableStateFlow(false)
    private val external = MutableStateFlow<Display?>(null)
    private val showHere = MutableStateFlow(false)
    private var presentation: ScoreboardPresentation? = null
    private var searchJob: Job? = null
    private var lastBack = 0L
    /** Solo per le prove (adb): accetta anche il server di questo stesso telefono. */
    private var allowSelf = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = refreshExternal()
        override fun onDisplayRemoved(id: Int) = refreshExternal()
        override fun onDisplayChanged(id: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finder = ScoreboardFinder(this)
        displays = getSystemService(DisplayManager::class.java)
        allowSelf = intent.getBooleanExtra("allowSelf", false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(Color.BLACK)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Due volte indietro per uscire: un tocco per sbaglio non spegne il tabellone.
                val now = SystemClock.elapsedRealtime()
                if (found.value == null || now - lastBack < 2_500) finish()
                else Toast.makeText(this@DisplayActivity, s.displayBackAgain, Toast.LENGTH_SHORT).show()
                lastBack = now
            }
        })
        displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        refreshExternal()
        setContent { TsmTheme { DisplayContent() } }
        search()
    }

    override fun onDestroy() {
        displays.unregisterDisplayListener(displayListener)
        presentation?.dismiss()
        presentation = null
        getSystemService(ConnectivityManager::class.java)?.bindProcessToNetwork(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- ricerca e collegamento

    private fun search(manual: String? = null) {
        searchJob?.cancel()
        notFound.value = false
        searching.value = true
        searchJob = lifecycleScope.launch {
            val result = if (manual != null) {
                ScoreboardFinder.parseAddress(manual)?.let { (h, p) -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { finder.verify(h, p) } }
            } else {
                finder.find(controller.storage.lastScoreboardHost)?.takeIf { allowSelf || it.host !in TvServer.localAddresses() }
            }
            searching.value = false
            if (result != null) connect(result) else notFound.value = found.value == null
        }
    }

    private fun connect(f: FoundScoreboard) {
        // Collegato all'hotspot dell'altro telefono: il traffico della pagina deve passare dal Wi-Fi anche se
        // Android, non vedendo internet lì, preferirebbe i dati mobili.
        getSystemService(ConnectivityManager::class.java)?.bindProcessToNetwork(f.network)
        controller.storage.lastScoreboardHost = f.label
        found.value = f
        updatePresentation()
    }

    /** Dalla pagina: niente aggiornamenti da 20 secondi. Si cerca di nuovo (l'indirizzo può essere cambiato). */
    private fun onLost() {
        if (searchJob?.isActive == true) return
        searchJob = lifecycleScope.launch {
            val again = finder.find(null, timeoutMs = 30_000)?.takeIf { allowSelf || it.host !in TvServer.localAddresses() }
            if (again != null && again.label != found.value?.label) connect(again)
        }
    }

    // ---------------------------------------------------------------- monitor esterno

    private fun refreshExternal() {
        external.value = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        updatePresentation()
    }

    private fun updatePresentation() {
        val d = external.value
        val f = found.value
        val current = presentation
        if (d == null || f == null) {
            current?.dismiss()
            presentation = null
        } else if (current == null || current.display.displayId != d.displayId || current.url != f.url) {
            current?.dismiss()
            presentation = ScoreboardPresentation(this, d, f.url) { runOnUiThread { onLost() } }.also {
                runCatching { it.show() }.onFailure { presentation = null }
            }
        }
        // Tabellone sul monitor: il telefono si abbassa al minimo (resta acceso, se no si spegne anche l'uscita video).
        window.attributes = window.attributes.apply {
            screenBrightness = if (presentation != null && !showHere.value) 0.05f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    // ---------------------------------------------------------------- interfaccia

    @Composable
    private fun DisplayContent() {
        val f by found.collectAsState()
        val ext by external.collectAsState()
        val here by showHere.collectAsState()
        val busy by searching.collectAsState()
        val missing by notFound.collectAsState()
        when {
            f == null -> SearchScreen(busy, missing)
            ext != null && presentation != null && !here -> OnMonitorScreen(f!!)
            else -> AndroidView(
                factory = { ctx -> scoreboardWebView(ctx) { runOnUiThread { onLost() } } },
                update = { web -> if (web.tag != f!!.url) { web.tag = f!!.url; web.loadUrl(f!!.url) } },
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
            )
        }
    }

    @Composable
    private fun SearchScreen(busy: Boolean, missing: Boolean) {
        val str = s
        var address by remember { mutableStateOf(controller.storage.lastScoreboardHost ?: "") }
        Row(
            Modifier.fillMaxSize().background(TsmColors.Background).padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tv, null, tint = TsmColors.Ball, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(str.displayMode, color = TsmColors.TextMain, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = TsmColors.Ball, strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(str.displaySearching, color = TsmColors.TextMain, fontSize = 18.sp)
                    }
                } else if (missing) {
                    Text(str.displayNotFound, color = TsmColors.Orange, fontSize = 16.sp)
                }
                Text(str.displaySteps, color = TsmColors.TextDim, fontSize = 15.sp, lineHeight = 22.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cast, null, tint = TsmColors.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str.tvChromecastHint, color = TsmColors.TextDim, fontSize = 13.sp)
                }
            }
            Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(0.4f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(40) },
                    label = { Text(str.displayManual) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton(str.displayConnect, Icons.Filled.Link, { search(address) }, Modifier.fillMaxWidth(), enabled = address.isNotBlank() && !busy)
                GhostButton(str.displayRetry, Icons.Filled.Refresh, { search() }, Modifier.fillMaxWidth(), enabled = !busy)
                GhostButton(str.exit, Icons.AutoMirrored.Filled.ExitToApp, { finish() }, Modifier.fillMaxWidth())
            }
        }
    }

    @Composable
    private fun OnMonitorScreen(f: FoundScoreboard) {
        val str = s
        Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Tv, null, tint = TsmColors.Ball, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(str.displayOnMonitor, color = TsmColors.TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(f.label, color = TsmColors.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton(str.displayShowHere, Icons.Filled.Tv, { showHere.value = true; updatePresentation() })
                GhostButton(str.exit, Icons.AutoMirrored.Filled.ExitToApp, { finish() })
            }
        }
    }
}

/** Il tabellone su un monitor collegato col cavo: occupa tutto il monitor, il telefono resta libero. */
class ScoreboardPresentation(
    context: Context,
    display: Display,
    val url: String,
    private val onLost: () -> Unit,
) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val page = url  // dentro apply "url" sarebbe quello della WebView
        setContentView(scoreboardWebView(context, onLost).apply { tag = page; loadUrl(page) })
    }
}

/** WebView del tabellone: JavaScript acceso, fondo nero, ricarica da sola se la pagina non arriva. */
@SuppressLint("SetJavaScriptEnabled")
fun scoreboardWebView(context: Context, onLost: () -> Unit): WebView = WebView(context).apply {
    // Altezza esplicita: con WRAP_CONTENT (il default di AndroidView) la WebView calcola 1vh = 0 e i testi spariscono.
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    // Versione di prova (Android Studio): la pagina si ispeziona da chrome://inspect sul computer.
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
    setBackgroundColor(Color.BLACK)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    overScrollMode = View.OVER_SCROLL_NEVER
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    keepScreenOn = true
    addJavascriptInterface(object {
        @JavascriptInterface
        fun lost() = onLost()
    }, "TSMDisplay")
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
            Log.d("Tabellone", "${m.message()} (riga ${m.lineNumber()})")
            return true
        }
    }
    webViewClient = object : WebViewClient() {
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) view.postDelayed({ (view.tag as? String)?.let { url -> view.loadUrl(url) } }, 3_000)
        }
    }
}
