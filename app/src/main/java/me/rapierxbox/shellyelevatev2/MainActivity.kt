package me.rapierxbox.shellyelevatev2

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.ConnectivityManager.*
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException


class MainActivity : ComponentActivity() {
    private lateinit var binding: MainActivityBinding // Declare the binding object

    private var clicksButtonRight: Int = 0
    private var clicksButtonLeft: Int = 0

    private var settingsChangedBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "settingsChangedBroadcastReceiver invoked")
            binding.myWebView.loadUrl(ServiceHelper.getWebviewUrl())
        }
    }

    private var webviewJavascriptInjectorBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("javascript")?.let { extra ->
                Log.d("MainActivity", "webviewJavascriptInjectorBroadcastReceiver invoked: $extra")
                binding.myWebView.evaluateJavascript(extra, null)
            }
        }
    }

    private val screenStateReceiver : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val event = when (action) {
                INTENT_TURN_SCREEN_ON -> mShellyElevateJavascriptInterface.onScreenOn()
                INTENT_TURN_SCREEN_OFF -> mShellyElevateJavascriptInterface.onScreenOff()
                INTENT_SCREEN_SAVER_STARTED-> mShellyElevateJavascriptInterface.onScreensaverOn()
                INTENT_SCREEN_SAVER_STOPPED -> mShellyElevateJavascriptInterface.onScreensaverOff()
                INTENT_PROXIMITY_UPDATED -> mShellyElevateJavascriptInterface.onMotion()
                else -> return
            }

            Log.d("MainActivity", "screenStateReceiver invoked: " + intent.action)
        }
    }

    var offlineFile = "file:///android_asset/offline.html"

    private val networkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { binding.myWebView.loadUrl(ServiceHelper.getWebviewUrl()) }
        }

        override fun onLost(network: Network) {
            runOnUiThread { binding.myWebView.loadUrl(ServiceHelper.getWebviewUrl()) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSettingsButtons() {
        binding.settingButtonOverlayRight.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                clicksButtonRight++
            }
            return@setOnTouchListener false
        }

        binding.settingButtonOverlayLeft.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (clicksButtonRight == 10) {
                    clicksButtonLeft++
                } else {
                    clicksButtonRight = 0
                    clicksButtonLeft = 0
                }

                if (clicksButtonLeft == 10) {
                    startActivity(Intent(this, SettingsActivity::class.java))

                    clicksButtonRight = 0
                    clicksButtonLeft = 0
                }
            }
            return@setOnTouchListener false
        }
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
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH)

        binding.myWebView.apply {

            webViewClient = object : WebViewClient() {
                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    if (mSharedPreferences.getBoolean(SP_IGNORE_SSL_ERRORS, false)) {
                        handler?.proceed()
                    } else {
                        super.onReceivedSslError(view, handler, error)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // inject currentUrl
                    val failedUrl = ServiceHelper.getWebviewUrl()
                    val js = "window.originalUrl = '${failedUrl.replace("'", "\\'")}';"
                    view?.evaluateJavascript(js, null)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    // Load a local HTML file from assets
                    view.loadUrl(offlineFile)
                }

                // Catch HTTP errors (like 404, 500)
                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse?
                ) {
                    view.loadUrl(offlineFile)
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url.toString()
                    if (url.contains("offline.html")) {
                        return WebResourceResponse(
                            "text/html", "UTF-8",
                            assets.open("offline.html")
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(mShellyElevateJavascriptInterface, "ShellyElevate")
            loadUrl(ServiceHelper.getWebviewUrl())
        }
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()

        //This will reload the screen after the screenSaver.
        if (binding.myWebView.originalUrl?.toHttpUrlOrNull() != ServiceHelper.getWebviewUrl().toHttpUrlOrNull())
            binding.myWebView.loadUrl(ServiceHelper.getWebviewUrl())

        // In case screen is already on and app resumes
        mShellyElevateJavascriptInterface.onScreenOn()
        mShellyElevateJavascriptInterface.onScreensaverOff()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // hide system bars
        hideSystemBars()

        binding = MainActivityBinding.inflate(layoutInflater) // Inflate the binding
        setContentView(binding.root) // Set the content view using binding.root

        configureWebView()
        //registerNetworkListener()
        setupSettingsButtons()

        binding.swipeDetectionOverlay.setOnTouchListener { _, event ->
            mSwipeHelper?.onTouchEvent(event)
            mScreenSaverManager.onTouchEvent(event)
            binding.myWebView.onTouchEvent(event)

            return@setOnTouchListener true
        }

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

        if (!mSharedPreferences.getBoolean(SP_SETTINGS_EVER_SHOWN, false))
            startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keyEventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        binding.myWebView.dispatchKeyEvent(keyEventDown)

        return super.onKeyDown(keyCode, event)
    }

    // === Called when a key is released ===
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("MainActivity", "Button pressed: $keyCode - Event: $event")

        // dispatch to webview
        val keyEventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        binding.myWebView.dispatchKeyEvent(keyEventUp)

        // handle internally
        when (keyCode) {
            140 -> { // Special power-like button (release)
                event?.let {
                    val duration = it.eventTime - it.downTime
                    if (duration >= 3000) {
                        // long press -> reboot or confirm
                        try {
                            Runtime.getRuntime().exec("reboot")
                        } catch (e: IOException) {
                            Log.e("MQTT", "Error rebooting:", e)
                        }
                    } else {
                        // TODO: short press -> screen off
                    }
                }
                return true
            }

            141 -> { // Switch 1
                switchInput(0, event?.action == KeyEvent.ACTION_UP)
                return true
            }

            142 -> { // Switch 2
                switchInput(1, event?.action == KeyEvent.ACTION_UP)
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
                //Let everyone know we are starting the screensaver
                LocalBroadcastManager.getInstance(ShellyElevateApplication.mApplicationContext)
                    .sendBroadcast(Intent(INTENT_PROXIMITY_UPDATED))
                return super.onKeyUp(keyCode, event)
            }

            136 -> { // Proximity left
                return super.onKeyUp(keyCode, event)
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
        mMQTTServer.publishSwitch(i, state)
        // simulate button pressed as 100+i (ie: 100, 101)
        mShellyElevateJavascriptInterface.onButtonPressed(100 + i)
    }

    private fun buttonPressed(i: Int) {
        mMQTTServer.publishButton(i)
        mShellyElevateJavascriptInterface.onButtonPressed(i)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
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
        super.onDestroy()
    }
}