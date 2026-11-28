package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun FocusActiveBanner(
    onExitFocusLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("focus_active_banner"),
        shape = RoundedCornerShape(18.dp),
        color = cs.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = cs.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.focus_mode_active_banner),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = cs.onErrorContainer
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onExitFocusLock,
                modifier = Modifier.testTag("btn_exit_focus_mode"),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = cs.error,
                    contentColor = cs.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.focus_mode_exit),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
fun FocusModeDialog(
    isFocusLockActive: Boolean,
    autoFocusOnLaunch: Boolean,
    hasDndPolicyAccess: Boolean,
    onToggleAutoFocusOnLaunch: (Boolean) -> Unit,
    onOpenDndPolicySettings: () -> Unit,
    onToggleFocusLock: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ProvideAssistantSubpage {
        AssistantPageKind.FOCUS.snapshot(
            "专注锁定：$isFocusLockActive；启动时自动进入：$autoFocusOnLaunch；勿扰权限：$hasDndPolicyAccess"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = cs.surfaceContainerLow,
        icon = {
            Icon(
                imageVector = if (isFocusLockActive) Icons.Rounded.Lock else Icons.Rounded.Shield,
                contentDescription = null,
                tint = if (isFocusLockActive) cs.error else cs.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.focus_mode_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                PageAssistantIcon()
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.focus_mode_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )

                // System DND permission card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (hasDndPolicyAccess) cs.secondaryContainer.copy(alpha = 0.5f)
                            else cs.tertiaryContainer.copy(alpha = 0.55f)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (hasDndPolicyAccess) Icons.Rounded.CheckCircle else Icons.Rounded.DoNotDisturbOn,
                        contentDescription = null,
                        tint = if (hasDndPolicyAccess) cs.secondary else cs.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(
                            if (hasDndPolicyAccess) R.string.focus_mode_dnd_ok else R.string.focus_mode_dnd_missing
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!hasDndPolicyAccess) {
                        FilledTonalButton(
                            onClick = onOpenDndPolicySettings,
                            modifier = Modifier.testTag("btn_grant_dnd_access"),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.focus_mode_grant_dnd),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                // Auto-enable Focus Mode on launch toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.focus_mode_auto_launch),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                            color = cs.onSurface
                        )
                        Text(
                            text = stringResource(R.string.focus_mode_auto_launch_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = cs.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoFocusOnLaunch,
                        onCheckedChange = onToggleAutoFocusOnLaunch,
                        modifier = Modifier.testTag("switch_auto_focus_launch")
                    )
                }

                // Transparent OS / CarWith explanation box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.surfaceContainerHigh.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.focus_mode_carwith_tip),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = cs.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = {
                        onToggleFocusLock()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_dialog_toggle_focus"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFocusLockActive) cs.error else cs.primary,
                        contentColor = if (isFocusLockActive) cs.onError else cs.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isFocusLockActive) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isFocusLockActive) R.string.focus_mode_exit else R.string.focus_mode_enter
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.hr_close))
            }
        }
    )
}
