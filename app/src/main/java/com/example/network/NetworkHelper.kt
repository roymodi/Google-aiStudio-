package com.example.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.model.NetworkStatusInfo
import com.example.model.NetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class NetworkHelper(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _currentStatus = MutableStateFlow(NetworkStatusInfo())
    val currentStatus: StateFlow<NetworkStatusInfo> = _currentStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isObserving = false

    // Cache of latest telephony display info
    private var lastDisplayInfoOverride: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    private var lastDisplayInfoNetworkType: Int = TelephonyManager.NETWORK_TYPE_UNKNOWN

    init {
        // Initial snapshot
        refreshNetworkSnapshot()
    }

    /**
     * Starts continuous real-time listeners for Telephony and Connectivity.
     */
    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (isObserving) return
        isObserving = true

        registerConnectivityCallback()
        registerTelephonyListeners()
        refreshNetworkSnapshot()
    }

    /**
     * Stops active network listeners.
     */
    fun stopMonitoring() {
        if (!isObserving) return
        isObserving = false

        unregisterConnectivityCallback()
        unregisterTelephonyListeners()
    }

    /**
     * Polls / refreshes the current network status immediately.
     */
    fun refreshNetworkSnapshot() {
        scope.launch {
            val status = computeCurrentStatus()
            _currentStatus.value = status
        }
    }

    private fun registerConnectivityCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refreshNetworkSnapshot()
                }

                override fun onLost(network: Network) {
                    refreshNetworkSnapshot()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    refreshNetworkSnapshot()
                }
            }

            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Exception) {
        }
    }

    private fun unregisterConnectivityCallback() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
            networkCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerTelephonyListeners() {
        val hasPhonePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhonePermission) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val executor: Executor = ContextCompat.getMainExecutor(context)
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        lastDisplayInfoOverride = telephonyDisplayInfo.overrideNetworkType
                        lastDisplayInfoNetworkType = telephonyDisplayInfo.networkType
                        refreshNetworkSnapshot()
                    }
                }
                telephonyManager.registerTelephonyCallback(executor, callback)
                telephonyCallback = callback
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        lastDisplayInfoOverride = telephonyDisplayInfo.overrideNetworkType
                        lastDisplayInfoNetworkType = telephonyDisplayInfo.networkType
                        refreshNetworkSnapshot()
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
                phoneStateListener = listener
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun unregisterTelephonyListeners() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager.unregisterTelephonyCallback(it)
                }
                telephonyCallback = null
            } else {
                phoneStateListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun computeCurrentStatus(): NetworkStatusInfo {
        val activeNet = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNet)

        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true

        // Carrier details
        val carrier = telephonyManager.networkOperatorName.takeIf { it.isNotBlank() }
            ?: telephonyManager.simOperatorName.takeIf { it.isNotBlank() }
            ?: "Cellular Provider"

        val isRoaming = telephonyManager.isNetworkRoaming

        var rawOverrideStr = "NONE"
        var rawNetTypeStr = "UNKNOWN"
        var networkType = NetworkType.DISCONNECTED

        if (caps != null && hasInternet) {
            val hasPhonePerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            val dataNetworkType = if (hasPhonePerm) {
                try {
                    telephonyManager.dataNetworkType
                } catch (_: SecurityException) {
                    TelephonyManager.NETWORK_TYPE_UNKNOWN
                }
            } else {
                TelephonyManager.NETWORK_TYPE_UNKNOWN
            }

            rawNetTypeStr = getNetworkTypeName(dataNetworkType)
            rawOverrideStr = getOverrideTypeName(lastDisplayInfoOverride)

            if (isWifi && !isCellular) {
                networkType = NetworkType.WIFI_ACTIVE
            } else {
                // Determine precision 5G vs 4G
                networkType = evaluate5GType(
                    overrideType = lastDisplayInfoOverride,
                    dataNetworkType = dataNetworkType,
                    displayInfoNetType = lastDisplayInfoNetworkType
                )
            }
        } else {
            networkType = NetworkType.DISCONNECTED
        }

        return NetworkStatusInfo(
            networkType = networkType,
            carrierName = carrier,
            is5G = networkType.is5G,
            isCellular = isCellular || networkType.isCellular,
            isWifi = isWifi,
            isMetered = isMetered,
            isRoaming = isRoaming,
            rawOverrideType = rawOverrideStr,
            rawNetworkType = rawNetTypeStr,
            signalLevel = 3,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * High precision evaluation of 5G Standalone, Non-Standalone, 5G+ mmWave, or 4G.
     */
    private fun evaluate5GType(
        overrideType: Int,
        dataNetworkType: Int,
        displayInfoNetType: Int
    ): NetworkType {
        // 1. Check for 5G+ / Ultra Wideband mmWave
        if (overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED) {
            return NetworkType.FIVE_G_PLUS
        }

        // 2. Check for 5G NSA (Non-Standalone)
        if (overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA) {
            return NetworkType.FIVE_G_NSA
        }

        // 3. Check for 5G SA (Standalone NR)
        if (dataNetworkType == TelephonyManager.NETWORK_TYPE_NR ||
            displayInfoNetType == TelephonyManager.NETWORK_TYPE_NR
        ) {
            return NetworkType.FIVE_G_SA
        }

        // 4. Check for 4G LTE
        if (dataNetworkType == TelephonyManager.NETWORK_TYPE_LTE ||
            overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO ||
            overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA
        ) {
            return NetworkType.FOUR_G_LTE
        }

        // 5. Check for legacy 3G/2G
        if (dataNetworkType in listOf(
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS
            )
        ) {
            return NetworkType.THREE_G_TWO_G
        }

        // If data network is unknown or cellular, default to 4G LTE fallback if on cellular
        return NetworkType.FOUR_G_LTE
    }

    private fun getOverrideTypeName(override: Int): String {
        return when (override) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "NR_ADVANCED (5G+)"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "NR_NSA (5G Sub-6)"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE_ADVANCED_PRO"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE_CA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE -> "NONE"
            else -> "OVERRIDE_$override"
        }
    }

    private fun getNetworkTypeName(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "NET_TYPE_$type"
        }
    }

    /**
     * Triggers tactile feedback when 5G drops.
     */
    fun triggerDropVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 400), -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 400), -1)
                vibrator?.vibrate(effect)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        /**
         * Quick intent to Cellular / Mobile Network settings.
         */
        fun openCellularSettings(context: Context) {
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }.onFailure {
                // Fallback to wireless settings if data roaming not available
                val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                runCatching { context.startActivity(fallbackIntent) }
            }
        }

        /**
         * Quick intent to Network Operator Settings.
         */
        fun openNetworkOperatorSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }.onFailure {
                openCellularSettings(context)
            }
        }
    }
}
