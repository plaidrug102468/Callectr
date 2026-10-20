/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package app.callectr.recorder.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecentActors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.callectr.recorder.R
import app.callectr.recorder.system.storage.SafHelper
import app.callectr.recorder.data.AppPreferences
import androidx.compose.runtime.mutableIntStateOf
import app.callectr.recorder.data.PrivilegedMode
import app.callectr.recorder.server.ModeSwitchResult
import app.callectr.recorder.server.RecorderBackend
import app.callectr.recorder.server.ShizukuBackend
import app.callectr.recorder.server.ShizukuStatus
import app.callectr.recorder.integrations.adb.AdbShell
import app.callectr.recorder.integrations.adb.DeveloperOptions
import app.callectr.recorder.onboarding.OnboardingStatus
import app.callectr.recorder.system.openAppSettings
import app.callectr.recorder.system.openDeveloperSettings
import app.callectr.recorder.system.openDeviceInfoSettings
import app.callectr.recorder.system.openWirelessDebugging
import app.callectr.recorder.ui.common.CvCard
import app.callectr.recorder.ui.common.OemGateNotice
import app.callectr.recorder.ui.common.CvHero
import app.callectr.recorder.ui.common.CvPrimaryButton
import app.callectr.recorder.ui.common.CvSectionHeader
import app.callectr.recorder.ui.common.CvStatusPill
import app.callectr.recorder.ui.common.CvTone
import app.callectr.recorder.ui.common.ModeSwitchDialog
import app.callectr.recorder.ui.theme.CallVaultTheme
import app.callectr.recorder.ui.theme.LocalCvBrand
import app.callectr.recorder.ui.viewmodels.PermissionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stateful wrapper that connects [PermissionsViewModel] to [PermissionsContent].
 *
 * It owns the Android-specific launchers ([rememberLauncherForActivityResult]) that must live
 * inside a composable and passes them into [PermissionsViewModel.onGrantAccess] as lambdas so
 * the ViewModel stays free of Compose and Activity references.
 *
 * @param status              The current [OnboardingStatus.Status] snapshot, observed by the
 *                            router in [AppNavigationScreen] via [collectAsState].
 * @param onPermissionGranted Called after any grant action completes so the router can refresh state.
 * @param modifier            Optional size/position modifier forwarded to [PermissionsContent].
 * @param viewModel           The "Brain" that decides which permission to request next.
 */
@Composable
fun PermissionsScreen(
    status: OnboardingStatus.Status,
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = viewModel()
) {

    val activityContext = LocalContext.current

    // Permission launchers must live inside a composable - the system dialog can only be
    // triggered from a composable context.  We pass these into the ViewModel as lambdas so
    // the ViewModel never needs to import Compose or hold a UI reference.
    val permissionRequestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        // false = permission denied or permanently blocked by the OS.
        // In the blocked case we open App Info so the user can grant it manually.
        if (!result) {
            activityContext.openAppSettings()
        }
        onPermissionGranted()
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && SafHelper.isCloudFolder(uri)) {
            // Reject a cloud folder (Google Drive, …) as the capture destination — it breaks live
            // recording. Don't advance onboarding, so the user picks on-device storage instead.
            Toast.makeText(activityContext, activityContext.getString(R.string.folder_cloud_rejected), Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        if (uri != null) {
            // takePersistableUriPermission locks in long-term read/write access so the
            // folder URI remains valid after a device reboot.
            activityContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            AppPreferences(activityContext).setRecordingFolderUri(uri)
        }
        onPermissionGranted()
    }
    // Keep first-run simple: after Android's battery-exemption screen, return straight to Callectr.
    // OEM-specific background tuning remains an advanced diagnostic later.
    val batteryExemptionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onPermissionGranted()
    }

    // No external shell-permission check needed: CallVault's embedded ADB shell runs as uid 2000
    // (shell), which already holds the audio-capture privileges scrcpy-server needs.

    PermissionsContent(
        status = status,
        onGrantAccessButtonClick = {
            viewModel.onGrantAccess(
                status = status,
                onPermissionGranted = onPermissionGranted,
                requestRuntimePermission = { permission -> permissionRequestLauncher.launch(permission) },
                launchFolderPicker = { folderPickerLauncher.launch(null) },
                onRequestBatteryExemption = {
                    batteryExemptionLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData("package:${activityContext.packageName}".toUri())
                    )
                },
            )
        },
        modifier = modifier
    )
}

/**
 * Stateless visual layer for the permissions checklist screen, redesigned on the "Signal" system.
 *
 * Renders a hero + a branded checklist of [PermissionCard] rows (leading icon disc, label,
 * one-line description, trailing [CvStatusPill]) based on the [OnboardingStatus.Status] snapshot.
 * The ADB step reads as the hero step — a teal-tinted card with a pairing hint when not connected.
 * A persistent bottom CTA fires [onGrantAccessButtonClick]; its label follows the original logic.
 * Contains no logic — all decisions live in [PermissionsViewModel].
 *
 * Accepting [OnboardingStatus.Status] directly (instead of a separate mapping type) ensures
 * that adding a new prerequisite to [OnboardingStatus] is reflected here automatically,
 * without maintaining a redundant parallel data structure.
 *
 * @param status                 The current "Snapshot" of every permission and setup step.
 * @param onGrantAccessButtonClick Forwarded to [PermissionsViewModel.onGrantAccess] by the
 *                               stateful [PermissionsScreen] wrapper.
 * @param modifier               Optional size/position modifier for the root [Surface].
 */
@Composable
fun PermissionsContent(
    status: OnboardingStatus.Status,
    onGrantAccessButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var offerBuildNumberStep by remember { mutableStateOf(shouldOfferBuildNumberStep(context)) }
    var wirelessDebuggingEnabled by remember {
        mutableStateOf(AdbShell.isWirelessDebuggingEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                offerBuildNumberStep = shouldOfferBuildNumberStep(context)
                wirelessDebuggingEnabled = AdbShell.isWirelessDebuggingEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentIndex = when {
        !status.notificationsGranted -> 0
        !status.adbConnected -> 1
        !status.contactsGranted -> 2
        !status.phoneStateGranted -> 3
        !status.callLogGranted -> 4
        !status.batteryExempted -> 5
        else -> 6
    }
    val totalSteps = 6
    val isAdbStep = currentIndex == 1

    val currentIcon = when (currentIndex) {
        0 -> Icons.Default.Notifications
        1 -> Icons.Default.DeveloperMode
        2 -> Icons.Default.RecentActors
        3 -> Icons.Default.Phone
        4 -> Icons.Default.History
        5 -> Icons.Default.BatteryStd
        else -> Icons.Default.Phone
    }
    val currentTitle = when (currentIndex) {
        0 -> "Allow notifications"
        1 -> "Pair Callectr with Android"
        2 -> "Allow contacts"
        3 -> "Allow phone access"
        4 -> "Allow call history"
        5 -> "Allow background operation"
        else -> "Permissions complete"
    }
    val currentBody = when (currentIndex) {
        0 -> "Callectr uses notifications to show recording status and the one-time pairing-code entry."
        1 -> when {
            offerBuildNumberStep -> "Developer Options must be enabled once. Callectr will open the correct Android settings page."
            !wirelessDebuggingEnabled -> "Turn on Wireless debugging. It gives Callectr the Android-level access needed to start the recorder."
            else -> "Tap Pair, then choose “Pair device with pairing code” in Android. Enter the six-digit code in Callectr’s notification."
        }
        2 -> "Used to show caller names. More advanced contact filters can be configured later."
        3 -> "Required to detect when a normal cellular call starts, connects and ends."
        4 -> "Used to identify the completed call and its phone number reliably."
        5 -> "Prevents Android from putting Callectr to sleep while it needs to detect and finish recordings."
        else -> "Everything required for the first recording test is ready."
    }

    val buttonText = when {
        currentIndex >= totalSteps -> "Continue"
        isAdbStep && offerBuildNumberStep -> "Open device info"
        isAdbStep && !wirelessDebuggingEnabled -> "Open Wireless debugging"
        isAdbStep -> "Pair"
        else -> "Continue"
    }

    val buttonAction: () -> Unit = when {
        currentIndex >= totalSteps -> onGrantAccessButtonClick
        isAdbStep && offerBuildNumberStep -> { { context.openDeviceInfoSettings() } }
        isAdbStep && !wirelessDebuggingEnabled -> {
            {
                app.callectr.recorder.integrations.adb.AdbPairingService.start(context)
                context.openWirelessDebugging()
            }
        }
        isAdbStep -> {
            {
                onGrantAccessButtonClick()
                context.openWirelessDebugging()
            }
        }
        else -> onGrantAccessButtonClick
    }

    Surface(
        modifier = modifier.navigationBarsPadding().fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 28.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (currentIndex < totalSteps) "SETUP \${currentIndex + 1} OF \$totalSteps" else "SETUP COMPLETE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    CvHero(
                        title = "Get Callectr ready",
                        subtitle = "One thing at a time. No recording-folder selection and no advanced features during first setup."
                    )
                }

                item {
                    CvCard {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = currentBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isAdbStep) {
                            Spacer(Modifier.height(14.dp))
                            CvStatusPill(
                                text = if (wirelessDebuggingEnabled) "Wireless debugging on" else "Wireless debugging off",
                                tone = if (wirelessDebuggingEnabled) CvTone.Success else CvTone.Neutral
                            )
                        }
                    }
                }

                item {
                    CvCard {
                        Text(
                            text = "What happens after this?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Callectr applies one safe baseline automatically: manual recording, AAC/M4A, and Internal storage → Recordings → Callectr. Then we test one cellular call before adding anything else.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Callectr \${app.callectr.recorder.BuildConfig.VERSION_NAME} • build \${app.callectr.recorder.BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                CvPrimaryButton(
                    text = buttonText,
                    onClick = buttonAction
                )
            }
        }
    }
}

/** Reads whether Developer Options is currently enabled. Delegates to the shared [DeveloperOptions]. */
private fun isDeveloperOptionsEnabled(context: android.content.Context): Boolean =
    DeveloperOptions.isEnabled(context)

/**
 * Whether to send the user to Settings ▸ About phone to tap Build number.
 *
 * Only a **proven** off. Asking `!isDeveloperOptionsEnabled()` instead is what made issue #40 fatal: on a
 * build that redacts `DEVELOPMENT_SETTINGS_ENABLED` the read is permanently `0`, so the ADB step's button
 * opened About phone every time and pairing could never be reached. The reporter had already tapped Build
 * number seven times; nothing he could do would change that read.
 */
private fun shouldOfferBuildNumberStep(context: android.content.Context): Boolean =
    DeveloperOptions.state(context).shouldOfferBuildNumberStep

/**
 * The dedicated, full-width ADB hero card.
 *
 * Unlike the generic [PermissionCard] (icon-left / text-middle / pill-right), the ADB step uses its
 * own vertical layout so the description and hint can use the full card width:
 *  - Top row: leading icon disc + title + [CvStatusPill] (Connected / Not connected).
 *  - A full-width short description line.
 *  - The teal-tinted pairing hint box (only while not connected) — text adapts to WD state.
 *  - A Developer Options + Wireless-debugging status block.
 *
 * Developer-Options and Wireless-debugging are device/global states (not part of
 * [OnboardingStatus.Status]); they are detected in [PermissionsContent] (re-evaluated on ON_RESUME)
 * and passed in here so the card stays coherent with the bottom CTA's action.
 *
 * The bottom CTA already drives the "open Wireless debugging" / "open device info" actions, so this
 * card only shows status lines (no duplicate buttons) to keep one clear action on screen.
 */
@Composable
private fun AdbPermissionCard(
    adbConnected: Boolean,
    devOptionsEnabled: Boolean,
    /**
     * Whether Developer options are PROVEN off. Distinct from `!devOptionsEnabled`, which on a phone that
     * redacts the setting is true forever — and used to put a permanent, false "Developer options are
     * off" warning on the first screen a new user sees (issue #40).
     */
    devOptionsProvenOff: Boolean,
    wirelessDebuggingEnabled: Boolean
) {
    val primary = MaterialTheme.colorScheme.primary
    val brand = LocalCvBrand.current

    // Hero tint while not connected; plain surface once connected.
    val container: Color =
        if (!adbConnected) primary.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)
        else MaterialTheme.colorScheme.surface
    val discAccent: Color = if (adbConnected) primary else brand.warning

    CvCard(
        color = container,
        border = adbConnected,
        contentPadding = PaddingValues(16.dp)
    ) {
        // Top row: icon disc + title + status pill. Title wraps to 2 lines; pill stays compact.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(discAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeveloperMode,
                    contentDescription = null,
                    tint = discAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.permission_adb_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            CvStatusPill(
                text = if (adbConnected)
                    stringResource(R.string.permissions_status_adb_connected)
                else stringResource(R.string.permissions_status_adb_not_connected),
                tone = if (adbConnected) CvTone.Success else CvTone.Warning
            )
        }

        // Full-width short description.
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_adb_desc_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Pairing hint (full width) — only while not yet connected, AND only once Wireless debugging
        // is on (when WD is off the user can't pair yet, so the WD status line guides them instead).
        if (!adbConnected && devOptionsEnabled && wirelessDebuggingEnabled) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(primary.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.permission_adb_hint_wd_on),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Developer Options + Wireless-debugging status lines. The bottom CTA owns the matching
        // action (open device info / open Wireless debugging / authorize), so no button is shown here.
        Spacer(Modifier.height(12.dp))
        if (devOptionsProvenOff) {
            // Can't pair without Developer Options, and the app can't auto-enable it.
            DevModeStatusLine(text = stringResource(R.string.permission_devmode_off), color = brand.warning)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.permission_devmode_enable_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            DevModeStatusLine(text = stringResource(R.string.permission_devmode_on), color = brand.success)
            Spacer(Modifier.height(8.dp))
            if (wirelessDebuggingEnabled) {
                DevModeStatusLine(text = stringResource(R.string.permission_wd_on), color = brand.success)
            } else {
                DevModeStatusLine(text = stringResource(R.string.permission_wd_off), color = brand.warning)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.permission_wd_enable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Some OEMs refuse to let the shell hand us the privilege at all, whatever the switches above say.
        // Nothing else on this screen can explain that, and the user cannot get past it without a switch
        // only they can reach — see [OemGateNotice]. Self-hiding when it does not apply.
        Spacer(Modifier.height(12.dp))
        OemGateNotice()
    }
}

/** A subtle status line with a leading dot, tinted [color] (success/warning). */
@Composable
private fun DevModeStatusLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

/**
 * A branded permission row: a leading circular icon disc, the label + muted one-line description,
 * and a trailing [CvStatusPill] (Granted / Required, or a custom override). When [isHero] the card
 * is teal-tinted and an optional [hint] line is shown — used for the key ADB / wireless-debugging
 * step so it visually leads the checklist.
 */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    isHero: Boolean = false,
    hint: String? = null,
    pillText: String? = null,
    pillTone: CvTone? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val brand = LocalCvBrand.current

    // Hero step (ADB-not-connected) gets a confident teal tint; everything else uses the plain surface.
    val container: Color =
        if (isHero) primary.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)
        else MaterialTheme.colorScheme.surface

    // Disc accent: teal when granted, brand-warning while pending — reads as a clear at-a-glance state.
    val discAccent: Color = if (granted) primary else brand.warning

    val tone = pillTone ?: if (granted) CvTone.Success else CvTone.Warning
    val statusText = pillText
        ?: if (granted) stringResource(R.string.permissions_status_granted)
        else stringResource(R.string.permissions_status_required)

    CvCard(
        color = container,
        border = !isHero,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(discAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = discAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            CvStatusPill(text = statusText, tone = tone)
        }

        if (hint != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(primary.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    CallVaultTheme {
        PermissionsContent(
            status = OnboardingStatus.Status(
                disclaimerAccepted       = true,
                notificationsGranted     = false,
                contactsGranted          = true,
                phoneStateGranted        = false,
                callLogGranted           = false,
                batteryExempted          = false,
                storageSelected          = false,
                adbConnected             = false,
                wizardCompleted          = false
            ),
            onGrantAccessButtonClick = {}
        )
    }
}
