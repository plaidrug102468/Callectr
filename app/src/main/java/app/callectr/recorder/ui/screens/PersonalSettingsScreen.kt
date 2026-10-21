package app.callectr.recorder.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.callectr.recorder.BuildConfig
import app.callectr.recorder.MainActivity
import app.callectr.recorder.data.AppPreferences

/**
 * Personal-use settings. Advanced upstream features are intentionally hidden from the normal app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context.applicationContext) }

    var appLock by remember { mutableStateOf(prefs.isAppLockEnabled()) }
    var screenshots by remember { mutableStateOf(prefs.isAllowScreenshots()) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayAllowed = Settings.canDrawOverlays(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SimpleSettingCard(
                    icon = Icons.Filled.Phone,
                    title = "Recording",
                    body = "Phone calls and supported app calls use manual REC. Callectr waits for the call to connect before saving audio.",
                    status = "Manual REC",
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Floating REC button", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.padding(3.dp))
                        Text(
                            if (overlayAllowed)
                                "Enabled. REC can appear over the Phone or WhatsApp call screen."
                            else
                                "Optional. Without this permission, REC remains available in the notification.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!overlayAllowed) {
                            Spacer(Modifier.padding(6.dp))
                            Button(
                                onClick = {
                                    overlayLauncher.launch(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:\${context.packageName}")
                                        )
                                    )
                                },
                            ) { Text("Enable floating REC") }
                        } else {
                            Spacer(Modifier.padding(5.dp))
                            Text("Enabled", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                SimpleSettingCard(
                    icon = Icons.Filled.Storage,
                    title = "Local storage",
                    body = "Recordings save automatically to Internal storage → Recordings → Callectr. There is no folder-selection step.",
                    status = "Automatic",
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Privacy", fontWeight = FontWeight.Bold)

                        SettingSwitchRow(
                            title = "App lock",
                            subtitle = "Use the phone's PIN, fingerprint or device credential when opening Callectr.",
                            checked = appLock,
                            onCheckedChange = {
                                appLock = it
                                prefs.setAppLockEnabled(it)
                                (context as? MainActivity)?.applySecureFlag()
                            },
                        )

                        SettingSwitchRow(
                            title = "Allow screenshots",
                            subtitle = "Keep this on while we develop Callectr so screenshots can be sent for review.",
                            checked = screenshots,
                            onCheckedChange = {
                                screenshots = it
                                prefs.setAllowScreenshots(it)
                                (context as? MainActivity)?.applySecureFlag()
                            },
                        )
                    }
                }
            }

            item {
                val ready = prefs.isPrivilegedTransportSetUp()
                SimpleSettingCard(
                    icon = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    title = "Recorder access",
                    body = if (ready)
                        "Android recorder access has been paired."
                    else
                        "Recorder access is not paired. Reopen Callectr setup/permissions before testing calls.",
                    status = if (ready) "Paired" else "Needs setup",
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("About", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.padding(3.dp))
                        Text(
                            "Callectr \${BuildConfig.VERSION_NAME} • build \${BuildConfig.VERSION_CODE}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Open-source licences remain bundled for legal attribution. Donation, community and upstream support links are not part of the personal Callectr interface.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleSettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    status: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(2.dp))
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                Text(status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(4.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
