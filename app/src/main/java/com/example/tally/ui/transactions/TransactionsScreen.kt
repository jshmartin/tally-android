package com.example.tally.ui.transactions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tally.ui.components.CaptureIcon
import com.example.tally.ui.components.EmptyHint
import com.example.tally.ui.components.MoneyText
import com.example.tally.ui.components.MonthSwitcher
import com.example.tally.ui.components.TxnListItem
import com.example.tally.ui.theme.TabularNumbers
import com.example.tally.util.Money
import com.example.tally.util.dayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onReview: () -> Unit,
    viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                },
            )
        },
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
}
