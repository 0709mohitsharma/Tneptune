package neptune.platform.trading

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

object ConfigManager {
    private const val TAG = "ConfigManager"
    private const val CONFIG_FOLDER = "Tneptune/config"
    private const val FILE_NAME = "config.txt"
    private const val KEY_TRADING_URL = "trading_url"
    private const val KEY_NOTIFICATION_TITLE = "notification_title"
    private const val KEY_NOTIFICATION_MESSAGE = "notification_message"
    private const val DEFAULT_URL = "https://tv-beta.dhan.co/"
    private const val DEFAULT_NOTIFICATION_TITLE = "Customizable Affirmation"
    private const val DEFAULT_NOTIFICATION_MESSAGE = "Respect the market and it will respect you..."
    private const val CONFIG_SEPARATOR = "="

    private val ALL_KEYS = listOf(
        KEY_TRADING_URL to DEFAULT_URL,
        KEY_NOTIFICATION_TITLE to DEFAULT_NOTIFICATION_TITLE,
        KEY_NOTIFICATION_MESSAGE to DEFAULT_NOTIFICATION_MESSAGE
    )

    private fun getConfigFile(): File {
        val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Environment.getExternalStorageDirectory()
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).parentFile ?: Environment.getExternalStorageDirectory()
        }
        return File(baseDir, "$CONFIG_FOLDER/$FILE_NAME")
    }

    private fun ensureConfigFileIsComplete(file: File) {
        if (!file.exists()) {
            createDefaultConfig(file)
            return
        }

        try {
            val existingContent = file.readText()
            val existingKeys = mutableSetOf<String>()
            
            existingContent.lineSequence()
                .filter { it.contains(CONFIG_SEPARATOR) }
                .map { it.substringBefore(CONFIG_SEPARATOR).trim() }
                .forEach { key -> existingKeys.add(key) }

            val missingKeys = ALL_KEYS.filter { (key, _) -> key !in existingKeys }
            
            if (missingKeys.isNotEmpty()) {
                Log.d(TAG, "Found missing keys: ${missingKeys.map { it.first }}")
                
                val updatedContent = buildString {
                    append(existingContent)
                    if (!existingContent.endsWith("\n")) {
                        append("\n")
                    }
                    missingKeys.forEach { (key, defaultValue) ->
                        append("$key$CONFIG_SEPARATOR$defaultValue\n")
                    }
                }
                
                file.writeText(updatedContent)
                Log.d(TAG, "Added missing keys to config file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring config completeness: ${e.message}")
        }
    }

    private fun getConfigValue(file: File, key: String, defaultValue: String): String {
        if (!file.exists()) {
            return defaultValue
        }

        return try {
            val content = file.readText()
            content.lineSequence()
                .filter { it.trim().startsWith(key) }
                .map { it.substringAfter(CONFIG_SEPARATOR).trim() }
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: defaultValue
        } catch (e: Exception) {
            Log.e(TAG, "Error reading config key $key: ${e.message}")
            defaultValue
        }
    }

    fun getTradingUrl(context: Context): String {
        val file = getConfigFile()
        Log.d(TAG, "Config file path: ${file.absolutePath}")
        Log.d(TAG, "Config file exists: ${file.exists()}")

        ensureConfigFileIsComplete(file)
        
        val value = getConfigValue(file, KEY_TRADING_URL, DEFAULT_URL)
        Log.d(TAG, "Trading URL: $value")
        
        return value
    }

    fun getNotificationTitle(context: Context): String {
        val file = getConfigFile()
        ensureConfigFileIsComplete(file)

        return getConfigValue(file, KEY_NOTIFICATION_TITLE, DEFAULT_NOTIFICATION_TITLE)
    }

    fun getNotificationMessage(context: Context): String {
        val file = getConfigFile()
        ensureConfigFileIsComplete(file)

        return getConfigValue(file, KEY_NOTIFICATION_MESSAGE, DEFAULT_NOTIFICATION_MESSAGE)
    }

    fun saveTradingUrl(context: Context, url: String): Boolean {
        val file = getConfigFile()
        
        return try {
            if (!file.exists()) {
                createDefaultConfig(file)
            } else {
                val content = file.readText()
                val updatedContent = if (content.contains(KEY_TRADING_URL)) {
                    content.lineSequence()
                        .map { line ->
                            if (line.trim().startsWith(KEY_TRADING_URL)) {
                                "$KEY_TRADING_URL$CONFIG_SEPARATOR$url"
                            } else {
                                line
                            }
                        }
                        .joinToString("\n")
                } else {
                    val separator = if (content.endsWith("\n")) "" else "\n"
                    "$content$separator$KEY_TRADING_URL$CONFIG_SEPARATOR$url\n"
                }
                file.writeText(updatedContent)
            }
            
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

    private fun createDefaultConfig(file: File) {
        try {
            val parent = file.parentFile ?: run {
                Log.w(TAG, "No parent directory for config file. Using defaults.")
                return
            }
            parent.mkdirs()
            if (!parent.canWrite()) {
                Log.w(TAG, "Cannot write to config directory. Using default values.")
                return
            }
            val content = buildString {
                append("# PeterBrowser Configuration\n")
                append("# Edit the trading_url below to change the trading platform\n")
                append("# Edit notification_title to customize the notification title\n")
                append("# Edit notification_message to customize the notification message\n")
                append("# File location: ${file.absolutePath}\n")
                append("#\n")
                append("$KEY_TRADING_URL$CONFIG_SEPARATOR$DEFAULT_URL\n")
                append("$KEY_NOTIFICATION_TITLE$CONFIG_SEPARATOR$DEFAULT_NOTIFICATION_TITLE\n")
                append("$KEY_NOTIFICATION_MESSAGE$CONFIG_SEPARATOR$DEFAULT_NOTIFICATION_MESSAGE\n")
            }
            file.writeText(content)
            Log.d(TAG, "Created default config at: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Error creating default config: ${e.message}. Using defaults.")
        }
    }
}
