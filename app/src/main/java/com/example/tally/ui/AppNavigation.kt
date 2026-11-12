package com.example.tally.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tally.data.model.Kind
import com.example.tally.ui.automation.AutomationScreen
import com.example.tally.ui.budgets.BudgetsScreen
import com.example.tally.ui.overview.OverviewScreen
import com.example.tally.ui.review.ReviewScreen
import com.example.tally.ui.transactions.TransactionsScreen
import com.example.tally.ui.transactions.TxnEditScreen

object Routes {
    const val OVERVIEW = "overview"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val AUTOMATION = "automation"
    const val REVIEW = "review"
    const val EDIT = "transaction?id={id}&kind={kind}"

    /** A null [id] adds a new transaction of [kind]. */
    fun edit(id: Long? = null, kind: Kind = Kind.EXPENSE) = "transaction?id=${id ?: -1}&kind=${kind.name}"
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Overview(Routes.OVERVIEW, "Overview", Icons.Filled.Home),
    Activity(Routes.TRANSACTIONS, "Activity", Icons.AutoMirrored.Filled.ReceiptLong),
    Budgets(Routes.BUDGETS, "Budgets", Icons.Filled.PieChart),
    // "Auto-capture" wraps in a quarter-width slot; the screen's own title keeps the full name.
    Capture(Routes.AUTOMATION, "Capture", Icons.Filled.Bolt),
}

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Review and the edit form are full-screen tasks, so the tabs step aside for them.
    val showTabs = Tab.entries.any { it.route == currentRoute }

    // Each screen's own top bar handles the status bar inset; the bottom bar handles the rest.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Routes.OVERVIEW,
            // Consumed so a screen's imePadding only adds what the keyboard covers beyond the bottom bar.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    onAdd = { nav.navigate(Routes.edit()) },
                    onOpen = { nav.navigate(Routes.edit(it)) },
                    onReview = { nav.navigate(Routes.REVIEW) },
                    onSeeAll = { nav.openTab(Routes.TRANSACTIONS) },
                    onBudgets = { nav.openTab(Routes.BUDGETS) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onAdd = { nav.navigate(Routes.edit()) },
                    onOpen = { nav.navigate(Routes.edit(it)) },
                    onReview = { nav.navigate(Routes.REVIEW) },
                )
            }
            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.AUTOMATION) {
                AutomationScreen(onReview = { nav.navigate(Routes.REVIEW) })
            }
            composable(Routes.REVIEW) {
                ReviewScreen(
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(Routes.edit(it)) },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("kind") {
                        type = NavType.StringType
                        defaultValue = Kind.EXPENSE.name
                    },
                ),
            ) {
                TxnEditScreen(onDone = { nav.popBackStack() })
            }
        }
    }
}
