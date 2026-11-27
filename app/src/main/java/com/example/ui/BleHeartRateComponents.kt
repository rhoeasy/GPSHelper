package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ble.BleConnectionStatus
import com.example.ble.BleDeviceCategory
import com.example.ble.BleDeviceClassifier
import com.example.ble.BleDeviceSource
import com.example.ble.BleHrDevice
import com.example.ble.HeartRateState
import com.example.ble.SavedBleDevice
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors

@Composable
private fun bleConnectionStatusText(state: HeartRateState): String? = when (state.status) {
    BleConnectionStatus.CONNECTING -> {
        val connecting = stringResource(R.string.hr_status_connecting)
        state.deviceName?.let { "$connecting $it" } ?: connecting
    }
    BleConnectionStatus.CONNECTED -> {
        val name = state.deviceName ?: stringResource(R.string.hr_device_name_fallback)
        val label = if (state.bpm == null) R.string.hr_status_waiting_measurement else R.string.hr_status_connected
        stringResource(label, name)
    }
    BleConnectionStatus.ERROR, BleConnectionStatus.DISCONNECTED -> state.errorMessage
    else -> null
}

@Composable
fun HeartRateStripCard(
    hrState: HeartRateState,
    lastSavedDevice: SavedBleDevice? = null,
    onOpenDevicePicker: () -> Unit,
    onQuickReconnect: () -> Unit = onOpenDevicePicker,
    onDisconnect: () -> Unit,
    isAlertActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    val cardBg by animateColorAsState(
        targetValue = if (isAlertActive) cs.errorContainer else cs.surfaceContainerLow,
        label = "hr_card_bg"
    )

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
        onClick = {
            if (!hrState.isConnected && hrState.status != BleConnectionStatus.CONNECTING) {
                onOpenDevicePicker()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("heart_rate_card"),
        shape = RoundedCornerShape(22.dp),
        color = cardBg
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
                    .background(
                        when {
                            isAlertActive -> cs.error
                            hrState.isConnected -> tc.heartContainer
                            else -> cs.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = when {
                        isAlertActive -> cs.onError
                        hrState.isConnected -> tc.heart
                        else -> cs.onSurfaceVariant
                    },
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hr_card_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isAlertActive) cs.onErrorContainer else cs.onSurface
                    )
                    if (hrState.isConnected) {
                        Text(
                            text = hrState.bpm?.toString() ?: "--",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 21.sp
                            ),
                            color = if (isAlertActive) cs.error else tc.heart,
                            modifier = Modifier.testTag("hr_bpm_value")
                        )
                        Text(
                            text = stringResource(R.string.hr_unit_bpm),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = if (isAlertActive) cs.onErrorContainer else cs.onSurfaceVariant
                        )
                    }
                    if (isAlertActive) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = cs.error,
                            modifier = Modifier.testTag("hr_high_alert_badge")
                        ) {
                            Text(
                                text = stringResource(R.string.alert_badge_high_hr),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                color = cs.onError,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val subtitle = bleConnectionStatusText(hrState)
                    ?: if (lastSavedDevice != null) {
                        stringResource(R.string.hr_status_last_prefix, lastSavedDevice.name)
                    } else {
                        stringResource(R.string.hr_status_disconnected)
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
            } else if (lastSavedDevice != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onQuickReconnect,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_quick_reconnect_hr"),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.secondaryContainer,
                            contentColor = cs.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothConnected,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.hr_reconnect_btn),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onOpenDevicePicker,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_connect_hr"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothSearching,
                            contentDescription = stringResource(R.string.hr_switch_device_cd),
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
    localDevices: List<BleHrDevice>,
    scannedDevices: List<BleHrDevice>,
    lastSavedDevice: SavedBleDevice?,
    autoReconnectEnabled: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onForgetLastDevice: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (name: String, address: String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showOnlyRecommended by rememberSaveable { mutableStateOf(true) }

    val trimmedQuery = searchQuery.trim()

    val filteredLocal = remember(localDevices, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            localDevices
        } else {
            localDevices.filter {
                it.name.contains(trimmedQuery, ignoreCase = true) ||
                    it.address.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    val recommendedNearbyCount = remember(scannedDevices) {
        scannedDevices.count { BleDeviceClassifier.isRecommendedWearableOrHr(it) }
    }

    // If user is searching by text, search across ALL nearby devices; otherwise respect filter pill.
    // If no recommended devices are found yet, fall back to showing all nearby devices automatically.
    val effectiveFilterRecommended = showOnlyRecommended && trimmedQuery.isEmpty() && recommendedNearbyCount > 0

    val filteredNearby = remember(scannedDevices, trimmedQuery, effectiveFilterRecommended) {
        scannedDevices.filter { dev ->
            val matchesQuery = trimmedQuery.isEmpty() ||
                dev.name.contains(trimmedQuery, ignoreCase = true) ||
                dev.address.contains(trimmedQuery, ignoreCase = true)
            val matchesCategory = !effectiveFilterRecommended ||
                BleDeviceClassifier.isRecommendedWearableOrHr(dev)
            matchesQuery && matchesCategory
        }
    }

    val lastDeviceIsConnectedLocally = remember(lastSavedDevice, localDevices) {
        lastSavedDevice != null && localDevices.any {
            it.address.equals(lastSavedDevice.address, ignoreCase = true) &&
                it.source == BleDeviceSource.CONNECTED_LOCAL
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .testTag("ble_device_picker_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
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

                // Compact search input for quickly locating a band by name
                Surface(
                    shape = RoundedCornerShape(50),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ble_search_input_box")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.hr_search_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                                cursorBrush = SolidColor(cs.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ble_search_field")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.hr_close),
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                val connectionMessage = hrState.errorMessage ?: bleConnectionStatusText(hrState)
                if (connectionMessage != null) {
                    val hasError = hrState.errorMessage != null
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (hasError) cs.errorContainer else cs.secondaryContainer
                    ) {
                        Text(
                            text = connectionMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = if (hasError) cs.onErrorContainer else cs.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Unified scrollable device list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section 1: Last Connected Device + Auto-Reconnect Switch
                    if (lastSavedDevice != null && trimmedQuery.isEmpty()) {
                        item(key = "section_last_header") {
                            BleSectionHeader(title = stringResource(R.string.hr_last_device_section))
                        }
                        item(key = "last_saved_card") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ble_reconnect_last"),
                                shape = RoundedCornerShape(18.dp),
                                color = cs.secondaryContainer.copy(alpha = 0.72f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                onSelectDevice(lastSavedDevice.name, lastSavedDevice.address)
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(cs.secondary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.BluetoothConnected,
                                                contentDescription = null,
                                                tint = cs.onSecondary,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = lastSavedDevice.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = cs.onSecondaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (lastDeviceIsConnectedLocally) {
                                                    BleStatusPill(
                                                        text = stringResource(R.string.hr_badge_connected_local),
                                                        bg = cs.secondary,
                                                        fg = cs.onSecondary
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${stringResource(R.string.hr_last_device)} · ${BleDeviceClassifier.shortAddressTail(lastSavedDevice.address)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                onSelectDevice(lastSavedDevice.name, lastSavedDevice.address)
                                            },
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = cs.secondary,
                                                contentColor = cs.onSecondary
                                            )
                                        ) {
                                            Text(
                                                text = stringResource(R.string.hr_reconnect_btn),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                        IconButton(
                                            onClick = onForgetLastDevice,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("btn_forget_last_ble")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.hr_forget_device_cd),
                                                tint = cs.onSecondaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Auto-reconnect toggle row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(cs.surface.copy(alpha = 0.45f))
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.hr_auto_reconnect_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = cs.onSecondaryContainer
                                        )
                                        Switch(
                                            checked = autoReconnectEnabled,
                                            onCheckedChange = onToggleAutoReconnect,
                                            modifier = Modifier.testTag("switch_ble_auto_reconnect")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Locally Connected & System-Paired Devices
                    if (filteredLocal.isNotEmpty()) {
                        item(key = "section_local_header") {
                            BleSectionHeader(
                                title = "${stringResource(R.string.hr_local_devices_section)} (${filteredLocal.size})"
                            )
                        }
                        items(filteredLocal, key = { "local_${it.address}" }) { device ->
                            BleDeviceRowCard(
                                device = device,
                                onClick = { onSelectDevice(device.name, device.address) }
                            )
                        }
                    }

                    // Section 3: Nearby Scanned Devices + Filter Chips
                    item(key = "section_nearby_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BleSectionHeader(
                                title = "${stringResource(R.string.hr_nearby_devices_section)} (${scannedDevices.size})"
                            )
                            if (scannedDevices.isNotEmpty() && trimmedQuery.isEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(cs.surfaceContainerHigh)
                                        .padding(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterTogglePill(
                                        label = stringResource(R.string.hr_filter_recommended),
                                        selected = effectiveFilterRecommended,
                                        enabled = recommendedNearbyCount > 0,
                                        onClick = { showOnlyRecommended = true },
                                        tag = "ble_filter_recommended"
                                    )
                                    FilterTogglePill(
                                        label = stringResource(R.string.hr_filter_all),
                                        selected = !effectiveFilterRecommended,
                                        enabled = true,
                                        onClick = { showOnlyRecommended = false },
                                        tag = "ble_filter_all"
                                    )
                                }
                            }
                        }
                    }

                    if (filteredNearby.isEmpty()) {
                        item(key = "nearby_empty_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when {
                                        trimmedQuery.isNotEmpty() && filteredLocal.isEmpty() ->
                                            stringResource(R.string.hr_search_empty)
                                        isScanning -> stringResource(R.string.hr_scanning)
                                        else -> stringResource(R.string.hr_empty_scan)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredNearby, key = { "nearby_${it.address}" }) { device ->
                            BleDeviceRowCard(
                                device = device,
                                onClick = { onSelectDevice(device.name, device.address) }
                            )
                        }
                    }

                    // Compact helpful tip at bottom of scrollable area
                    item(key = "mi_band_tip") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cs.primaryContainer.copy(alpha = 0.55f)
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
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                                    color = cs.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Bottom action row
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

@Composable
private fun BleSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, start = 2.dp)
    )
}

@Composable
private fun FilterTogglePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = when {
                !enabled -> cs.outline.copy(alpha = 0.55f)
                selected -> cs.onPrimaryContainer
                else -> cs.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun BleDeviceRowCard(
    device: BleHrDevice,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tc = LocalTelemetryColors.current

    val iconVector = when {
        device.advertisesHeartRate || device.category == BleDeviceCategory.HEART_RATE_SENSOR ->
            Icons.Rounded.Favorite
        device.category == BleDeviceCategory.WEARABLE -> Icons.Rounded.Watch
        device.source == BleDeviceSource.CONNECTED_LOCAL -> Icons.Rounded.BluetoothConnected
        else -> Icons.Rounded.Bluetooth
    }

    val (iconBg, iconTint) = when {
        device.advertisesHeartRate || device.category == BleDeviceCategory.HEART_RATE_SENSOR ->
            tc.heartContainer to tc.heart
        device.source == BleDeviceSource.CONNECTED_LOCAL ->
            cs.secondaryContainer to cs.onSecondaryContainer
        device.category == BleDeviceCategory.WEARABLE ->
            cs.primaryContainer to cs.onPrimaryContainer
        else -> cs.surfaceContainerHigh to cs.onSurfaceVariant
    }

    val categoryText = BleDeviceClassifier.categoryLabelZh(device.category)
    val signalDesc = BleDeviceClassifier.signalDescriptionZh(device.rssi, device.source)
    val shortTail = BleDeviceClassifier.shortAddressTail(device.address)
    val bars = BleDeviceClassifier.signalBars(device.rssi)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ble_device_row_${device.address}"),
        shape = RoundedCornerShape(16.dp),
        color = if (device.source == BleDeviceSource.CONNECTED_LOCAL) {
            cs.surfaceContainerHigh
        } else {
            cs.surfaceContainer
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (device.source == BleDeviceSource.CONNECTED_LOCAL) {
                        BleStatusPill(
                            text = stringResource(R.string.hr_badge_connected_local),
                            bg = cs.secondaryContainer,
                            fg = cs.onSecondaryContainer
                        )
                    } else if (device.source == BleDeviceSource.PAIRED_LOCAL) {
                        BleStatusPill(
                            text = stringResource(R.string.hr_badge_paired_local),
                            bg = cs.primaryContainer,
                            fg = cs.onPrimaryContainer
                        )
                    }
                    if (device.advertisesHeartRate) {
                        BleStatusPill(
                            text = stringResource(R.string.hr_badge_service),
                            bg = tc.heartContainer,
                            fg = tc.heart
                        )
                    } else if (device.category == BleDeviceCategory.WEARABLE &&
                        device.source == BleDeviceSource.NEARBY_SCAN
                    ) {
                        BleStatusPill(
                            text = stringResource(R.string.hr_badge_wearable),
                            bg = cs.primaryContainer,
                            fg = cs.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = "$categoryText · $signalDesc · $shortTail",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (bars != null) {
                SignalBarsIndicator(
                    level = bars,
                    activeColor = when {
                        bars >= 3 -> cs.secondary
                        bars == 2 -> cs.primary
                        else -> cs.outline
                    },
                    inactiveColor = cs.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun BleStatusPill(
    text: String,
    bg: Color,
    fg: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun SignalBarsIndicator(
    level: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .width(18.dp)
            .height(14.dp)
    ) {
        val barCount = 4
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val heightFraction = 0.35f + 0.65f * ((i + 1).toFloat() / barCount)
            val barHeight = size.height * heightFraction
            val left = i * (barWidth + gap)
            val top = size.height - barHeight
            drawRoundRect(
                color = if (i < level) activeColor else inactiveColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
