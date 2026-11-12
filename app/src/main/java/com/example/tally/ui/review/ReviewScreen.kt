package com.example.tally.ui.review

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tally.data.model.Category
import com.example.tally.data.model.TxnRow
import com.example.tally.ui.categoryIcon
import com.example.tally.ui.components.AmountText
import com.example.tally.ui.components.CategoryBadge
import com.example.tally.ui.components.title
import com.example.tally.util.dateTimeLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.Factory),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val count = rows?.size ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(if (count > 0) "Review ($count)" else "Review") },
                actions = {
                    if (count > 1) TextButton(onClick = viewModel::keepAll) { Text("Keep all") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = rows ?: return@Scaffold
        if (list.isEmpty()) {
            AllCaughtUp(Modifier.padding(padding))
            return@Scaffold
        }
        // A purchase captured while this screen is open lands above the first card; bring it into view
        // unless the user has scrolled down the list.
        LaunchedEffect(list.first().id) {
            if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(list, key = { it.id }) { row ->
                ReviewCard(
                    row = row,
                    categories = categories.filter { it.kind == row.kind },
                    onCategory = { viewModel.setCategory(row, it) },
                    onKeep = { viewModel.keep(row) },
                    onDiscard = {
                        scope.launch {
                            val txn = viewModel.discard(row) ?: return@launch
                            val result = snackbar.showSnackbar(
                                "Discarded ${row.title}",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(txn)
                        }
                    },
                    onOpen = { onOpen(row.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(
    row: TxnRow,
    categories: List<Category>,
    onCategory: (Long) -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(row.categoryIcon, row.categoryColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.merchant.ifBlank { "Unknown merchant" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${row.sourceAppLabel ?: "Notification"} · ${row.occurredAt.dateTimeLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                AmountText(row.amountCents, row.kind, style = MaterialTheme.typography.titleMedium)
            }
            if (!row.counted) {
                Text(
                    "Not counted until you keep a purchase from ${row.sourceAppLabel ?: "this app"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (!row.sourceText.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        row.sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMenu(row, categories, onCategory)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDiscard) { Text("Discard") }
                FilledTonalButton(onClick = onKeep) { Text("Keep") }
            }
        }
    }
}

@Composable
private fun CategoryMenu(row: TxnRow, categories: List<Category>, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(row.categoryName ?: "Category", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    leadingIcon = { Icon(categoryIcon(category.iconKey), contentDescription = null, tint = Color(category.color)) },
                    onClick = {
                        open = false
                        onPick(category.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AllCaughtUp(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text("All caught up", style = MaterialTheme.typography.titleMedium)
        Text(
            "New purchases from your notifications will wait here until you've checked them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
