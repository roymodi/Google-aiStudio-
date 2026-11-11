package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.GuardSettings
import com.example.model.LogEventType
import com.example.model.NetworkLogEvent
import com.example.model.NetworkStatusInfo
import com.example.model.NetworkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class GuardRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _networkStatus = MutableStateFlow(NetworkStatusInfo())
    val networkStatus: StateFlow<NetworkStatusInfo> = _networkStatus.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<GuardSettings> = _settings.asStateFlow()

    private val _logs = MutableStateFlow(loadLogs())
    val logs: StateFlow<List<NetworkLogEvent>> = _logs.asStateFlow()

    fun updateNetworkStatus(status: NetworkStatusInfo) {
        val previous = _networkStatus.value
        _networkStatus.value = status

        // Detect connection changes and log them
        if (previous.networkType != NetworkType.UNKNOWN && previous.networkType != status.networkType) {
            when {
                status.networkType.is5G && !previous.networkType.is5G -> {
                    addLog(
                        NetworkLogEvent(
                            type = LogEventType.CONNECTED_5G,
                            title = "5G Secured (${status.networkType.label})",
                            description = "Connected to high-speed 5G network on carrier ${status.carrierName}.",
                            previousType = previous.networkType,
                            currentType = status.networkType
                        )
                    )
                }
                !status.networkType.is5G && previous.networkType.is5G -> {
                    addLog(
                        NetworkLogEvent(
                            type = LogEventType.DROPPED_TO_4G,
                            title = "⚠️ Dropped from 5G to ${status.networkType.label}",
                            description = "Connection degraded from 5G to ${status.networkType.label}. Guard action triggered.",
                            previousType = previous.networkType,
                            currentType = status.networkType
                        )
                    )
                }
                status.networkType == NetworkType.DISCONNECTED -> {
                    addLog(
                        NetworkLogEvent(
                            type = LogEventType.DISCONNECTED,
                            title = "Data Disconnected",
                            description = "Active mobile data and Wi-Fi connections lost.",
                            previousType = previous.networkType,
                            currentType = status.networkType
                        )
                    )
                }
            }
        }
    }

    fun updateSettings(newSettings: GuardSettings) {
        _settings.value = newSettings
        prefs.edit()
            .putBoolean(KEY_MONITORING_ACTIVE, newSettings.isMonitoringActive)
            .putBoolean(KEY_ALERT_4G, newSettings.alertOn4GDrop)
            .putBoolean(KEY_VIBRATE, newSettings.vibrateOnDrop)
            .putBoolean(KEY_SOUND, newSettings.soundAlertOnDrop)
            .putBoolean(KEY_AUTO_SETTINGS, newSettings.autoOpenSettingsOnDrop)
            .apply()
    }

    fun addLog(event: NetworkLogEvent) {
        val current = _logs.value.toMutableList()
        current.add(0, event) // newest first
        if (current.size > MAX_LOGS) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
        saveLogs(current)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun loadSettings(): GuardSettings {
        return GuardSettings(
            isMonitoringActive = prefs.getBoolean(KEY_MONITORING_ACTIVE, true),
            alertOn4GDrop = prefs.getBoolean(KEY_ALERT_4G, true),
            vibrateOnDrop = prefs.getBoolean(KEY_VIBRATE, true),
            soundAlertOnDrop = prefs.getBoolean(KEY_SOUND, true),
            autoOpenSettingsOnDrop = prefs.getBoolean(KEY_AUTO_SETTINGS, false)
        )
    }

    private fun saveLogs(list: List<NetworkLogEvent>) {
        try {
            val jsonArray = JSONArray()
            list.take(30).forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("timestamp", item.timestamp)
                obj.put("type", item.type.name)
                obj.put("title", item.title)
                obj.put("description", item.description)
                item.previousType?.let { obj.put("prev", it.name) }
                item.currentType?.let { obj.put("curr", it.name) }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_LOGS, jsonArray.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun loadLogs(): List<NetworkLogEvent> {
        val list = mutableListOf<NetworkLogEvent>()
        val raw = prefs.getString(KEY_LOGS, null) ?: return list
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = runCatching { LogEventType.valueOf(obj.getString("type")) }.getOrDefault(LogEventType.CONNECTED_5G)
                val prev = if (obj.has("prev")) runCatching { NetworkType.valueOf(obj.getString("prev")) }.getOrNull() else null
                val curr = if (obj.has("curr")) runCatching { NetworkType.valueOf(obj.getString("curr")) }.getOrNull() else null
                list.add(
                    NetworkLogEvent(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        type = type,
                        title = obj.getString("title"),
                        description = obj.getString("description"),
                        previousType = prev,
                        currentType = curr
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    companion object {
        private const val PREFS_NAME = "guard_repository_prefs"
        private const val KEY_MONITORING_ACTIVE = "monitoring_active"
        private const val KEY_ALERT_4G = "alert_4g"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_SOUND = "sound"
        private const val KEY_AUTO_SETTINGS = "auto_settings"
        private const val KEY_LOGS = "network_logs_json"
        private const val MAX_LOGS = 50

        @Volatile
        private var INSTANCE: GuardRepository? = null

        fun getInstance(context: Context): GuardRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GuardRepository(context).also { INSTANCE = it }
            }
        }
    }
}
