package com.example.tally.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tally.data.model.Kind
import com.example.tally.data.model.Source
import com.example.tally.data.model.TxnRow
import com.example.tally.ui.categoryIcon
import com.example.tally.ui.theme.TabularNumbers
import com.example.tally.util.Money
import com.example.tally.util.label
import com.example.tally.util.timeLabel
import java.time.YearMonth

@Composable
fun MonthSwitcher(month: YearMonth, onMonthChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = month.label(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = month < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** A category's icon in a tinted circle. A null [iconKey] means uncategorized. */
@Composable
fun CategoryBadge(iconKey: String?, color: Int?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val tint = color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(iconKey), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Expenses are plain; income gets a plus and the brand color. */
@Composable
fun AmountText(
    cents: Long,
    kind: Kind,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Text(
        text = if (kind == Kind.INCOME) "+${Money.format(cents)}" else Money.format(cents),
        style = style.merge(TabularNumbers),
        fontWeight = FontWeight.Medium,
        color = if (kind == Kind.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        maxLines = 1,
    )
}

/** An amount in the surrounding text style, with even-width digits. */
@Composable
fun MoneyText(cents: Long, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(Money.format(cents), style = LocalTextStyle.current.merge(TabularNumbers), color = color, modifier = modifier)
}

val TxnRow.title: String
    get() = merchant.ifBlank { categoryName ?: if (kind == Kind.INCOME) "Income" else "Expense" }

@Composable
fun TxnListItem(row: TxnRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { CategoryBadge(row.categoryIcon, row.categoryColor) },
        headlineContent = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (row.source == Source.NOTIFICATION) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = "Captured from a notification",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = listOf(row.categoryName ?: "Uncategorized", row.occurredAt.timeLabel()).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.needsReview) {
                    Text("· To review", color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                }
            }
        },
        trailingContent = { AmountText(row.amountCents, row.kind) },
    )
}

/** Green while there's room, amber from 80%, red when over. */
@Composable
fun BudgetBar(
    spentCents: Long,
    budgetCents: Long,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val fraction = if (budgetCents <= 0) 0f else (spentCents.toFloat() / budgetCents).coerceIn(0f, 1f)
    val color = when {
        spentCents > budgetCents -> MaterialTheme.colorScheme.error
        spentCents * 10 >= budgetCents * 8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** "$40 left of $400" or "$12 over $400". */
fun budgetLine(spentCents: Long, budgetCents: Long): String =
    if (spentCents <= budgetCents) {
        "${Money.format(budgetCents - spentCents)} left of ${Money.formatShort(budgetCents)}"
    } else {
        "${Money.format(spentCents - budgetCents)} over ${Money.formatShort(budgetCents)}"
    }

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun CaptureIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(Icons.Filled.Bolt, contentDescription = null, tint = tint, modifier = modifier)
}
