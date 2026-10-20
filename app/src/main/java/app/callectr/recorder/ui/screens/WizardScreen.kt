/*
 * Callectr: call recording for Android.
 * Based on the CallVault project and distributed under GPL-3.0-or-later.
 */
package app.callectr.recorder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.callectr.recorder.BuildConfig
import app.callectr.recorder.R
import app.callectr.recorder.ui.common.CvCard
import app.callectr.recorder.ui.common.CvHero
import app.callectr.recorder.ui.common.CvPrimaryButton
import app.callectr.recorder.ui.common.CvScaffold
import app.callectr.recorder.ui.common.CvStatusPill
import app.callectr.recorder.ui.common.CvTone
import app.callectr.recorder.ui.viewmodels.WizardViewModel

/**
 * Minimal first-run setup.
 *
 * Permissions and ADB pairing happen before this screen. Everything else deliberately uses a known
 * baseline so the first physical-device goal is simple: make one call, record it, find the M4A in
 * Recordings/Callectr, and verify both sides. Advanced features stay in Settings for later.
 */
@Composable
fun WizardScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WizardViewModel = viewModel(),
) {
    CvScaffold(
        title = "Callectr",
        subtitle = "Simple test setup",
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                CvPrimaryButton(
                    text = "Use these defaults and continue",
                    leadingIcon = Icons.Filled.CheckCircle,
                    onClick = {
                        viewModel.applySimpleBaseline()
                        viewModel.finish()
                        onFinished()
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CvHero(
                    title = "Ready to test",
                    subtitle = "No folder picker, no Drive setup, no transcription downloads and no experimental options. First we prove recording works.",
                )
            }

            item {
                BaselineCard(
                    icon = Icons.Filled.Folder,
                    title = "Automatic storage",
                    body = "Recordings save to Internal storage → Recordings → Callectr. Android does not need a separate broad Files permission for this.",
                    status = "Automatic",
                )
            }

            item {
                BaselineCard(
                    icon = Icons.Filled.Phone,
                    title = "Phone and app calls",
                    body = "Carrier and WhatsApp/app-call support are enabled, but recording does not start automatically. Callectr will offer Record after the call is connected.",
                    status = "Manual",
                )
            }

            item {
                BaselineCard(
                    icon = Icons.Filled.GraphicEq,
                    title = "Compatible audio",
                    body = "AAC in an M4A container is the default for this test build so the finished file should play in ordinary Android/Samsung audio players.",
                    status = "AAC / M4A",
                )
            }

            item {
                BaselineCard(
                    icon = Icons.Filled.Settings,
                    title = "Advanced features later",
                    body = "Drive sync, transcription, summaries, custom folders, experimental resilient recording and automatic updates stay out of first-run setup until the core recorder is proven.",
                    status = "Deferred",
                )
            }

            item {
                CvCard {
                    Text(
                        text = "First test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Finish setup, make one ordinary cellular call, answer/connect it, press Record when offered, end the call, then check Recordings/Callectr and play the M4A.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Callectr ${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BaselineCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    status: String,
) {
    CvCard {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        CvStatusPill(text = status, tone = CvTone.Success)
    }
}
