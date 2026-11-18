package com.example.tally.ui.settings

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tally.data.backup.BackupFrequency
import com.example.tally.data.backup.BackupStatus
import com.example.tally.data.backup.BackupSummary
import com.example.tally.data.backup.FolderWithBackup
import com.example.tally.ui.components.SectionHeader

private const val DATA_NOTICE =
    "Tally has no account and no cloud. Your transactions, categories, budgets and merchant rules are saved only " +
        "on this phone, so uninstalling the app, clearing its storage or losing the phone erases them."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message.text,
            actionLabel = "Undo".takeIf { message.canUndo },
            duration = if (message.canUndo) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        viewModel.messageShown()
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRestore()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let(viewModel::chooseFolder)
    }
    // Backup files have no standard type, and what they arrive as depends on how they were sent.
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickBackupFile)
    }
    val send = {
        viewModel.prepareShare { uri ->
            val intent = Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Send backup"))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SectionHeader("Automatic backup") }
                item {
                    AutomaticBackup(
                        status = state.status,
                        working = state.working,
                        onChooseFolder = { folderPicker.launch(null) },
                        onFrequency = viewModel::setFrequency,
                        onBackUpNow = viewModel::backUpNow,
                        onTurnOff = viewModel::turnOff,
                    )
                }
                item { SectionHeader("Restore") }
                item {
                    Block {
                        Body(
                            "Replace everything in the app with a backup file. Use this after reinstalling Tally or " +
                                "on a new phone. You can undo it straight after."
                        )
                        OutlinedButton(onClick = { backupPicker.launch(arrayOf("*/*")) }, enabled = !state.working) {
                            Text("Restore from a backup file")
                        }
                    }
                }
                item { SectionHeader("Move to a new phone") }
                item {
                    Block {
                        Step(1, "Send a backup to the new phone. Quick Share sends it straight across without the internet.")
                        OutlinedButton(onClick = send, enabled = !state.working) { Text("Send backup") }
                        Step(2, "On the new phone, install Tally, open Settings and tap Restore from a backup file.")
                        Step(3, "Turn on auto-capture there too. Notification access doesn't move with the backup.")
                    }
                }
            }
        }
    }

    state.folderWithBackup?.let { choice ->
        FolderWithBackupDialog(
            choice = choice,
            onRestore = viewModel::restoreFolderBackup,
            onReplace = viewModel::replaceFolderBackup,
            onCancel = viewModel::cancelFolderChoice,
        )
    }

    state.picked?.let { picked ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "It has ${describeBackup(picked.summary)}. Everything in the app now will be replaced. " +
                        "You can undo this straight after."
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomaticBackup(
    status: BackupStatus,
    working: Boolean,
    onChooseFolder: () -> Unit,
    onFrequency: (BackupFrequency) -> Unit,
    onBackUpNow: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Block {
        if (status.folder == null) {
            Body(DATA_NOTICE)
            Body(
                "Choose a folder, such as Documents, and Tally keeps a backup file there that it updates daily, " +
                    "weekly or monthly. The file stays even if the app is uninstalled."
            )
            Button(onClick = onChooseFolder, enabled = !working) { Text("Choose folder") }
            return@Block
        }
        Text("Backing up to ${status.folderName ?: "your chosen folder"}", style = MaterialTheme.typography.bodyLarge)
        val lastBackupAt = status.lastBackupAt
        when {
            status.failed -> Text(
                "The last backup didn't work. The folder may have been moved or deleted. Choose it again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            lastBackupAt != null -> Body("Last backup ${timeAgo(lastBackupAt)}")
        }
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            BackupFrequency.entries.forEachIndexed { index, frequency ->
                SegmentedButton(
                    selected = status.frequency == frequency,
                    onClick = { onFrequency(frequency) },
                    shape = SegmentedButtonDefaults.itemShape(index, BackupFrequency.entries.size),
                ) { Text(frequency.label) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBackUpNow, enabled = !working) { Text("Back up now") }
            TextButton(onClick = onChooseFolder, enabled = !working) { Text("Change folder") }
            TextButton(onClick = onTurnOff, enabled = !working) { Text("Turn off") }
        }
    }
}

@Composable
private fun FolderWithBackupDialog(
    choice: FolderWithBackup,
    onRestore: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    val existing = choice.existing
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("This folder already has a backup") },
        text = {
            Text(
                if (existing != null) {
                    "It has ${describeBackup(existing.summary)}. Restore it to bring that data into the app, " +
                        "or replace it with what's in the app now."
                } else {
                    "There's a backup file here that Tally can't read. Replace it with what's in the app now?"
                }
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = onReplace) { Text("Replace it") }
                if (existing != null) TextButton(onClick = onRestore) { Text("Restore it") }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Block(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Step(number: Int, text: String) {
    Row {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(24.dp))
        Body(text)
    }
}

private fun timeAgo(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < DateUtils.MINUTE_IN_MILLIS) {
        "just now"
    } else {
        DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

/** "120 transactions, 14 categories and 30 merchant rules". */
internal fun describeBackup(summary: BackupSummary): String =
    "${count(summary.transactions, "transaction")}, ${count(summary.categories, "category", "categories")} " +
        "and ${count(summary.rules, "merchant rule")}"

private fun count(n: Int, noun: String, plural: String = "${noun}s") = if (n == 1) "1 $noun" else "$n $plural"
