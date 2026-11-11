package com.example.model

/**
 * Representation of cellular and connection types.
 */
enum class NetworkType(val label: String, val category: String) {
    FIVE_G_SA("5G SA", "5G Standalone"),
    FIVE_G_NSA("5G NSA", "5G Non-Standalone"),
    FIVE_G_PLUS("5G+ Ultra", "5G mmWave / Ultra Wideband"),
    FOUR_G_LTE("4G LTE", "4G High-Speed Cellular"),
    THREE_G_TWO_G("3G / 2G", "Legacy Mobile Data"),
    WIFI_ACTIVE("Wi-Fi", "Local Wireless"),
    DISCONNECTED("Disconnected", "No Active Internet"),
    UNKNOWN("Scanning...", "Determining Connection");

    val is5G: Boolean
        get() = this == FIVE_G_SA || this == FIVE_G_NSA || this == FIVE_G_PLUS

    val isCellular: Boolean
        get() = this == FIVE_G_SA || this == FIVE_G_NSA || this == FIVE_G_PLUS ||
                this == FOUR_G_LTE || this == THREE_G_TWO_G
}

/**
 * Snapshot of current network telemetry.
 */
data class NetworkStatusInfo(
    val networkType: NetworkType = NetworkType.UNKNOWN,
    val carrierName: String = "Detecting Carrier...",
    val is5G: Boolean = false,
    val isCellular: Boolean = false,
    val isWifi: Boolean = false,
    val isMetered: Boolean = true,
    val isRoaming: Boolean = false,
    val rawOverrideType: String = "None",
    val rawNetworkType: String = "Unknown",
    val signalStrengthDbm: Int? = null,
    val signalLevel: Int = 3, // 0 to 4
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Log entry for network switches, alerts, and service lifecycle.
 */
data class NetworkLogEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogEventType,
    val title: String,
    val description: String,
    val previousType: NetworkType? = null,
    val currentType: NetworkType? = null
)

enum class LogEventType {
    CONNECTED_5G,
    DROPPED_TO_4G,
    DROPPED_TO_LEGACY,
    DISCONNECTED,
    SERVICE_STARTED,
    SERVICE_STOPPED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    ALERT_TRIGGERED
}

/**
 * Represents each permission item in the checklist.
 */
data class PermissionItem(
    val permission: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isRequired: Boolean = true,
    val isPermanentlyDenied: Boolean = false
)

/**
 * Persistent user settings for 5G Guard.
 */
data class GuardSettings(
    val isMonitoringActive: Boolean = true,
    val alertOn4GDrop: Boolean = true,
    val vibrateOnDrop: Boolean = true,
    val soundAlertOnDrop: Boolean = true,
    val autoOpenSettingsOnDrop: Boolean = false
)
