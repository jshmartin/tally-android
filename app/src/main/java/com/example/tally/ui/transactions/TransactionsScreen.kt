package com.example.tally.ui.transactions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tally.export.ExportPeriod
import com.example.tally.export.Xlsx
import com.example.tally.ui.components.CaptureIcon
import com.example.tally.ui.components.EmptyHint
import com.example.tally.ui.components.MoneyText
import com.example.tally.ui.components.MonthSwitcher
import com.example.tally.ui.components.TxnListItem
import com.example.tally.ui.theme.TabularNumbers
import com.example.tally.util.Money
import com.example.tally.util.dayLabel
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onReview: () -> Unit,
    viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var choosingPeriod by rememberSaveable { mutableStateOf(false) }
    // Saved, since Android may stop Tally while the save dialog is open.
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Xlsx.MIME_TYPE)) { uri ->
        val period = pendingExport?.let(ExportPeriod::fromKey)
        pendingExport = null
        if (uri != null && period != null) viewModel.export(period, uri)
    }

    LaunchedEffect(Unit) {
        viewModel.exports.collect { result ->
            when (result) {
                is ExportResult.Saved -> {
                    val shown = snackbar.showSnackbar(
                        "Saved ${result.count} ${if (result.count == 1) "transaction" else "transactions"}",
                        actionLabel = "Open",
                        duration = SnackbarDuration.Long,
                    )
                    if (shown == SnackbarResult.ActionPerformed && !openSpreadsheet(context, result.uri)) {
                        snackbar.showSnackbar("No app on this phone opens spreadsheets")
                    }
                }
                ExportResult.Failed -> snackbar.showSnackbar("Couldn't save the spreadsheet")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                actions = {
                    if (state.reviewCount > 0) {
                        IconButton(onClick = onReview) {
                            BadgedBox(badge = { Badge { Text("${state.reviewCount}") } }) {
                                CaptureIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export to spreadsheet") },
                                leadingIcon = { Icon(Icons.Outlined.TableView, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    choosingPeriod = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item { MonthSwitcher(state.month, viewModel::setMonth) }
            item {
                Text(
                    "Spent ${Money.format(state.totals.spentCents)} · Income ${Money.format(state.totals.incomeCents)}",
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.loaded && state.days.isEmpty()) {
                item { EmptyHint("No transactions this month.") }
            }
            state.days.forEach { day ->
                item(key = "d${day.date}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
                    ) {
                        Text(
                            day.date.dayLabel(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (day.spentCents > 0) {
                            MoneyText(day.spentCents, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(day.rows, key = { it.id }) { row ->
                    TxnListItem(row, onClick = { onOpen(row.id) })
                }
            }
        }
    }

    if (choosingPeriod) {
        ExportDialog(
            month = state.month,
            onDismiss = { choosingPeriod = false },
            onExport = { period ->
                choosingPeriod = false
                scope.launch {
                    // Checked first, so the user isn't asked where to save a file with nothing in it.
                    if (viewModel.exportCount(period) == 0) {
                        snackbar.showSnackbar(
                            when (period) {
                                is ExportPeriod.Month -> "No transactions in ${period.label}"
                                is ExportPeriod.Year -> "No transactions in ${period.year}"
                                ExportPeriod.Everything -> "No transactions yet"
                            }
                        )
                    } else {
                        pendingExport = period.key
                        saveFile.launch(period.fileName())
                    }
                }
            },
        )
    }
}

@Composable
private fun ExportDialog(month: YearMonth, onDismiss: () -> Unit, onExport: (ExportPeriod) -> Unit) {
    val options = listOf(ExportPeriod.Month(month), ExportPeriod.Year(month.year), ExportPeriod.Everything)
    var picked by rememberSaveable { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export to spreadsheet") },
        text = {
            Column {
                Text(
                    "Saves an Excel file that Google Sheets, Excel and Numbers can open, with a row for " +
                        "each transaction.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Column(Modifier.selectableGroup()) {
                    options.forEachIndexed { i, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = i == picked, onClick = { picked = i }, role = Role.RadioButton)
                                .padding(vertical = 10.dp),
                        ) {
                            RadioButton(selected = i == picked, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExport(options[picked]) }) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Returns false when no app can open it. */
private fun openSpreadsheet(context: Context, uri: Uri): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, Xlsx.MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
