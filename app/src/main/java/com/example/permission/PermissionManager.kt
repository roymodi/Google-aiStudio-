package com.example.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.model.PermissionItem

object PermissionManager {

    private const val PREFS_NAME = "five_g_guard_prefs"
    private const val KEY_FIRST_LAUNCH_COMPLETED = "first_launch_completed"
    private const val KEY_REQUESTED_ONCE_PREFIX = "perm_requested_"

    /**
     * List of runtime permissions required for 5G monitoring.
     */
    fun getRequiredRuntimePermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    /**
     * Checks if this is the very first time the app is launched.
     */
    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
    }

    /**
     * Marks the first launch as completed.
     */
    fun markFirstLaunchCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_COMPLETED, true).apply()
    }

    /**
     * Records that permission request was triggered at least once.
     */
    fun markPermissionRequested(context: Context, permission: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_REQUESTED_ONCE_PREFIX + permission, true).apply()
    }

    /**
     * Checks full status of all relevant permissions.
     */
    fun checkPermissions(context: Context, activity: Activity? = null): List<PermissionItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val items = mutableListOf<PermissionItem>()

        // 1. Phone State (Required for TelephonyManager 5G NR / cell details)
        val phoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val phonePermanentlyDenied = if (!phoneGranted && activity != null) {
            val wasRequested = prefs.getBoolean(KEY_REQUESTED_ONCE_PREFIX + Manifest.permission.READ_PHONE_STATE, false)
            wasRequested && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_PHONE_STATE)
        } else false

        items.add(
            PermissionItem(
                permission = Manifest.permission.READ_PHONE_STATE,
                title = "Phone & Carrier State",
                description = "Required to inspect cellular carrier data, NR (New Radio 5G) bands, and network override types.",
                isGranted = phoneGranted,
                isRequired = true,
                isPermanentlyDenied = phonePermanentlyDenied
            )
        )

        // 2. Fine Location (Android 10+ requires Location for CellInfo and TelephonyDisplayInfo)
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val locationPermanentlyDenied = if (!locationGranted && activity != null) {
            val wasRequested = prefs.getBoolean(KEY_REQUESTED_ONCE_PREFIX + Manifest.permission.ACCESS_FINE_LOCATION, false)
            wasRequested && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } else false

        items.add(
            PermissionItem(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                title = "Cellular Location Telemetry",
                description = "Mandated by Android OS to query active cell tower identifiers and verify 5G carrier bands.",
                isGranted = locationGranted,
                isRequired = true,
                isPermanentlyDenied = locationPermanentlyDenied
            )
        )

        // 3. Network State (Normal permission, auto-granted at install time)
        val networkGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED

        items.add(
            PermissionItem(
                permission = Manifest.permission.ACCESS_NETWORK_STATE,
                title = "Network Connectivity State",
                description = "Allows observing real-time network switches between Wi-Fi and Cellular.",
                isGranted = networkGranted,
                isRequired = true,
                isPermanentlyDenied = false
            )
        )

        // 4. Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            val notifPermanentlyDenied = if (!notifGranted && activity != null) {
                val wasRequested = prefs.getBoolean(KEY_REQUESTED_ONCE_PREFIX + Manifest.permission.POST_NOTIFICATIONS, false)
                wasRequested && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            } else false

            items.add(
                PermissionItem(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    title = "Foreground Service & Alerts",
                    description = "Enables persistent status notifications and instant sound/vibration alerts when dropping to 4G.",
                    isGranted = notifGranted,
                    isRequired = true,
                    isPermanentlyDenied = notifPermanentlyDenied
                )
            )
        }

        return items
    }

    /**
     * Returns true if all mandatory permissions are granted.
     */
    fun areAllCriticalPermissionsGranted(context: Context): Boolean {
        val permissions = getRequiredRuntimePermissions()
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns list of missing runtime permissions.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredRuntimePermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Navigates directly to the Application Details Settings page.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
