package neptune.platform.trading

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.WorkSource
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class OverlayWebViewService : Service() {

    companion object {
        const val CHANNEL_ID = "overlay_webview_channel"
        const val CHANNEL_ID_IMPORTANT = "overlay_webview_important_channel"
        const val NOTIF_ID = 1001
        const val EXTRA_URL = "extra_url"

        const val ACTION_SET_MODE = "web.spidey.browser.ACTION_SET_MODE"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_ORIENTATION = "extra_orientation"
        const val MODE_BACKGROUND = 0
        const val MODE_FOREGROUND = 1

        const val ACTION_GO_BACK = "web.spidey.browser.ACTION_GO_BACK"
    }

    private lateinit var windowManager: WindowManager
    private var webView: WebView? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var urlToLoad: String? = null
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var isScreenAwake = false

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("WebViewService", "OverlayWebViewService.onCreate() - SERVICE CREATED")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        
        acquireWakeLock()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification("Trading WebView running"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotification("Trading WebView running"))
        }
    }
    
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "PeterBrowser:TradingWebViewWakeLock"
        ).apply {
            setWorkSource(WorkSource(null))
        }
    }

    private fun ensureWebView() {
        android.util.Log.d("WebViewService", "ensureWebView() called, webView=null? ${webView == null}")
        if (webView != null) {
            android.util.Log.d("WebViewService", "WebView already exists")
            return
        }

        // Enable WebView debugging for troubleshooting
        WebView.setWebContentsDebuggingEnabled(true)

        webView = WebView(this@OverlayWebViewService).apply {
            android.util.Log.d("WebViewService", "Creating new WebView")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true   // Re-enable for site functionality
            settings.databaseEnabled = true      // Re-enable for site functionality
            settings.cacheMode = WebSettings.LOAD_DEFAULT   // Allow caching of static content like images, but dynamic data remains live
            settings.allowFileAccess = false     // Security: disable file access
            settings.allowContentAccess = false  // Security: disable content access
            settings.allowUniversalAccessFromFileURLs = false  // Security: restrict
            settings.allowFileAccessFromFileURLs = false      // Security: restrict
            settings.mediaPlaybackRequiresUserGesture = true  // Battery: disable auto media
            settings.defaultTextEncodingName = "UTF-8"

            settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
            settings.setSupportZoom(false)       // Battery: disable zoom if not needed
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = false // Battery: disable zoom controls
            settings.displayZoomControls = false

            // Additional optimizations for speed and battery
            settings.setSupportMultipleWindows(false)
            settings.setGeolocationEnabled(false)
            settings.setSaveFormData(false)
            settings.setSavePassword(false)
            settings.setJavaScriptCanOpenWindowsAutomatically(false)
            settings.loadsImagesAutomatically = true
            settings.blockNetworkImage = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    android.util.Log.d("WebViewService", "WebViewClient.shouldOverrideUrlLoading called")
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    android.util.Log.d("WebViewService", "WebViewClient.onPageStarted: $url")
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    android.util.Log.d("WebViewService", "WebViewClient.onPageFinished: $url")
                    super.onPageFinished(view, url)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                    android.util.Log.d("WebViewService", "WebChromeClient.onCreateWindow called")
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = webView
                    resultMsg?.sendToTarget()
                    return true
                }
            }

            // Make sure the webview can receive key events
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()

            // Enable hardware acceleration for WebGL charts
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // Handle the hardware/back key when this overlay has focus
            setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    try {
                        if (canGoBack()) {
                            post { goBack() }  // call on UI thread
                            android.util.Log.d("WebViewService", "Overlay handled BACK: goBack()")
                            true
                        } else {
                            android.util.Log.d("WebViewService", "Overlay handled BACK: no history")
                            false
                        }
                    } catch (ex: Exception) {
                        android.util.Log.e("WebViewService", "Error handling BACK in overlay: ${ex.message}", ex)
                        false
                    }
                } else {
                    false
                }
            }
        }

        addWebViewToWindow(mode = MODE_BACKGROUND, orientation = Configuration.ORIENTATION_PORTRAIT)
        urlToLoad?.let {
            android.util.Log.d("WebViewService", "Loading URL: $it")
            webView?.loadUrl(it)
        }
    }

    private fun addWebViewToWindow(mode: Int, orientation: Int = Configuration.ORIENTATION_PORTRAIT) {
        android.util.Log.d("WebViewService", "addWebViewToWindow called, mode: $mode, orientation: $orientation")
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.d("WebViewService", "No overlay permission, stopping service")
            stopSelf()
            return
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = if (mode == MODE_BACKGROUND) {
            WindowManager.LayoutParams(
                100, 100,  // Temporarily larger for visibility
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0; y = 0
            }
        } else {
            val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON  // Keep screen on when visible
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }

        try {
            val wv = webView!!
            if (currentParams == null) {
                android.util.Log.d("WebViewService", "Adding WebView to window")
                windowManager.addView(wv, params)
            } else {
                android.util.Log.d("WebViewService", "Updating WebView layout")
                windowManager.updateViewLayout(wv, params)
            }
            currentParams = params

            if (mode == MODE_BACKGROUND) {
                wv.isFocusable = false
                wv.isClickable = false
                wv.isFocusableInTouchMode = false
                wv.setVisibility(View.INVISIBLE)  // Hide but keep running
                releaseScreenWakeLock()
                android.util.Log.d("WebViewService", "Set to background mode - screen will sleep normally")
            } else {
                wv.isFocusable = true
                wv.isClickable = true
                wv.isFocusableInTouchMode = true
                wv.setVisibility(View.VISIBLE)    // Show in foreground
                wv.requestFocus()
                acquireScreenWakeLock()
                android.util.Log.d("WebViewService", "Set to foreground mode - screen kept awake, requesting focus")
            }
        } catch (e: Exception) {
            android.util.Log.e("WebViewService", "Error managing WebView window: ${e.message}", e)
            try {
                if (webView?.parent != null) {
                    windowManager.removeViewImmediate(webView)
                }
            } catch (_: Exception) {}
            try {
                windowManager.addView(webView, params); currentParams = params
                android.util.Log.d("WebViewService", "Re-added WebView successfully")
            } catch (ex: Exception) {
                android.util.Log.e("WebViewService", "Failed to re-add WebView: ${ex.message}", ex)
                stopSelf()
            }
        }
    }
    
    private fun acquireScreenWakeLock() {
        if (!isScreenAwake) {
            try {
                wakeLock?.let {
                    if (!it.isHeld) {
                        it.acquire(10 * 60 * 1000L) // 10 minutes max, will be released on background
                        android.util.Log.d("WebViewService", "Screen wake lock acquired")
                    }
                }
                isScreenAwake = true
            } catch (e: Exception) {
                android.util.Log.e("WebViewService", "Failed to acquire wake lock: ${e.message}", e)
            }
        }
    }
    
    private fun releaseScreenWakeLock() {
        if (isScreenAwake) {
            try {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        android.util.Log.d("WebViewService", "Screen wake lock released")
                    }
                }
                isScreenAwake = false
            } catch (e: Exception) {
                android.util.Log.e("WebViewService", "Failed to release wake lock: ${e.message}", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Trading WebView Overlay", NotificationManager.IMPORTANCE_LOW)
            )
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_IMPORTANT, "Trading WebView Active", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun buildNotification(text: String, important: Boolean = false): Notification {
        val channelId = if (important) CHANNEL_ID_IMPORTANT else CHANNEL_ID
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Trading WebView")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(if (important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        
        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("WebViewService", "onStartCommand called with action: ${intent?.action} - SERVICE PID: ${android.os.Process.myPid()}")

        // Add a safety check to ensure we have a valid intent
        if (intent == null) {
            android.util.Log.d("WebViewService", "Null intent received")
            return START_STICKY
        }

        val url = intent.getStringExtra(EXTRA_URL)
        if (url != null) {
            urlToLoad = url
            android.util.Log.d("WebViewService", "Set URL to load: $url")
        }

        when (intent.action) {
            ACTION_SET_MODE -> {
                val mode = intent.getIntExtra(EXTRA_MODE, MODE_BACKGROUND)
                val orientation = intent.getIntExtra(EXTRA_ORIENTATION, Configuration.ORIENTATION_PORTRAIT)
                android.util.Log.d("WebViewService", "Setting mode to: $mode, orientation: $orientation")
                setMode(mode, orientation)
            }
            ACTION_GO_BACK -> {
                android.util.Log.d("WebViewService", "Processing GO_BACK action - IMMEDIATELY")
                // Make sure the WebView exists before asking it to goBack
                ensureWebView()
                android.util.Log.d("WebViewService", "WebView ensured, calling goBackDirect")
                goBackDirect()
                // No Thread.sleep(100) anymore - removed from main thread
            }
            else -> {
                android.util.Log.d("WebViewService", "Default case - ensuring WebView")
                ensureWebView()
                urlToLoad?.let { u ->
                    android.util.Log.d("WebViewService", "Loading URL in default case: $u")
                    webView?.post { webView?.loadUrl(u) }
                }
            }
        }

        return START_STICKY
    }

    fun setMode(mode: Int, orientation: Int = Configuration.ORIENTATION_PORTRAIT) {
        android.util.Log.d("WebViewService", "setMode called with: $mode, orientation: $orientation")
        ensureWebView()
        webView?.post {
            android.util.Log.d("WebViewService", "Executing addWebViewToWindow from post")
            addWebViewToWindow(mode, orientation)
            if (mode == MODE_FOREGROUND) {
                webView?.requestFocus()
                android.util.Log.d("WebViewService", "Requested focus for foreground mode")
            }
        }
    }

    // Fixed goBackDirect to run on UI thread properly
    fun goBackDirect() {
        android.util.Log.d("WebViewService", "goBackDirect called - START")
        val w = webView
        android.util.Log.d("WebViewService", "WebView instance: $w, canGoBack(): ${w?.canGoBack()}")

        if (w != null) {
            w.post {
                try {
                    if (w.canGoBack()) {
                        android.util.Log.d("WebViewService", "Executing goBack() - WILL NAVIGATE")
                        w.goBack()
                        android.util.Log.d("WebViewService", "goBack() executed successfully")
                    } else {
                        android.util.Log.d("WebViewService", "Cannot go back - no history available")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WebViewService", "Error running goBack on UI thread: ${e.message}", e)
                }
            }
        } else {
            android.util.Log.d("WebViewService", "WebView is null - cannot go back")
        }
        android.util.Log.d("WebViewService", "goBackDirect called - END")
    }

    override fun onDestroy() {
        android.util.Log.d("WebViewService", "onDestroy called - SERVICE DESTROYED")
        releaseScreenWakeLock()
        try {
            webView?.let {
                if (it.parent != null) windowManager.removeViewImmediate(it)
                it.stopLoading()
                it.loadUrl("about:blank")
                it.removeAllViews()
                it.destroy()
            }
        } catch (e: Exception) {
            android.util.Log.e("WebViewService", "Error in onDestroy: ${e.message}", e)
        }
        webView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.d("WebViewService", "onTaskRemoved called - STOPPING SERVICE")
        stopSelf()  // Stop the service when app is removed from recent apps
        super.onTaskRemoved(rootIntent)
    }
}