package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PermissionItem
import com.example.ui.theme.FiveGGreen
import com.example.ui.theme.FiveGGreenContainer
import com.example.ui.theme.FourGAmber
import com.example.ui.theme.FourGAmberContainer
import com.example.ui.theme.DropAlertRed
import com.example.ui.theme.DropAlertRedContainer

@Composable
fun PermissionsCard(
    permissions: List<PermissionItem>,
    onRequestMissingPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val missingPermissions = permissions.filter { !it.isGranted }
    val hasMissing = missingPermissions.isNotEmpty()
    val hasPermanentDenial = missingPermissions.any { it.isPermanentlyDenied }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permissions_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Permissions",
                        tint = if (hasMissing) FourGAmber else FiveGGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "System Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (hasMissing) FourGAmberContainer else FiveGGreenContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (hasMissing) "${missingPermissions.size} MISSING" else "ALL GRANTED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasMissing) FourGAmber else FiveGGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // User Guidance Banner if any permission is missing
            AnimatedVisibility(
                visible = hasMissing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("permissions_required_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = DropAlertRedContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DropAlertRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = DropAlertRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Permissions Required for 5G Guard",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DropAlertRed
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (hasPermanentDenial) {
                                        "One or more permissions were previously denied. Please open App Settings to grant them for continuous 5G protection."
                                    } else {
                                        "Required permissions must be granted to query 5G carrier bands and run continuous background alerts."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (hasPermanentDenial) {
                                OutlinedButton(
                                    onClick = onOpenAppSettings,
                                    modifier = Modifier.testTag("open_settings_button"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open App Settings")
                                }
                            } else {
                                Button(
                                    onClick = onRequestMissingPermissions,
                                    modifier = Modifier.testTag("grant_missing_permissions_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DropAlertRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Grant",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Grant Missing Permissions")
                                }
                            }
                        }
                    }
                }
            }

            // List of permissions
            permissions.forEach { item ->
                PermissionRowItem(item = item)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PermissionRowItem(item: PermissionItem) {
    val icon = when {
        item.permission.contains("PHONE_STATE") -> Icons.Default.PhoneAndroid
        item.permission.contains("LOCATION") -> Icons.Default.LocationOn
        item.permission.contains("NOTIFICATIONS") -> Icons.Default.Notifications
        else -> Icons.Default.Wifi
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isGranted) FiveGGreen.copy(alpha = 0.2f) else FourGAmber.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isGranted) FiveGGreenContainer else FourGAmberContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.title,
                    tint = if (item.isGranted) FiveGGreen else FourGAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (item.isGranted) FiveGGreenContainer else DropAlertRedContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.isGranted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (item.isGranted) "Granted" else "Denied",
                        tint = if (item.isGranted) FiveGGreen else DropAlertRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (item.isGranted) "GRANTED" else "DENIED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isGranted) FiveGGreen else DropAlertRed
                    )
                }
            }
        }
    }
}
