package com.kenfenheuer.inventur.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kenfenheuer.inventur.MainActivity
import com.kenfenheuer.inventur.data.CsvExporter
import com.kenfenheuer.inventur.data.ScanEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val rowTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)

const val COPYRIGHT_NOTICE = "© 2026 Olaf Kenfenheuer"

private fun Context.findMainActivity(): MainActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is MainActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onOpenScanner: () -> Unit,
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val lastScanned by viewModel.lastScanned.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<ScanEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanEntry?>(null) }

    val duplicates = remember(items) { viewModel.duplicateBarcodes(items) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventur") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = onOpenScanner) {
                        Icon(Icons.Filled.Settings, contentDescription = "Scanner verbinden")
                    }
                    IconButton(
                        enabled = items.isNotEmpty(),
                        onClick = {
                            val file = CsvExporter.writeCsv(context, items)
                            context.startActivity(CsvExporter.shareIntent(context, file))
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Als CSV teilen")
                    }
                    IconButton(
                        enabled = items.isNotEmpty(),
                        onClick = { showClearDialog = true },
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Liste leeren")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Über die App") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Info, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showAboutDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showManualDialog = true },
                icon = { Icon(Icons.Filled.Keyboard, contentDescription = null) },
                text = { Text("Nummer eingeben") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SummaryBar(
                scans = items.size,
                duplicates = duplicates.size,
                lastScanned = lastScanned,
            )

            if (items.isEmpty()) {
                EmptyHint()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Unten extra Platz, damit der letzte Eintrag ueber den FAB
                    // gescrollt werden kann und dessen Buttons nicht verdeckt werden.
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { entry ->
                        ScanRow(
                            entry = entry,
                            isDuplicate = entry.barcode in duplicates,
                            onEditNote = { noteTarget = entry },
                            onDelete = { deleteTarget = entry },
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Liste leeren?") },
            text = { Text("Alle erfassten Scans werden unwiderruflich entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                    Toast.makeText(context, "Inventurliste geleert", Toast.LENGTH_SHORT).show()
                }) { Text("Leeren") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showManualDialog) {
        ManualEntryDialog(
            onDismiss = { showManualDialog = false },
            onSave = { barcode ->
                viewModel.addScan(barcode)
                showManualDialog = false
                Toast.makeText(context, "Inventarnummer erfasst", Toast.LENGTH_SHORT).show()
            },
        )
    }

    noteTarget?.let { target ->
        NoteDialog(
            entry = target,
            onDismiss = { noteTarget = null },
            onSave = { note ->
                viewModel.setNote(target.id, note)
                noteTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Scan löschen?") },
            text = { Text("Inventarnummer „${target.barcode}“ wird aus der Liste entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(target.id)
                    deleteTarget = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun SummaryBar(scans: Int, duplicates: Int, lastScanned: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Scans: $scans", style = MaterialTheme.typography.titleMedium)
                if (duplicates > 0) {
                    Text(
                        "Duplikate: $duplicates",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = if (lastScanned != null) "Zuletzt: $lastScanned" else "Bereit zum Scannen …",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Noch keine Inventarnummern erfasst", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Text(
                "Scanne eine Inventarnummer mit dem gekoppelten BCST-47.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Tipp: langes Tippen auf einen Scan fügt eine Bemerkung hinzu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanRow(
    entry: ScanEntry,
    isDuplicate: Boolean,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onEditNote),
        colors = if (isDuplicate) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.barcode,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isDuplicate) {
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Doppelt gescannt",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = rowTimeFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.note.isNotEmpty()) {
                    Spacer(Modifier.size(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = entry.note,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
            IconButton(onClick = onEditNote) {
                Icon(
                    Icons.Filled.EditNote,
                    contentDescription = "Bemerkung bearbeiten",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Scan loeschen",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
        title = { Text("Inventur") },
        text = {
            Column {
                if (versionName.isNotEmpty()) {
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    text = COPYRIGHT_NOTICE,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Alle Rechte vorbehalten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // HID-Scan-Erfassung pausieren, damit die Bildschirmtastatur das Eingabefeld
    // erreicht und Tastendruecke nicht als Scan interpretiert werden.
    DisposableEffect(Unit) {
        val activity = context.findMainActivity()
        activity?.scanCaptureEnabled = false
        onDispose { activity?.scanCaptureEnabled = true }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val save = { if (text.isNotBlank()) onSave(text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inventarnummer eingeben") },
        text = {
            Column {
                Text(
                    text = "Falls ein Barcode nicht lesbar ist, kannst du die " +
                        "Inventarnummer hier von Hand erfassen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Inventarnummer") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = text.isNotBlank()) { Text("Erfassen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun NoteDialog(
    entry: ScanEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(entry.note) }

    // HID-Scan-Erfassung waehrend der Texteingabe pausieren, damit die Tastatur
    // das Eingabefeld erreicht und nicht als Scan interpretiert wird.
    DisposableEffect(Unit) {
        val activity = context.findMainActivity()
        activity?.scanCaptureEnabled = false
        onDispose { activity?.scanCaptureEnabled = true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bemerkung") },
        text = {
            Column {
                Text(
                    text = entry.barcode,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Notiz zu diesem Scan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
