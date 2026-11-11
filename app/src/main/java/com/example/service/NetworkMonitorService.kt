package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.GuardRepository
import com.example.model.LogEventType
import com.example.model.NetworkLogEvent
import com.example.model.NetworkStatusInfo
import com.example.model.NetworkType
import com.example.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NetworkMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var networkHelper: NetworkHelper
    private lateinit var repository: GuardRepository
    private lateinit var notificationManager: NotificationManager

    private var previousType: NetworkType? = null
    private var isFirstStatus = true

    override fun onCreate() {
        super.onCreate()
        repository = GuardRepository.getInstance(applicationContext)
        networkHelper = NetworkHelper(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()
        startForegroundServiceNotification(
            title = "5G Guard Active",
            message = "Continuous background network monitoring running..."
        )

        networkHelper.startMonitoring()
        observeNetworkChanges()

        repository.addLog(
            NetworkLogEvent(
                type = LogEventType.SERVICE_STARTED,
                title = "Guard Service Active",
                description = "Continuous background 5G monitoring service initiated."
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopMonitoringService()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun observeNetworkChanges() {
        serviceScope.launch {
            networkHelper.currentStatus.collectLatest { status ->
                repository.updateNetworkStatus(status)
                handleNetworkEvaluation(status)
            }
        }
    }

    private fun handleNetworkEvaluation(status: NetworkStatusInfo) {
        val currentType = status.networkType

        if (isFirstStatus) {
            previousType = currentType
            isFirstStatus = false
            updateForegroundNotification(status)
            return
        }

        val settings = repository.settings.value

        // Check if connection degraded from 5G to 4G or lower
        if (previousType?.is5G == true && !currentType.is5G && currentType.isCellular) {
            // Drop detected!
            if (settings.alertOn4GDrop) {
                dispatchDropAlertNotification(currentType, status.carrierName)
            }
            if (settings.vibrateOnDrop) {
                networkHelper.triggerDropVibration()
            }
        }

        previousType = currentType
        updateForegroundNotification(status)
    }

    private fun startForegroundServiceNotification(title: String, message: String) {
        val notification = buildOngoingNotification(title, message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID_FOREGROUND, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun updateForegroundNotification(status: NetworkStatusInfo) {
        val title = if (status.networkType.is5G) {
            "5G Guard: 5G Secured (${status.networkType.label})"
        } else if (status.networkType == NetworkType.WIFI_ACTIVE) {
            "5G Guard: Wi-Fi Connected"
        } else if (status.networkType == NetworkType.FOUR_G_LTE) {
            "5G Guard: On 4G LTE (${status.carrierName})"
        } else {
            "5G Guard: ${status.networkType.label}"
        }

        val details = when {
            status.networkType.is5G -> "Carrier: ${status.carrierName} • 5G Lock verified"
            status.networkType == NetworkType.FOUR_G_LTE -> "⚠️ Warning: Not on 5G • Carrier: ${status.carrierName}"
            status.networkType == NetworkType.WIFI_ACTIVE -> "Active Data: Wi-Fi • Cellular standby"
            else -> "Status: ${status.networkType.category}"
        }

        val notification = buildOngoingNotification(title, details)
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun buildOngoingNotification(title: String, message: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_MONITOR_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Guard", stopPendingIntent)
            .build()
    }

    private fun dispatchDropAlertNotification(droppedTo: NetworkType, carrier: String) {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertNotif = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle("⚠️ Network Alert: Dropped from 5G to ${droppedTo.label}")
            .setContentText("Connection degraded on $carrier. Tap to inspect or switch network.")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT, alertNotif)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel 1: Ongoing monitor
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                "5G Network Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays real-time 5G connection status in background"
                setShowBadge(false)
            }

            // Channel 2: High priority drop alert
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "5G Network Drop Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts instantly when mobile data drops from 5G to 4G LTE or lower"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(monitorChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun stopMonitoringService() {
        networkHelper.stopMonitoring()
        repository.addLog(
            NetworkLogEvent(
                type = LogEventType.SERVICE_STOPPED,
                title = "Guard Service Stopped",
                description = "Background monitoring paused by user."
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkHelper.stopMonitoring()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_MONITOR_ID = "five_g_guard_monitor_channel"
        const val CHANNEL_ALERT_ID = "five_g_guard_alert_channel"
        const val NOTIFICATION_ID_FOREGROUND = 5001
        const val NOTIFICATION_ID_ALERT = 5002
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
