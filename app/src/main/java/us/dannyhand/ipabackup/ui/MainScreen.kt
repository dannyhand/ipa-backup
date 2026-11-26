package us.dannyhand.ipabackup.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Everything the main screen shows. Safe to update from background threads (Compose snapshot state). */
class UiState {
    val log = mutableStateListOf<String>()
    var busy by mutableStateOf(false)
    /** The task currently running, e.g. "Backing up apps"; null when idle. */
    var task by mutableStateOf<String?>(null)
    var stopVisible by mutableStateOf(false)
    var stopLabel by mutableStateOf("Stop")
    var stopEnabled by mutableStateOf(true)
    /** "Danny Hand Jr · iOS 6.1.6" once a device has been seen. */
    var device by mutableStateOf<String?>(null)
    var connected by mutableStateOf(false)
}

class Feature(
    val title: String,
    val icon: ImageVector,
    val section: String,
    val needsJailbreak: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    features: List<Feature>,
    sectionNotes: Map<String, String>,
    onCopyLog: () -> Unit,
    onClearLog: () -> Unit,
    onSaveLog: () -> Unit = {},
    onStop: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("IPA Backup", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.device ?: "No device yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            item { StatusCard(state, onStop) }
            for ((section, items) in features.groupBy { it.section }) {
                item { SectionHeader(section, sectionNotes[section]) }
                items(items.chunked(3)) { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { f -> FeatureTile(f, enabled = !state.busy, modifier = Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item { ActivityLog(state, onCopyLog, onClearLog, onSaveLog, Modifier.height(320.dp)) }
        }
    }
}

@Composable
private fun StatusCard(state: UiState, onStop: () -> Unit) {
    val busy = state.busy
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (busy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.connected || busy) Icons.Outlined.PhoneIphone else Icons.Outlined.Usb,
                    contentDescription = null,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            busy -> state.task ?: "Working…"
                            state.device != null -> "Ready"
                            else -> "Connect your iPod"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            busy -> state.log.lastOrNull { it.isNotBlank() }?.trim() ?: ""
                            else -> "Plug the device into this phone with a USB OTG adapter, then choose what to back up."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AnimatedVisibility(busy) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
            AnimatedVisibility(state.stopVisible) {
                FilledTonalButton(
                    onClick = onStop,
                    enabled = state.stopEnabled,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) { Text(state.stopLabel) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, note: String?) {
    Row(Modifier.padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (note != null) {
            Spacer(Modifier.width(8.dp))
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeatureTile(f: Feature, enabled: Boolean, modifier: Modifier) {
    ElevatedCard(
        onClick = { if (enabled) f.onClick() },
        modifier = modifier
            .height(108.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(f.icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(22.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    f.title,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (f.needsJailbreak) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Needs a jailbroken device",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ActivityLog(state: UiState, onCopy: () -> Unit, onClear: () -> Unit, onSave: () -> Unit, modifier: Modifier) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Activity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onSave) { Icon(Icons.Outlined.Save, contentDescription = "Save log to Downloads") }
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy log") }
            IconButton(onClick = onClear) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear log") }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(state.log.size) {
            if (state.log.isNotEmpty()) listState.scrollToItem(state.log.size - 1)
        }
        SelectionContainer {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.log) { _, line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = when {
                            line.contains("FAILED") || line.startsWith("ERROR") -> MaterialTheme.colorScheme.error
                            line.contains("✓") -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
