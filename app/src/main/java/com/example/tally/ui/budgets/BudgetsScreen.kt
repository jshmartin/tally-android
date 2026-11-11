package com.example.tally.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tally.data.model.Category
import com.example.tally.data.model.CategorySpending
import com.example.tally.data.model.Kind
import com.example.tally.ui.CategoryIcons
import com.example.tally.ui.categoryIcon
import com.example.tally.ui.components.BudgetBar
import com.example.tally.ui.components.CategoryBadge
import com.example.tally.ui.components.EmptyHint
import com.example.tally.ui.components.MoneyText
import com.example.tally.ui.components.MonthSwitcher
import com.example.tally.ui.components.SectionHeader
import com.example.tally.ui.components.budgetLine
import com.example.tally.ui.label
import com.example.tally.ui.theme.TabularNumbers
import com.example.tally.util.Money
import kotlinx.coroutines.launch

/** What the category dialog is open for. */
private sealed interface Editing {
    data class New(val kind: Kind) : Editing
    data class Existing(val category: Category) : Editing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = viewModel(factory = BudgetsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val open: (CategorySpending) -> Unit = { row ->
        scope.launch { viewModel.load(row.id)?.let { editing = Editing.Existing(it) } }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Budgets") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Editing.New(Kind.EXPENSE) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Category") },
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
            item { TotalCard(state) }
            item { SectionHeader("Spending") }
            items(state.expenses, key = { it.id }) { row -> ExpenseRow(row, onClick = { open(row) }) }
            if (state.uncategorizedCents > 0) {
                item(key = "uncategorized") {
                    ListItem(
                        leadingContent = { CategoryBadge(null, null) },
                        headlineContent = { Text("Uncategorized") },
                        supportingContent = { Text("Pick a category on these to count them against a budget") },
                        trailingContent = { MoneyText(state.uncategorizedCents) },
                    )
                }
            }
            item { SectionHeader("Income", action = "Add", onAction = { editing = Editing.New(Kind.INCOME) }) }
            if (state.loaded && state.income.isEmpty()) item { EmptyHint("No income categories.") }
            items(state.income, key = { it.id }) { row ->
                ListItem(
                    modifier = Modifier.clickable { open(row) },
                    leadingContent = { CategoryBadge(row.iconKey, row.color) },
                    headlineContent = { Text(row.name) },
                    trailingContent = { MoneyText(row.totalCents) },
                )
            }
        }
    }

    editing?.let { current ->
        val existing = (current as? Editing.Existing)?.category
        CategoryDialog(
            existing = existing,
            newKind = (current as? Editing.New)?.kind ?: Kind.EXPENSE,
            onDismiss = { editing = null },
            onSave = { name, kind, budget, icon ->
                viewModel.save(existing?.id, name, kind, budget, icon)
                editing = null
            },
            onDelete = existing?.let {
                {
                    editing = null
                    deleting = it
                }
            },
        )
    }

    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${category.name}?") },
            text = {
                Text(
                    "Its transactions stay, but become uncategorized. Merchant rules that file purchases " +
                        "under it are removed."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(category)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TotalCard(state: BudgetsState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (state.budgetCents > 0) {
                Text("Budgeted spending", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${Money.format(state.budgetedSpentCents)} of ${Money.formatShort(state.budgetCents)}",
                    style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                BudgetBar(state.budgetedSpentCents, state.budgetCents)
                Spacer(Modifier.height(6.dp))
                Text(budgetLine(state.budgetedSpentCents, state.budgetCents), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("No budgets yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap a category to give it a monthly budget. Tally shows how much is left as purchases come in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpenseRow(row: CategorySpending, onClick: () -> Unit) {
    val budget = row.monthlyBudgetCents
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { CategoryBadge(row.iconKey, row.color) },
        headlineContent = { Text(row.name) },
        supportingContent = {
            if (budget != null && budget > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    BudgetBar(row.totalCents, budget)
                    Text(budgetLine(row.totalCents, budget))
                }
            } else {
                Text("No budget")
            }
        },
        trailingContent = { MoneyText(row.totalCents) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    existing: Category?,
    newKind: Kind,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: Kind, budgetCents: Long?, iconKey: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: newKind) }
    var budget by rememberSaveable { mutableStateOf(existing?.monthlyBudgetCents?.let(Money::toInput).orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(existing?.iconKey ?: "other") }
    val budgetValid = budget.isBlank() || Money.parseInput(budget) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New category" else "Edit category") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (existing == null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Kind.entries.forEachIndexed { index, k ->
                            SegmentedButton(
                                selected = kind == k,
                                onClick = { kind = k },
                                shape = SegmentedButtonDefaults.itemShape(index, Kind.entries.size),
                            ) { Text(k.label) }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == Kind.EXPENSE) {
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { budget = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                        label = { Text("Monthly budget") },
                        placeholder = { Text("No budget") },
                        prefix = { Text(Money.symbol) },
                        isError = !budgetValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryIcons.keys.forEach { key ->
                        FilledTonalIconToggleButton(checked = icon == key, onCheckedChange = { icon = key }) {
                            Icon(categoryIcon(key), contentDescription = key)
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete category", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && budgetValid,
                onClick = {
                    onSave(name.trim(), kind, if (budget.isBlank()) null else Money.parseInput(budget), icon)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
