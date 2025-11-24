package web.spidey.browser

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import web.spidey.browser.databinding.ActivityMainBinding
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
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

        val svc = Intent(this, OverlayWebViewService::class.java)
        svc.putExtra(OverlayWebViewService.EXTRA_URL, tradingUrl)
        ContextCompat.startForegroundService(this, svc)

        setupBackPressedHandler()
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
}