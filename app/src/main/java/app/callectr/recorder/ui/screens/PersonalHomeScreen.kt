package app.callectr.recorder.ui.screens

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.callectr.recorder.data.recordings.RecordingsRepository.RecordingItem
import app.callectr.recorder.ui.viewmodels.HomeViewModel
import app.callectr.recorder.ui.viewmodels.RecordingPlaybackController
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Callectr's personal-use home screen.
 *
 * Deliberately small: recorder health, local storage, recent recordings, playback and Settings.
 * Transcription, summaries, community links, imports and cloud features stay out of the primary UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalHomeScreen(
    onOpenSettings: () -> Unit,
    openRecording: String? = null,
    onOpenRecordingHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.playback.collectAsState()
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(openRecording, uiState.recordings) {
        val requested = openRecording ?: return@LaunchedEffect
        if (uiState.recordings.any { it.displayName == requested }) {
            selectedName = requested
            onOpenRecordingHandled()
        }
    }

    val selected = uiState.recordings.firstOrNull { it.displayName == selectedName }
    if (selected != null) {
        PersonalRecordingScreen(
            item = selected,
            playback = playback,
            onBack = {
                viewModel.playbackController.stop()
                selectedName = null
            },
            onPlay = { viewModel.play(selected) },
            onPause = viewModel::pausePlayback,
            onResume = viewModel::resumePlayback,
            onSeek = viewModel::seekTo,
            onDelete = {
                viewModel.playbackController.stop()
                viewModel.deleteRecording(selected)
                selectedName = null
            },
            modifier = modifier,
            viewModel = viewModel,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Callectr", fontWeight = FontWeight.Bold)
                        Text(
                            "Personal call recorder",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Phone,
                                contentDescription = null,
                                tint = if (uiState.status.isReady) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.padding(5.dp))
                            Column {
                                Text(
                                    if (uiState.status.isReady) "Ready to record" else "Recorder needs attention",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (uiState.status.isReady)
                                        "REC will be offered during supported calls."
                                    else
                                        "Open Settings to repair recorder access.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Storage, contentDescription = null)
                        Spacer(Modifier.padding(6.dp))
                        Column {
                            Text("Local storage", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Internal storage → Recordings → Callectr",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "No folder selection required",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Recordings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        uiState.recordings.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (uiState.isLoading && !uiState.hasLoaded) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.recordings.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("No recordings yet", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Make or receive a call and press REC when Callectr offers it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(uiState.recordings, key = { it.uri.toString() }) { item ->
                    PersonalRecordingRow(item = item, onClick = { selectedName = item.displayName })
                }
            }
        }
    }
}

@Composable
private fun PersonalRecordingRow(item: RecordingItem, onClick: () -> Unit) {
    val title = item.contactName
        ?: item.number?.takeIf { it.isNotBlank() }
        ?: item.voipApp?.let { "$it call" }
        ?: "Call recording"
    val subtitle = buildString {
        item.displayDate?.let { append(it) }
        item.durationSeconds?.let {
            if (isNotEmpty()) append("  •  ")
            append(formatDuration(it))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (item.voipApp != null) Icons.Filled.Headset else Icons.Filled.Phone,
                contentDescription = null,
            )
            Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalRecordingScreen(
    item: RecordingItem,
    playback: RecordingPlaybackController.PlaybackState,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    viewModel: HomeViewModel,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val active = playback.activeUri == item.uri

    BackHandler(onBack = onBack)

    LaunchedEffect(active, playback.phase) {
        while (active && playback.phase == RecordingPlaybackController.Phase.PLAYING) {
            viewModel.playbackController.syncPosition()
            delay(500)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        item.contactName ?: item.number ?: item.voipApp ?: "Call recording",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.displayDate ?: DateFormat.getMediumDateFormat(androidx.compose.ui.platform.LocalContext.current)
                            .format(Date(item.lastModified)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    if (active && playback.phase == RecordingPlaybackController.Phase.LOADING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        val duration = if (active) playback.durationMs else ((item.durationSeconds ?: 0L) * 1000L).toInt()
                        val position = if (active) playback.positionMs else 0
                        Slider(
                            value = position.toFloat().coerceAtMost(duration.coerceAtLeast(1).toFloat()),
                            onValueChange = { if (active) onSeek(it.toInt()) },
                            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                            enabled = active && duration > 0,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(formatMillis(position), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Text(formatMillis(duration), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            when {
                                !active -> onPlay()
                                playback.phase == RecordingPlaybackController.Phase.PLAYING -> onPause()
                                playback.phase == RecordingPlaybackController.Phase.PAUSED -> onResume()
                                else -> onPlay()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (active && playback.phase == RecordingPlaybackController.Phase.PLAYING)
                                Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            if (active && playback.phase == RecordingPlaybackController.Phase.PLAYING)
                                "Pause" else "Play"
                        )
                    }
                }
            }

            HorizontalDivider()

            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.padding(4.dp))
                Text("Delete recording", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete recording?") },
            text = { Text("This removes the local recording from Callectr and device storage.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatMillis(ms: Int): String = formatDuration((ms / 1000).toLong())
