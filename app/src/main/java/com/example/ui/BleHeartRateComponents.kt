package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ble.BleConnectionStatus
import com.example.ble.BleHrDevice
import com.example.ble.HeartRateState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun HeartRateStripCard(
    hrState: HeartRateState,
    onOpenDevicePicker: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    val beatTransition = rememberInfiniteTransition(label = "hr_beat")
    val beatScale by beatTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hr_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("heart_rate_card"),
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (hrState.isConnected) tc.heartContainer else cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = if (hrState.isConnected) tc.heart else cs.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(if (hrState.isConnected && hrState.bpm != null) beatScale else 1f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hr_card_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface
                    )
                    if (hrState.isConnected) {
                        Text(
                            text = hrState.bpm?.toString() ?: "--",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 21.sp
                            ),
                            color = tc.heart,
                            modifier = Modifier.testTag("hr_bpm_value")
                        )
                        Text(
                            text = stringResource(R.string.hr_unit_bpm),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = cs.onSurfaceVariant
                        )
                    }
                }

                val subtitle = when (hrState.status) {
                    BleConnectionStatus.CONNECTED -> hrState.deviceName ?: "BLE 心率设备"
                    BleConnectionStatus.CONNECTING -> stringResource(R.string.hr_status_connecting)
                    BleConnectionStatus.ERROR -> hrState.errorMessage ?: stringResource(R.string.hr_status_disconnected)
                    else -> stringResource(R.string.hr_status_disconnected)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = if (hrState.status == BleConnectionStatus.ERROR) cs.error else cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hrState.isConnected || hrState.status == BleConnectionStatus.CONNECTING) {
                FilledTonalButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_disconnect_hr"),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = cs.errorContainer,
                        contentColor = cs.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.hr_disconnect_btn),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onOpenDevicePicker,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_connect_hr"),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = cs.surfaceContainerHigh,
                        contentColor = cs.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.hr_connect_btn),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun BleDevicePickerDialog(
    hrState: HeartRateState,
    isScanning: Boolean,
    devices: List<BleHrDevice>,
    lastSavedDevice: Pair<String, String>?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (name: String, address: String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ble_device_picker_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Rounded.BluetoothSearching else Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.hr_dialog_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )
                    }
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = cs.primary
                        )
                    }
                }

                // Mi Band Heart Rate Broadcast Tip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.primaryContainer.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.hr_mi_band_tip),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = cs.onPrimaryContainer
                        )
                    }
                }

                hrState.errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cs.errorContainer
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = cs.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (lastSavedDevice != null) {
                    val (savedName, savedAddr) = lastSavedDevice
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectDevice(savedName, savedAddr) }
                            .testTag("ble_reconnect_last"),
                        shape = RoundedCornerShape(16.dp),
                        color = cs.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BluetoothConnected,
                                contentDescription = null,
                                tint = cs.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = savedName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = cs.onSecondaryContainer
                                )
                                Text(
                                    text = "${stringResource(R.string.hr_last_device)} · $savedAddr",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = cs.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Text(
                                text = stringResource(R.string.hr_connect_btn),
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.onSecondaryContainer
                            )
                        }
                    }
                }

                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(
                                if (isScanning) R.string.hr_scanning else R.string.hr_empty_scan
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices, key = { it.address }) { device ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSelectDevice(device.name, device.address) },
                                shape = RoundedCornerShape(16.dp),
                                color = cs.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (device.advertisesHeartRate) tc.heartContainer
                                                else cs.surfaceContainerHigh
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (device.advertisesHeartRate) {
                                                Icons.Rounded.Favorite
                                            } else {
                                                Icons.Rounded.Bluetooth
                                            },
                                            contentDescription = null,
                                            tint = if (device.advertisesHeartRate) tc.heart else cs.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = device.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = cs.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (device.advertisesHeartRate) {
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = tc.heartContainer
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.hr_badge_service),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = tc.heart,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${device.address}  ·  ${device.rssi} dBm",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (isScanning) onStopScan() else onStartScan() },
                        modifier = Modifier.testTag("btn_ble_rescan")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (isScanning) R.string.hr_stop_scan else R.string.hr_rescan
                            )
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_ble_close")
                    ) {
                        Text(text = stringResource(R.string.hr_close))
                    }
                }
            }
        }
    }
}
