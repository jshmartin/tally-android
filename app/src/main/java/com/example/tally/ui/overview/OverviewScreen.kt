package com.example.tally.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tally.capture.NotificationAccess
import com.example.tally.capture.rememberNotificationAccess
import com.example.tally.data.model.CategorySpending
import com.example.tally.ui.components.BudgetBar
import com.example.tally.ui.components.CaptureIcon
import com.example.tally.ui.components.CategoryBadge
import com.example.tally.ui.components.EmptyHint
import com.example.tally.ui.components.MoneyText
import com.example.tally.ui.components.MonthSwitcher
import com.example.tally.ui.components.SectionHeader
import com.example.tally.ui.components.TxnListItem
import com.example.tally.ui.components.budgetLine
import com.example.tally.ui.theme.TabularNumbers
import com.example.tally.util.Money
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onReview: () -> Unit,
    onSeeAll: () -> Unit,
    onBudgets: () -> Unit,
    viewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val captureOn = rememberNotificationAccess()
    val context = LocalContext.current
    val monthName = state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tally") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { MonthSwitcher(state.month, viewModel::setMonth) }
            item { SummaryCard(state) }
            if (!captureOn) {
                item { AutoCaptureCard(onTurnOn = { NotificationAccess.openSettings(context) }) }
            }
            if (state.reviewCount > 0) {
                item { ReviewCard(state.reviewCount, onReview) }
            }
            if (state.topCategories.isNotEmpty() || state.uncategorizedCents > 0) {
                item { SectionHeader("Where it went", action = "Budgets", onAction = onBudgets) }
                items(state.topCategories, key = { "c${it.id}" }) { CategoryRow(it) }
                if (state.uncategorizedCents > 0) {
                    item(key = "uncategorized") {
                        ListItem(
                            leadingContent = { CategoryBadge(null, null) },
                            headlineContent = { Text("Uncategorized") },
                            trailingContent = { MoneyText(state.uncategorizedCents) },
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    "Recent",
                    action = if (state.transactionCount > state.recent.size) "See all" else null,
                    onAction = onSeeAll,
                )
            }
            if (state.loaded && state.recent.isEmpty()) {
                item {
                    EmptyHint(
                        if (captureOn) "Nothing in $monthName yet. Purchases from your notifications will show up here."
                        else "Nothing in $monthName yet. Add a transaction, or turn on auto-capture."
                    )
                }
            }
            items(state.recent, key = { "t${it.id}" }) { TxnListItem(it, onClick = { onOpen(it.id) }) }
        }
    }
}

@Composable
private fun SummaryCard(state: OverviewState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Spent", style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(state.totals.spentCents),
                style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                fontWeight = FontWeight.SemiBold,
            )
            if (state.budgetCents > 0) {
                Spacer(Modifier.height(12.dp))
                BudgetBar(
                    state.budgetedSpentCents,
                    state.budgetCents,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${budgetLine(state.budgetedSpentCents, state.budgetCents)} budgeted",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Stat("Income", "+${Money.format(state.totals.incomeCents)}", Modifier.weight(1f))
                Stat("Net", Money.format(state.totals.incomeCents - state.totals.spentCents), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AutoCaptureCard(onTurnOn: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            CaptureIcon(Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Capture purchases automatically", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tally can read the alerts your bank, card and payment apps send, and add each purchase for you. " +
                        "Nothing leaves your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onTurnOn) { Text("Turn on") }
            }
        }
    }
}

@Composable
private fun ReviewCard(count: Int, onReview: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        onClick = onReview,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CaptureIcon(Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 captured purchase to review" else "$count captured purchases to review",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("Check the amount and category, then keep it.", style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onClick = onReview) { Text("Review") }
        }
    }
}

@Composable
private fun CategoryRow(category: CategorySpending) {
    val budget = category.monthlyBudgetCents
    ListItem(
        leadingContent = { CategoryBadge(category.iconKey, category.color) },
        headlineContent = { Text(category.name) },
        supportingContent = if (budget != null && budget > 0) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    BudgetBar(category.totalCents, budget)
                    Text(budgetLine(category.totalCents, budget))
                }
            }
        } else {
            null
        },
        trailingContent = { MoneyText(category.totalCents) },
    )
}
