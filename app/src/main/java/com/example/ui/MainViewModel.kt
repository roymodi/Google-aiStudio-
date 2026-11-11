package com.example.ui

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GuardRepository
import com.example.model.GuardSettings
import com.example.model.NetworkLogEvent
import com.example.model.NetworkStatusInfo
import com.example.model.NetworkType
import com.example.model.PermissionItem
import com.example.network.NetworkHelper
import com.example.permission.PermissionManager
import com.example.service.NetworkMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val networkStatus: NetworkStatusInfo = NetworkStatusInfo(),
    val permissions: List<PermissionItem> = emptyList(),
    val isAllPermissionsGranted: Boolean = false,
    val hasMissingPermissions: Boolean = false,
    val missingPermissions: List<PermissionItem> = emptyList(),
    val showFirstLaunchPopup: Boolean = false,
    val settings: GuardSettings = GuardSettings(),
    val logs: List<NetworkLogEvent> = emptyList(),
    val snackbarMessage: String? = null,
    val isSimulationActive: Boolean = false
)

class MainViewModel(context: Context) : ViewModel() {

    private val repository = GuardRepository.getInstance(context)
    private val networkHelper = NetworkHelper(context)

    private val _permissionsState = MutableStateFlow<List<PermissionItem>>(emptyList())
    private val _showFirstLaunchPopup = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _isSimulationActive = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Check first launch
        val isFirst = PermissionManager.isFirstLaunch(context)
        _showFirstLaunchPopup.value = isFirst

        // Initial permissions check
        refreshPermissions(context)

        // Reactive UI state updates
        viewModelScope.launch { repository.networkStatus.collect { recomputeUiState() } }
        viewModelScope.launch { _permissionsState.collect { recomputeUiState() } }
        viewModelScope.launch { _showFirstLaunchPopup.collect { recomputeUiState() } }
        viewModelScope.launch { repository.settings.collect { recomputeUiState() } }
        viewModelScope.launch { repository.logs.collect { recomputeUiState() } }
        viewModelScope.launch { _snackbarMessage.collect { recomputeUiState() } }
        viewModelScope.launch { _isSimulationActive.collect { recomputeUiState() } }

        // Start local network helper listeners
        networkHelper.startMonitoring()
        viewModelScope.launch {
            networkHelper.currentStatus.collect { status ->
                if (!_isSimulationActive.value) {
                    repository.updateNetworkStatus(status)
                }
            }
        }

        // If monitoring is active in settings, ensure service is started
        if (repository.settings.value.isMonitoringActive && PermissionManager.areAllCriticalPermissionsGranted(context)) {
            NetworkMonitorService.start(context)
        }
    }

    private fun recomputeUiState() {
        val perms = _permissionsState.value
        val missing = perms.filter { !it.isGranted }
        val allGranted = perms.isNotEmpty() && missing.isEmpty()
        _uiState.value = MainUiState(
            networkStatus = repository.networkStatus.value,
            permissions = perms,
            isAllPermissionsGranted = allGranted,
            hasMissingPermissions = missing.isNotEmpty(),
            missingPermissions = missing,
            showFirstLaunchPopup = _showFirstLaunchPopup.value,
            settings = repository.settings.value,
            logs = repository.logs.value,
            snackbarMessage = _snackbarMessage.value,
            isSimulationActive = _isSimulationActive.value
        )
    }

    fun refreshPermissions(context: Context, activity: Activity? = null) {
        val list = PermissionManager.checkPermissions(context, activity)
        _permissionsState.value = list
    }

    fun onFirstLaunchHandled(context: Context) {
        PermissionManager.markFirstLaunchCompleted(context)
        _showFirstLaunchPopup.value = false
    }

    fun toggleMonitoring(context: Context, enabled: Boolean) {
        val updated = repository.settings.value.copy(isMonitoringActive = enabled)
        repository.updateSettings(updated)

        if (enabled) {
            if (PermissionManager.areAllCriticalPermissionsGranted(context)) {
                NetworkMonitorService.start(context)
                _snackbarMessage.value = "5G Guard background monitoring started"
            } else {
                _snackbarMessage.value = "Please grant required permissions to start background service"
            }
        } else {
            NetworkMonitorService.stop(context)
            _snackbarMessage.value = "5G Guard monitoring stopped"
        }
    }

    fun updateSettings(newSettings: GuardSettings) {
        repository.updateSettings(newSettings)
    }

    fun clearLogs() {
        repository.clearLogs()
        _snackbarMessage.value = "Action logs cleared"
    }

    fun triggerNetworkRefresh(context: Context) {
        _isSimulationActive.value = false
        networkHelper.refreshNetworkSnapshot()
        _snackbarMessage.value = "Scanning current network..."
    }

    /**
     * Testing / simulation helper so users and evaluators can experience 5G connection and drop alerts.
     */
    fun simulate5GConnection() {
        _isSimulationActive.value = true
        val simulated5G = NetworkStatusInfo(
            networkType = NetworkType.FIVE_G_SA,
            carrierName = "5G Carrier (Ultra Band)",
            is5G = true,
            isCellular = true,
            isWifi = false,
            isMetered = true,
            isRoaming = false,
            rawOverrideType = "NR_ADVANCED (5G+)",
            rawNetworkType = "NR (5G)",
            signalLevel = 4
        )
        repository.updateNetworkStatus(simulated5G)
        _snackbarMessage.value = "Simulation: Connected to 5G SA (Ultra Band)"
    }

    fun simulate4GDrop(context: Context) {
        _isSimulationActive.value = true
        val simulated4G = NetworkStatusInfo(
            networkType = NetworkType.FOUR_G_LTE,
            carrierName = "Cellular Provider",
            is5G = false,
            isCellular = true,
            isWifi = false,
            isMetered = true,
            isRoaming = false,
            rawOverrideType = "LTE_CA",
            rawNetworkType = "LTE (4G)",
            signalLevel = 2
        )
        repository.updateNetworkStatus(simulated4G)

        if (repository.settings.value.vibrateOnDrop) {
            networkHelper.triggerDropVibration()
        }

        _snackbarMessage.value = "⚠️ Simulated Drop to 4G LTE! Alert triggered."
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        networkHelper.stopMonitoring()
    }
}
