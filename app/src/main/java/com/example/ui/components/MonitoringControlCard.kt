package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GuardSettings
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FiveGGreen
import com.example.ui.theme.FiveGGreenContainer
import com.example.ui.theme.TextMuted

@Composable
fun MonitoringControlCard(
    settings: GuardSettings,
    onToggleMaster: (Boolean) -> Unit,
    onUpdateSettings: (GuardSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monitoring_control_card"),
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
            // Master Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power",
                        tint = if (settings.isMonitoringActive) FiveGGreen else TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Background 5G Guard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (settings.isMonitoringActive)
                                "Real-time background service active"
                            else
                                "Monitoring paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = settings.isMonitoringActive,
                    onCheckedChange = onToggleMaster,
                    modifier = Modifier.testTag("master_monitor_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FiveGGreen,
                        checkedTrackColor = FiveGGreenContainer
                    )
                )
            }

            // Sub Rules
            AnimatedVisibility(visible = settings.isMonitoringActive) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "GUARD & ALERT RULES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    RuleToggleRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Alert on 4G / LTE Drop",
                        subtitle = "High-priority alert when mobile data falls below 5G",
                        checked = settings.alertOn4GDrop,
                        testTag = "alert_on_4g_drop_switch",
                        onCheckedChange = { onUpdateSettings(settings.copy(alertOn4GDrop = it)) }
                    )

                    RuleToggleRow(
                        icon = Icons.Default.Vibration,
                        title = "Haptic Vibration",
                        subtitle = "Vibrate phone forcefully when 5G drops",
                        checked = settings.vibrateOnDrop,
                        testTag = "vibrate_switch",
                        onCheckedChange = { onUpdateSettings(settings.copy(vibrateOnDrop = it)) }
                    )

                    RuleToggleRow(
                        icon = Icons.Default.VolumeUp,
                        title = "Sound Alert",
                        subtitle = "Play audio notification on degradation",
                        checked = settings.soundAlertOnDrop,
                        testTag = "sound_switch",
                        onCheckedChange = { onUpdateSettings(settings.copy(soundAlertOnDrop = it)) }
                    )

                    RuleToggleRow(
                        icon = Icons.Default.SettingsSuggest,
                        title = "Auto-Open Cellular Settings",
                        subtitle = "Quickly navigate to toggle preferred network",
                        checked = settings.autoOpenSettingsOnDrop,
                        testTag = "auto_settings_switch",
                        onCheckedChange = { onUpdateSettings(settings.copy(autoOpenSettingsOnDrop = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
