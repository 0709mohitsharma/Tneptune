package neptune.platform.trading

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ConfigManager {
    private const val TAG = "ConfigManager"
    private const val FOLDER_NAME = "Tneptune"
    private const val FILE_NAME = "config.txt"
    private const val KEY_TRADING_URL = "trading_url"
    private const val DEFAULT_URL = "https://tv-beta.dhan.co/"
    private const val CONFIG_SEPARATOR = "="

    private var cachedUrl: String? = null

    fun getTradingUrl(context: Context): String {
        cachedUrl?.let { return it }

        val file = getConfigFile()
        
        Log.d(TAG, "Config file path: ${file.absolutePath}")
        Log.d(TAG, "Config file exists: ${file.exists()}")

        if (!file.exists()) {
            Log.d(TAG, "Config file not found, creating default")
            createDefaultConfig(file)
            cachedUrl = DEFAULT_URL
            return DEFAULT_URL
        }

        return try {
            val content = file.readText()
            Log.d(TAG, "Config file content:\n$content")
            
            content.lineSequence()
                .filter { it.trim().startsWith(KEY_TRADING_URL) }
                .map { it.substringAfter(CONFIG_SEPARATOR).trim() }
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.also { Log.d(TAG, "Found URL in config: $it") }
                ?: DEFAULT_URL.also { Log.d(TAG, "No URL found, using default") }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading config: ${e.message}")
            DEFAULT_URL
        }.also {
            cachedUrl = it
        }
    }

    fun saveTradingUrl(context: Context, url: String): Boolean {
        val file = getConfigFile()
        
        return try {
            val content = buildString {
                append("# PeterBrowser Configuration\n")
                append("# Edit the trading_url below to change the trading platform\n")
                append("#\n")
                append("$KEY_TRADING_URL$CONFIG_SEPARATOR$url\n")
            }
            
            file.parentFile?.mkdirs()
            file.writeText(content)
            
            cachedUrl = url
            Log.d(TAG, "Saved trading URL: $url")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving config: ${e.message}")
            false
        }
    }

    fun getConfigFilePath(): String {
        return getConfigFile().absolutePath
    }

    private fun getConfigFile(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appFolder = File(downloadsDir, FOLDER_NAME)
        return File(appFolder, FILE_NAME)
    }

    private fun createDefaultConfig(file: File) {
        try {
            file.parentFile?.mkdirs()
            val content = buildString {
                append("# PeterBrowser Configuration\n")
                append("# Edit the trading_url below to change the trading platform\n")
                append("# File location: ${file.absolutePath}\n")
                append("#\n")
                append("$KEY_TRADING_URL$CONFIG_SEPARATOR$DEFAULT_URL\n")
            }
            file.writeText(content)
            Log.d(TAG, "Created default config at: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating default config: ${e.message}")
        }
    }

    fun clearCache() {
        cachedUrl = null
    }
}
