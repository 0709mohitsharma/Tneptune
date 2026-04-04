package neptune.platform.trading

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.OrientationEventListener
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import neptune.platform.trading.databinding.ActivityMainBinding
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.IntentSender
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orientationListener: OrientationEventListener
    private val TAG = "TradingWebView"
    private lateinit var tradingUrl: String
    
    private lateinit var prefs: SharedPreferences
    
    enum class PermissionState {
        PENDING_OVERLAY,
        PENDING_STORAGE,
        PENDING_NOTIFICATION,
        PENDING_BATTERY,
        ALL_GRANTED
    }
    
    private var currentPermissionState = PermissionState.PENDING_OVERLAY

    companion object {
        private const val PREFS_NAME = "peter_browser_prefs"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val REQUEST_STORAGE_PERMISSION = 1005
        private const val REQUEST_NOTIFICATION_PERMISSION = 1006
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkAndRequestPermissions()
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasBatteryOptimizationExemption(): Boolean {
        return try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Battery check failed: ${e.message}")
            true
        }
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Checking all permissions...")
        
        if (!hasOverlayPermission()) {
            Log.d(TAG, "Missing overlay permission")
            currentPermissionState = PermissionState.PENDING_OVERLAY
            requestOverlayPermission()
            return
        }
        
        if (!hasStoragePermission()) {
            Log.d(TAG, "Missing storage permission")
            currentPermissionState = PermissionState.PENDING_STORAGE
            requestStoragePermission()
            return
        }
        
        if (!hasNotificationPermission()) {
            Log.d(TAG, "Missing notification permission")
            currentPermissionState = PermissionState.PENDING_NOTIFICATION
            requestNotificationPermission()
            return
        }
        
        if (!hasBatteryOptimizationExemption()) {
            Log.d(TAG, "Missing battery optimization exemption")
            currentPermissionState = PermissionState.PENDING_BATTERY
            requestBatteryOptimizationExemption()
            return
        }
        
        Log.d(TAG, "All permissions granted!")
        currentPermissionState = PermissionState.ALL_GRANTED
        onAllPermissionsGranted()
    }

    private fun requestOverlayPermission() {
        Log.d(TAG, "Requesting overlay permission...")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestStoragePermission() {
        Log.d(TAG, "Requesting storage permission...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: IntentSender.SendIntentException) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        Log.d(TAG, "Requesting battery optimization exemption...")
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Battery optimization request failed: ${e.message}")
            showToast("Please disable battery optimization manually for reliable background operation")
        }
    }

    private fun requestNotificationPermission() {
        Log.d(TAG, "Requesting notification permission...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    private fun onAllPermissionsGranted() {
        Log.d(TAG, "All permissions granted, starting trading view...")
        tradingUrl = ConfigManager.getTradingUrl(this)
        Log.d(TAG, "Trading URL: $tradingUrl")
        
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

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showToast("Storage permission granted")
                }
            }
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showToast("Notification permission granted")
                } else {
                    showToast("Notification permission denied - notifications may not work")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        if (currentPermissionState != PermissionState.ALL_GRANTED) {
            checkAndRequestPermissions()
        } else {
            Log.d(TAG, "Setting foreground mode")
            val i = Intent(this, OverlayWebViewService::class.java)
            i.action = OverlayWebViewService.ACTION_SET_MODE
            i.putExtra(OverlayWebViewService.EXTRA_MODE, OverlayWebViewService.MODE_FOREGROUND)
            i.putExtra(OverlayWebViewService.EXTRA_ORIENTATION, resources.configuration.orientation)
            startService(i)
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentPermissionState == PermissionState.ALL_GRANTED) {
            Log.d(TAG, "Setting background mode")
            val i = Intent(this, OverlayWebViewService::class.java)
            i.action = OverlayWebViewService.ACTION_SET_MODE
            i.putExtra(OverlayWebViewService.EXTRA_MODE, OverlayWebViewService.MODE_BACKGROUND)
            i.putExtra(OverlayWebViewService.EXTRA_ORIENTATION, resources.configuration.orientation)
            startService(i)
        }
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed - sending GO_BACK intent")
                val backIntent = Intent(this@MainActivity, OverlayWebViewService::class.java).apply {
                    action = OverlayWebViewService.ACTION_GO_BACK
                }
                try {
                    startService(backIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service: ${e.message}", e)
                }
                moveTaskToBack(true)
            }
        }
        onBackPressedDispatcher.addCallback(callback)
    }

    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val decorView = window.decorView
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        }
        orientationListener.enable()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::orientationListener.isInitialized) {
            orientationListener.disable()
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}