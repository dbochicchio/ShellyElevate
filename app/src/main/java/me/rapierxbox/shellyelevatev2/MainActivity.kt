package me.rapierxbox.shellyelevatev2

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
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
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
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mScreenSaverManager
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSharedPreferences
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mShellyElevateJavascriptInterface
import me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSwipeHelper
import me.rapierxbox.shellyelevatev2.databinding.MainActivityBinding
import me.rapierxbox.shellyelevatev2.helper.ServiceHelper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
            Log.d("MainActivity", "webviewJavascriptInjectorBroadcastReceiver invoked")
            intent?.getStringExtra("javascript")?.let { extra ->
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

            Log.d("MainActivity", "screenStateReceiver invoked: " + intent?.action)
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
        @Suppress("DEPRECATION")
        webSettings.databaseEnabled = true

        @Suppress("DEPRECATION")
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH)

        webSettings.javaScriptCanOpenWindowsAutomatically = true

        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // hide system bars
        hideSystemBars()

        binding = MainActivityBinding.inflate(layoutInflater) // Inflate the binding
        setContentView(binding.root) // Set the content view using binding.root

        configureWebView()
        setupSettingsButtons()

        binding.swipeDetectionOverlay.setOnTouchListener { _, event ->
            mSwipeHelper.onTouchEvent(event)
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

    // === Called when a key is released ===
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("MainActivity", "Button pressed: " + keyCode.toString() + " - Event: " + event.toString())
        when (keyCode) {

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                // TODO: play/pause toggle
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                // TODO: play
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                // TODO: pause
                return true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                // TODO: next track
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                // TODO: previous track
                return true
            }

            140 -> { // Special power-like button (release)
                event?.let {
                    val duration = it.eventTime - it.downTime
                    if (duration >= 1000) {
                        // TODO: long press -> reboot or confirm
                    } else {
                        // TODO: short press -> screen off
                    }
                }
                return true
            }

            131 -> { // Shelly input button 1 - UP
                mMQTTServer.publishButton(0)
                return true
            }

            132 -> { // Shelly input button 1 - DOWN
                mMQTTServer.publishButton(1)
                return true
            }

            133 -> { // Shelly input button 2 - UP
                mMQTTServer.publishButton(2)
                return true
            }

            134 -> { // Shelly input button 2 - DOWN
                mMQTTServer.publishButton(3)
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

            else -> return super.onKeyUp(keyCode, event)
        }
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