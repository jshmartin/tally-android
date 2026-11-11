package com.example.tally.ui.automation

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tally.capture.NotificationAccess
import com.example.tally.capture.rememberNotificationAccess
import com.example.tally.ui.components.CaptureIcon
import com.example.tally.ui.components.CategoryBadge
import com.example.tally.ui.components.EmptyHint
import com.example.tally.ui.components.SectionHeader
import com.example.tally.ui.label
import com.example.tally.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onReview: () -> Unit,
    viewModel: AutomationViewModel = viewModel(factory = AutomationViewModel.Factory),
) {
    val context = LocalContext.current
    val captureOn = rememberNotificationAccess()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val reviewCount by viewModel.reviewCount.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Auto-capture") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                StatusCard(
                    on = captureOn,
                    onOpenSettings = { NotificationAccess.openSettings(context) },
                    onOpenAppInfo = { NotificationAccess.openAppInfo(context) },
                )
            }
            if (reviewCount > 0) {
                item {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onReview),
                        leadingContent = { CaptureIcon() },
                        headlineContent = { Text("Review captured purchases") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge { Text("$reviewCount") }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        },
                    )
                }
            }

            item { SectionHeader("Apps") }
            if (apps.isEmpty()) {
                item {
                    EmptyHint(
                        "Apps show up here after Tally captures a purchase from one. Turn an app off and Tally " +
                            "stops reading its notifications."
                    )
                }
            }
            items(apps, key = { it.packageName }) { app ->
                ListItem(
                    headlineContent = { Text(app.label) },
                    supportingContent = { Text(if (app.capturedCount == 1) "1 captured" else "${app.capturedCount} captured") },
                    trailingContent = {
                        Switch(checked = app.enabled, onCheckedChange = { viewModel.setAppEnabled(app, it) })
                    },
                )
            }

            item { SectionHeader("Merchant rules") }
            item {
                EmptyHint(
                    if (rules.isEmpty()) {
                        "When you change a purchase's category, Tally remembers it for that merchant and files the " +
                            "next purchase there the same way."
                    } else {
                        "Purchases at these merchants get these categories automatically."
                    }
                )
            }
            items(rules, key = { it.merchantKey }) { rule ->
                ListItem(
                    leadingContent = { CategoryBadge(rule.iconKey, rule.color) },
                    headlineContent = { Text(rule.merchant) },
                    supportingContent = { Text(rule.categoryName) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.deleteRule(rule) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Forget ${rule.merchant}")
                        }
                    },
                )
            }

            item { SectionHeader("Try a notification") }
            item {
                Tester(
                    input = viewModel.testInput,
                    onInput = viewModel::onTestInputChange,
                    result = testResult,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(on: Boolean, onOpenSettings: () -> Unit, onOpenAppInfo: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (on) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    CaptureIcon()
                }
                Spacer(Modifier.width(10.dp))
                Text(if (on) "Auto-capture is on" else "Auto-capture is off", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                if (on) {
                    "New purchases, payments and deposits from your notifications are added for you, and wait in " +
                        "Review until you've checked them."
                } else {
                    "Tally can read the alerts your bank, card and payment apps send, pick out purchases, payments " +
                        "and deposits, and add them for you. It only keeps the ones that look like money moving, and " +
                        "nothing leaves your phone."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (on) {
                TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) {
                    Text("Notification access settings")
                }
            } else {
                Button(onClick = onOpenSettings) { Text("Turn on in Settings") }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        "If the switch is greyed out, open App info, tap ⋮ and choose Allow restricted settings, " +
                            "then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenAppInfo, contentPadding = PaddingValues(0.dp)) { Text("Open App info") }
                }
            }
        }
    }
}

@Composable
private fun Tester(input: String, onInput: (String) -> Unit, result: TestResult) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            label = { Text("Notification text") },
            placeholder = { Text("You spent \$12.34 at Starbucks") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        when (result) {
            TestResult.Empty -> Text(
                "Paste an alert from your bank to see what Tally would record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TestResult.NotAPurchase -> Text(
                "Tally would skip this. It looks for an amount and a word like spent, purchase, paid or received, " +
                    "and skips promotions, declines and bills that are due.",
                style = MaterialTheme.typography.bodyMedium,
            )
            is TestResult.Purchase -> {
                val p = result.preview.purchase
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${p.kind.label} · ${Money.format(p.amountCents)}", style = MaterialTheme.typography.titleMedium)
                        Text("Merchant: ${p.merchant ?: "not found"}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Category: ${result.preview.categoryName ?: "none yet"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
