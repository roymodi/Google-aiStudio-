package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.network.NetworkHelper
import com.example.permission.PermissionManager
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ActionLogCard
import com.example.ui.components.FirstLaunchPermissionDialog
import com.example.ui.components.HeroConnectionBadge
import com.example.ui.components.MonitoringControlCard
import com.example.ui.components.PermissionsCard
import com.example.ui.components.QuickActionsCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainScreen(
                    activity = this,
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    activity: ComponentActivity,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Multi-permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, _) ->
            PermissionManager.markPermissionRequested(context, perm)
        }
        viewModel.refreshPermissions(context, activity)
        if (uiState.settings.isMonitoringActive && PermissionManager.areAllCriticalPermissionsGranted(context)) {
            viewModel.toggleMonitoring(context, true)
        }
    }

    // On-Launch & Foreground Verification: Refresh permissions every time app is resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(context, activity)
                viewModel.triggerNetworkRefresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // First Launch Popup Trigger: Auto-request or show dialog on first launch
    LaunchedEffect(uiState.showFirstLaunchPopup) {
        if (uiState.showFirstLaunchPopup) {
            val missing = PermissionManager.getMissingPermissions(context)
            if (missing.isNotEmpty()) {
                // First launch popup dialog is visible
            } else {
                viewModel.onFirstLaunchHandled(context)
            }
        }
    }

    // Snackbar handling
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // First Launch Permission Dialog
    if (uiState.showFirstLaunchPopup) {
        FirstLaunchPermissionDialog(
            onGrantAllClicked = {
                viewModel.onFirstLaunchHandled(context)
                val missing = PermissionManager.getMissingPermissions(context)
                if (missing.isNotEmpty()) {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            },
            onDismiss = {
                viewModel.onFirstLaunchHandled(context)
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "5G Guard",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "5G Guard",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Active Mobile Data Guardian",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Real-Time Hero Connection Badge
            item {
                HeroConnectionBadge(
                    status = uiState.networkStatus,
                    onRefreshScan = { viewModel.triggerNetworkRefresh(context) }
                )
            }

            // 2. Permissions Status Card (With Missing Permissions Guidance Banner)
            item {
                PermissionsCard(
                    permissions = uiState.permissions,
                    onRequestMissingPermissions = {
                        val missing = PermissionManager.getMissingPermissions(context)
                        if (missing.isNotEmpty()) {
                            permissionLauncher.launch(missing.toTypedArray())
                        }
                    },
                    onOpenAppSettings = {
                        PermissionManager.openAppSettings(context)
                    }
                )
            }

            // 3. Master Monitoring & Alert Rules Toggle Card
            item {
                MonitoringControlCard(
                    settings = uiState.settings,
                    onToggleMaster = { enabled ->
                        viewModel.toggleMonitoring(context, enabled)
                    },
                    onUpdateSettings = { newSettings ->
                        viewModel.updateSettings(newSettings)
                    }
                )
            }

            // 4. Quick System Network Controls
            item {
                QuickActionsCard(
                    onOpenCellularSettings = {
                        NetworkHelper.openCellularSettings(context)
                    },
                    onOpenOperatorSettings = {
                        NetworkHelper.openNetworkOperatorSettings(context)
                    }
                )
            }

            // 5. Action Log Card (with live events and testing simulation)
            item {
                ActionLogCard(
                    logs = uiState.logs,
                    onClearLogs = { viewModel.clearLogs() },
                    onSimulate5G = { viewModel.simulate5GConnection() },
                    onSimulate4GDrop = { viewModel.simulate4GDrop(context) }
                )
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}
