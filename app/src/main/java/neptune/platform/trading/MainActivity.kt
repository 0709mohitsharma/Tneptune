package neptune.platform.trading

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orientationListener: OrientationEventListener
    private val TAG = "TradingWebView"
    private lateinit var tradingUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ConfigManager.clearCache()
        tradingUrl = ConfigManager.getTradingUrl(this)

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ensureOverlayPermission()
        applyBatteryOptimizations()
        requestNotificationPermission()
        requestStoragePermission()

        val svc = Intent(this, OverlayWebViewService::class.java)
        svc.putExtra(OverlayWebViewService.EXTRA_URL, tradingUrl)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }

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

    private fun applyBatteryOptimizations() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "App not ignoring battery optimizations")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Battery optimization check failed: ${e.message}")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1002)
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
                decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
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
        i.putExtra(OverlayWebViewService.EXTRA_ORIENTATION, resources.configuration.orientation)
        startService(i)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity onPause - setting background mode")
        val i = Intent(this, OverlayWebViewService::class.java)
        i.action = OverlayWebViewService.ACTION_SET_MODE
        i.putExtra(OverlayWebViewService.EXTRA_MODE, OverlayWebViewService.MODE_BACKGROUND)
        i.putExtra(OverlayWebViewService.EXTRA_ORIENTATION, resources.configuration.orientation)
        startService(i)
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationListener.disable()
    }
}