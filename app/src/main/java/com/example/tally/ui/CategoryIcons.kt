package com.example.tally.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.tally.data.model.Kind

/** The icons a category can have, keyed by what's stored in [com.example.tally.data.model.Category.iconKey]. */
val CategoryIcons: Map<String, ImageVector> = linkedMapOf(
    "groceries" to Icons.Filled.LocalGroceryStore,
    "dining" to Icons.Filled.Restaurant,
    "coffee" to Icons.Filled.LocalCafe,
    "transport" to Icons.Filled.DirectionsCar,
    "shopping" to Icons.Filled.ShoppingBag,
    "bills" to Icons.Filled.Receipt,
    "home" to Icons.Filled.Home,
    "entertainment" to Icons.Filled.Movie,
    "health" to Icons.Filled.LocalHospital,
    "fitness" to Icons.Filled.FitnessCenter,
    "travel" to Icons.Filled.Flight,
    "pets" to Icons.Filled.Pets,
    "education" to Icons.Filled.School,
    "gifts" to Icons.Filled.CardGiftcard,
    "paycheck" to Icons.Filled.Work,
    "refund" to Icons.Filled.Replay,
    "savings" to Icons.Filled.Savings,
    "cash" to Icons.Filled.Payments,
    "other" to Icons.Filled.Category,
)

fun categoryIcon(key: String?): ImageVector =
    if (key == null) Icons.Filled.QuestionMark else CategoryIcons[key] ?: Icons.Filled.Category

val Kind.label: String
    get() = when (this) {
        Kind.EXPENSE -> "Expense"
        Kind.INCOME -> "Income"
    }
