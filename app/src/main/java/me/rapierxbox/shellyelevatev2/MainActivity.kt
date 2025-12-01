package me.rapierxbox.shellyelevatev2

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rapierxbox.shellyelevatev2.Constants.INTENT_PROXIMITY_UPDATED
import me.rapierxbox.shellyelevatev2.Constants.INTENT_SCREEN_SAVER_STARTED
import me.rapierxbox.shellyelevatev2.Constants.INTENT_SCREEN_SAVER_STOPPED
import me.rapierxbox.shellyelevatev2.Constants.INTENT_SETTINGS_CHANGED
import me.rapierxbox.shellyelevatev2.Constants.INTENT_TURN_SCREEN_OFF
import me.rapierxbox.shellyelevatev2.Constants.INTENT_TURN_SCREEN_ON
import me.rapierxbox.shellyelevatev2.Constants.INTENT_WEBVIEW_INJECT_JAVASCRIPT
import me.rapierxbox.shellyelevatev2.Constants.SP_IGNORE_SSL_ERRORS
import me.rapierxbox.shellyelevatev2.Constants.SP_SETTINGS_EVER_SHOWN
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mMQTTServer
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mMediaHelper
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mScreenSaverManager
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSharedPreferences
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mShellyElevateJavascriptInterface
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSwipeHelper
import me.rapierxbox.shellyelevatev2.databinding.MainActivityBinding
import me.rapierxbox.shellyelevatev2.helper.ServiceHelper
import java.io.IOException

class MainActivity : ComponentActivity() {
    private var isActive: Boolean = false
    private var initialLoadDone = false
    private var retryJob: kotlinx.coroutines.Job? = null

    private lateinit var binding: MainActivityBinding // Declare the binding object

    private var clicksButtonRight: Int = 0
    private var clicksButtonLeft: Int = 0

    // === SETTINGS CHANGED RECEIVER ===
    private val settingsChangedBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    val webviewUrl = ServiceHelper.getWebviewUrl() // heavy computation if any
                    withContext(Dispatchers.Main) {
                        Log.d("MainActivity", "Reloading WebView due to settings change: $webviewUrl")
                        binding.myWebView.loadUrl(webviewUrl)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error reloading WebView on settings change", e)
                }
            }
        }
    }

    // === WEBVIEW JS INJECTOR RECEIVER ===
    private val webviewJavascriptInjectorBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val javascriptCode = intent?.getStringExtra("javascript") ?: return
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    val processedJs = javascriptCode.trim() // any heavy processing
                    withContext(Dispatchers.Main) {
                        Log.d("MainActivity", "Injecting JS into WebView: $processedJs")
                        binding.myWebView.evaluateJavascript(processedJs, null)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error injecting JS", e)
                }
            }
        }
    }

    // === SCREEN STATE RECEIVER ===
    private val screenStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return

            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    when (action) {
                        INTENT_TURN_SCREEN_ON -> mShellyElevateJavascriptInterface.onScreenOn()
                        INTENT_TURN_SCREEN_OFF -> mShellyElevateJavascriptInterface.onScreenOff()
                        INTENT_SCREEN_SAVER_STARTED -> mShellyElevateJavascriptInterface.onScreensaverOn()
                        INTENT_SCREEN_SAVER_STOPPED -> mShellyElevateJavascriptInterface.onScreensaverOff()
                        INTENT_PROXIMITY_UPDATED -> mShellyElevateJavascriptInterface.onMotion()
                    }

                    // UI logging only on main thread
                    withContext(Dispatchers.Main) {
                        Log.d("MainActivity", "screenStateReceiver invoked: $action")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error handling screen state: $action", e)
                }
            }
        }
    }

    var offlineFile = "file:///android_asset/offline.html"
    @SuppressLint("ClickableViewAccessibility")
    private fun setupSettingsButtons() {
        binding.settingButtonOverlayRight.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) clicksButtonRight++
            false
        }
        binding.settingButtonOverlayLeft.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (clicksButtonRight == 10) clicksButtonLeft++ else resetClicks()
                if (clicksButtonLeft == 10) startSettingsActivity()
            }
            false
        }
    }

    private fun startSettingsActivity() {
        resetClicks()
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun resetClicks() {
        clicksButtonLeft = 0
        clicksButtonRight = 0
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val webSettings: WebSettings = binding.myWebView.settings

        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.allowFileAccess = true

        @Suppress("DEPRECATION")
        webSettings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        webSettings.allowUniversalAccessFromFileURLs = true

        @Suppress("DEPRECATION")
        webSettings.databaseEnabled = true

        @Suppress("DEPRECATION")
        webSettings.setRenderPriority(WebSettings.RenderPriority.NORMAL)

        // Hint Chromium to preraster when appropriate (improves first paint)
        webSettings.offscreenPreRaster = true

        binding.myWebView.apply {

            webViewClient = object : WebViewClient() {

                // Helper to know if we already show the offline page
                private fun isOfflineUrl(url: String?): Boolean {
                    return url != null && url.contains("offline.html")
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    try {
                        if (mSharedPreferences.getBoolean(SP_IGNORE_SSL_ERRORS, false)) {
                            handler?.proceed()
                        } else {
                            handler?.cancel()
                            // show offline page if SSL blocks main frame
                            view?.post {
                                if (!isOfflineUrl(offlineFile)) view.loadUrl(offlineFile)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "SSL error handling failed", e)
                    }
                }

                // Modern HTTP error (API 23+)
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse?) {
                    try {
                        if (request.isForMainFrame) {
                            val failingUrl = request.url.toString()
                            Log.w("MainActivity", "HTTP error for main frame: $failingUrl -> ${errorResponse?.statusCode}")
                            view.post {
                                if (!isOfflineUrl(failingUrl)) view.loadUrl(offlineFile)
                            }
                        } else {
                            super.onReceivedHttpError(view, request, errorResponse)
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "onReceivedHttpError failed", e)
                    }
                }

                // Legacy and generic network errors (covers timeouts, dns, connect)
                @Deprecated("deprecated")
                override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                    try {
                        Log.w("MainActivity", "Legacy onReceivedError for $failingUrl: $description")
                        if (!isOfflineUrl(failingUrl)) view.post { view.loadUrl(offlineFile) }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "legacy onReceivedError failed", e)
                    }
                }

                // Modern variant (API 23+)
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    try {
                        if (request.isForMainFrame) {
                            val failingUrl = request.url.toString()
                            Log.w("MainActivity", "onReceivedError main frame: $failingUrl - ${error.description}")
                            if (!isOfflineUrl(failingUrl)) view.post { view.loadUrl(offlineFile) }
                        } else {
                            super.onReceivedError(view, request, error)
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "onReceivedError failed", e)
                    }
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    // Serve offline.html only when explicitly requested or when you want to map a failed host to offline
                    if (url.contains("offline.html")) {
                        return WebResourceResponse("text/html", "UTF-8", assets.open("offline.html"))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("shellyelevate:")) {
                        when (url.removePrefix("shellyelevate:")) {
                            "reload" -> view?.post { view.loadUrl(ServiceHelper.getWebviewUrl()) }
                            "offline" -> view?.post { view.loadUrl(offlineFile) }
                            "settings" -> startSettingsActivity()
                        }
                        return true
                    }
                    return false
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Log.e("MainActivity", "WebView render process crashed; didCrash=${detail.didCrash()}")

                    try {
                        val parent = view.parent as? ViewGroup ?: return true
                        val index = parent.indexOfChild(view)
                        val lp = view.layoutParams

                        // Remove before destroy
                        parent.removeViewAt(index)
                        view.destroy()

                        // Create and configure new WebView
                        val newWebView = WebView(this@MainActivity)
                        binding.myWebView = newWebView
                        configureWebView()

                        // Re‑insert at same position
                        parent.addView(newWebView, index, lp)

                        // Guard against loops: only load offline if not already showing
                        if (newWebView.url?.contains("offline.html") != true) {
                            newWebView.post { newWebView.loadUrl(offlineFile) }
                        }

                        // Breadcrumb logging for diagnostics
                        Log.i("MainActivity", "Recovered WebView after crash, showing offline page")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to recover WebView", e)
                    }

                    // We handled the crash
                    return true
                }

            }

            webChromeClient = WebChromeClient()
            addJavascriptInterface(mShellyElevateJavascriptInterface, "ShellyElevate")
            // Delay first load slightly to allow system services to settle after boot
            //postDelayed({ loadUrl(ServiceHelper.getWebviewUrl()) }, 500)
        }
    }

    private fun registerBroadcastReceivers() {
        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(settingsChangedBroadcastReceiver, IntentFilter(INTENT_SETTINGS_CHANGED))
            registerReceiver(webviewJavascriptInjectorBroadcastReceiver, IntentFilter(INTENT_WEBVIEW_INJECT_JAVASCRIPT))
            registerReceiver(screenStateReceiver, IntentFilter().apply {
                addAction(INTENT_TURN_SCREEN_ON)
                addAction(INTENT_TURN_SCREEN_OFF)
                addAction(INTENT_SCREEN_SAVER_STARTED)
                addAction(INTENT_SCREEN_SAVER_STOPPED)
                addAction(INTENT_PROXIMITY_UPDATED)
            })
        }
    }

    private fun broadcastProximity(value: Float) {
        val intent = Intent(INTENT_PROXIMITY_UPDATED)
            .putExtra(Constants.INTENT_PROXIMITY_KEY, value)

        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun safeInitialLoad() {
        lifecycleScope.launch(Dispatchers.Default) {
            val url = ServiceHelper.getWebviewUrl()
            val online = ServiceHelper.isNetworkReady(applicationContext)

            withContext(Dispatchers.Main) {
                if (online) {
                    binding.myWebView.loadUrl(url)
                    initialLoadDone = true
                    cancelRetry()
                } else {
                    binding.myWebView.loadUrl(offlineFile)
                    initialLoadDone = true
                    scheduleRetryOnlineAfterOffline(url)
                }
            }
        }
    }

    private fun scheduleRetryOnlineAfterOffline(targetUrl: String) {
        cancelRetry()
        retryJob = lifecycleScope.launch(Dispatchers.Default) {
            // Simple backoff: 2s, 4s, 8s, 16s (max 4 attempts)
            val delays = listOf(2000L, 4000L, 8000L, 16000L)
            for (d in delays) {
                kotlinx.coroutines.delay(d)
                if (!isActive) return@launch
                val online = ServiceHelper.isNetworkReady(applicationContext)
                if (online) {
                    withContext(Dispatchers.Main) {
                        binding.myWebView.loadUrl(targetUrl)
                        cancelRetry()
                    }
                    return@launch
                }
            }
            // Optional: keep a final slow retry every 30s
            while (isActive) {
                kotlinx.coroutines.delay(30000L)
                val online = ServiceHelper.isNetworkReady(applicationContext)
                if (online) {
                    withContext(Dispatchers.Main) {
                        binding.myWebView.loadUrl(targetUrl)
                        cancelRetry()
                    }
                    return@launch
                }
            }
        }
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }


    override fun onResume() {
        super.onResume()

        setScreenOptions()

        // In case screen is already on and app resumes
        mShellyElevateJavascriptInterface.onScreenOn()
        mShellyElevateJavascriptInterface.onScreensaverOff()

        if (!initialLoadDone) {
            safeInitialLoad() // does connectivity check and loads either URL or offline
            initialLoadDone = true
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start KioskService as foreground service
        ServiceHelper.ensureKioskService(applicationContext)

        // handle screen options
        setScreenOptions()

        binding = MainActivityBinding.inflate(layoutInflater) // Inflate the binding
        setContentView(binding.root) // Set the content view using binding.root

        configureWebView()
        setupSettingsButtons()
        setupSwipeOverlay()

        registerBroadcastReceivers()

        if (!mSharedPreferences.getBoolean(SP_SETTINGS_EVER_SHOWN, false))
            startActivity(Intent(this, SettingsActivity::class.java))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeOverlay() {
        binding.swipeDetectionOverlay.setOnTouchListener { _, event ->
            // Do NOT call WebView.onTouchEvent manually
            mSwipeHelper?.onTouchEvent(event)
            mScreenSaverManager.onTouchEvent(event)
            false // let event propagate naturally
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Log everything for debugging
        //Log.d("MainActivity", "dispatchKeyEvent: $event")

        val handled = onKeyUpInternal(event.keyCode, event)

        // Then always forward to WebView
        // if (!handled) binding.myWebView.post { binding.myWebView.dispatchKeyEvent(event) }

        // Return true if you handled it, otherwise let Activity super handle it
        return handled || super.dispatchKeyEvent(event)
    }

    private fun onKeyUpInternal(keyCode: Int, event: android.view.KeyEvent): Boolean {
        Log.d("MainActivity", "Key pressed: $keyCode - Event: $event")

        // handle internally
        when (keyCode) {
            140 -> { // Special power-like button (release)
                event.let {
                    val duration = it.eventTime - it.downTime
                    if (duration >= 3000) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try { Runtime.getRuntime().exec("reboot") } catch (e: IOException) {
                                Log.e("MainActivity", "Error rebooting:", e)
                            }
                        }
                        return true
                    }
                }
                return true
            }

            141 -> { // Switch 1
                switchInput(0, event.action == KeyEvent.ACTION_UP)
                return true
            }

            142 -> { // Switch 2
                switchInput(1, event.action == KeyEvent.ACTION_UP)
                return true
            }

            131 -> { // Shelly input button 1 - UP
                buttonPressed(0)
                return true
            }

            132 -> { // Shelly input button 1 - DOWN
                buttonPressed(1)
                return true
            }

            133 -> { // Shelly input button 2 - UP
                buttonPressed(2)
                return true
            }

            134 -> { // Shelly input button 2 - DOWN
                buttonPressed(3)
                return true
            }

            135 -> { // Proximity detected
                //Let everyone know we are stopping the screensaver
                broadcastProximity(0f)
                return true
            }

            136 -> { // Proximity left
                //still sending proximity to keep the screen on
                broadcastProximity(0.5f)
                return true
            }

            // MEDIA EVENTS
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                mMediaHelper.resumeOrPauseMusic()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                mMediaHelper.resumeMusic()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                mMediaHelper.pauseMusic()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                // TODO: next track?
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                // TODO: previous track?
                return true
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                mMediaHelper.volume += 10
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                mMediaHelper.volume -= 10
                return true
            }

            else -> return super.onKeyUp(keyCode, event)
        }
    }

    private fun switchInput(i: Int, state: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            mMQTTServer.publishSwitch(i, state)
        }
        mShellyElevateJavascriptInterface.onButtonPressed(100 + i)
    }

    private fun buttonPressed(i: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            mMQTTServer.publishButton(i)
        }
        mShellyElevateJavascriptInterface.onButtonPressed(i)
    }

    @Suppress("DEPRECATION")
    private fun setScreenOptions() {
        // Full screen support
        enableEdgeToEdge()

        // Prevent system from dimming or turning off the screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // hide system bars
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).apply {
            unregisterReceiver(settingsChangedBroadcastReceiver)
            unregisterReceiver(webviewJavascriptInjectorBroadcastReceiver)
            unregisterReceiver(screenStateReceiver)
        }
        cancelRetry()
        super.onDestroy()
    }

    override fun onStop() {
        cancelRetry()
        super.onStop()
    }
}