package com.example.tally.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tally.data.model.Kind
import com.example.tally.data.model.Source
import com.example.tally.ui.categoryIcon
import com.example.tally.ui.components.CaptureIcon
import com.example.tally.ui.label
import com.example.tally.ui.theme.TabularNumbers
import com.example.tally.util.Money
import com.example.tally.util.dayLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnEditScreen(
    onDone: () -> Unit,
    viewModel: TxnEditViewModel = viewModel(factory = TxnEditViewModel.Factory),
) {
    val form = viewModel.form
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val original = viewModel.original
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(viewModel.missing) { if (viewModel.missing) onDone() }
    LaunchedEffect(Unit) { if (viewModel.isNew) amountFocus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                },
                title = {
                    Text(
                        when {
                            viewModel.isNew -> "New transaction"
                            original?.needsReview == true -> "Review purchase"
                            else -> "Edit transaction"
                        }
                    )
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                    TextButton(onClick = { viewModel.save(onDone) }, enabled = viewModel.loaded) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (!viewModel.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Kind.entries.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = form.kind == kind,
                        onClick = { viewModel.setKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index, Kind.entries.size),
                    ) { Text(kind.label) }
                }
            }

            OutlinedTextField(
                value = form.amount,
                onValueChange = viewModel::setAmount,
                label = { Text("Amount") },
                prefix = { Text(Money.symbol) },
                textStyle = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                isError = form.amountError,
                supportingText = if (form.amountError) {
                    { Text("Enter an amount, like 12.50") }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus),
            )

            OutlinedTextField(
                value = form.merchant,
                onValueChange = viewModel::setMerchant,
                label = { Text(if (form.kind == Kind.INCOME) "From" else "Merchant or description") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { it.kind == form.kind }.forEach { category ->
                        val selected = form.categoryId == category.id
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.pickCategory(if (selected) null else category.id) },
                            label = { Text(category.name) },
                            leadingIcon = {
                                Icon(
                                    categoryIcon(category.iconKey),
                                    contentDescription = null,
                                    tint = Color(category.color),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }

            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(form.date.dayLabel())
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::setNote,
                label = { Text("Note") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (original?.source == Source.NOTIFICATION) {
                CapturedFrom(viewModel.sourceAppLabel, original?.sourceText)
            }
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this transaction?") },
            text = {
                Text(
                    if (original?.source == Source.NOTIFICATION) {
                        "It won't be captured again from the same notification."
                    } else {
                        "This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete(onDone)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CapturedFrom(appLabel: String?, text: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CaptureIcon(Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Captured from ${appLabel ?: "a notification"}", style = MaterialTheme.typography.titleSmall)
            }
            if (!text.isNullOrBlank()) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
