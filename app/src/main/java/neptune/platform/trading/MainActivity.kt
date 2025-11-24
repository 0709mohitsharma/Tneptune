package neptune.platform.trading

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.OrientationEventListener
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import neptune.platform.trading.databinding.ActivityMainBinding
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orientationListener: OrientationEventListener
    private val TAG = "TradingWebView"
//        private val tradingUrl = "https://www.google.com"
    private val tradingUrl = "https://tv.dhan.co"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ensureOverlayPermission()
        requestIgnoreBatteryOptimization()
        requestNotificationPermission()

        val svc = Intent(this, OverlayWebViewService::class.java)
        svc.putExtra(OverlayWebViewService.EXTRA_URL, tradingUrl)
        ContextCompat.startForegroundService(this, svc)

        setupBackPressedHandler()

        setupOrientationListener()
    }

    private fun ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "MainActivity back pressed - sending GO_BACK intent")
                val backIntent = Intent(this@MainActivity, OverlayWebViewService::class.java).apply {
                    action = OverlayWebViewService.ACTION_GO_BACK
                }
                Log.d(TAG, "Intent created: $backIntent")

                try {
                    // Changed to regular startService since service is already running
                    startService(backIntent)
                    Log.d(TAG, "Service started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service: ${e.message}", e)
                }

                Log.d(TAG, "Moving task to back")
                moveTaskToBack(true)
            }
        }
        onBackPressedDispatcher.addCallback(callback)
    }

    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val decorView = window.decorView
                if (orientation in 45..135 || orientation in 225..315) {
                    // Landscape mode: hide status bar and navigation bar
                    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                } else {
                    // Portrait mode: show status bar and navigation bar
                    decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
        orientationListener.enable()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume - setting foreground mode")
        val i = Intent(this, OverlayWebViewService::class.java)
        i.action = OverlayWebViewService.ACTION_SET_MODE
        i.putExtra(OverlayWebViewService.EXTRA_MODE, OverlayWebViewService.MODE_FOREGROUND)
        startService(i)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity onPause - setting background mode")
        val i = Intent(this, OverlayWebViewService::class.java)
        i.action = OverlayWebViewService.ACTION_SET_MODE
        i.putExtra(OverlayWebViewService.EXTRA_MODE, OverlayWebViewService.MODE_BACKGROUND)
        startService(i)
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationListener.disable()
    }
}