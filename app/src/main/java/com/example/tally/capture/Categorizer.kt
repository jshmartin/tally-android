package com.example.tally.capture

import com.example.tally.data.StarterCategories
import com.example.tally.data.model.Kind

/**
 * Guesses a starter category from a merchant's name. A merchant rule the user has taught Tally
 * always wins over this; see [PurchaseRecorder.categoryFor].
 */
object Categorizer {
    // Checked in order, so "Uber Eats" is Dining before "Uber" can be Transport.
    private val EXPENSE_KEYWORDS: List<Pair<String, List<String>>> = listOf(
        StarterCategories.DINING to listOf(
            "starbucks", "coffee", "cafe", "café", "caffe", "espresso", "restaurant", "pizza", "pizzeria",
            "burger", "burgers", "mcdonald", "mcdonalds", "chipotle", "taco", "tacos", "taco bell", "doordash",
            "uber eats", "ubereats", "grubhub", "postmates", "dunkin", "subway", "wendy", "wendys", "kfc",
            "chick fil a", "panera", "domino", "dominos", "sushi", "diner", "bakery", "bistro", "grill",
            "kitchen", "tavern", "pub", "brewing", "brewery", "shake shack", "five guys", "popeyes",
            "sweetgreen", "in n out", "bar", "eatery", "noodle", "noodles", "ramen", "pho", "bbq", "deli",
            "bagel", "bagels", "donut", "donuts", "boba", "swiggy", "zomato",
        ),
        StarterCategories.GROCERIES to listOf(
            "grocery", "groceries", "supermarket", "market", "kroger", "safeway", "whole foods", "trader joe",
            "trader joes", "aldi", "costco", "publix", "wegmans", "heb", "h e b", "sprouts", "food lion",
            "instacart", "albertsons", "meijer", "giant", "fred meyer", "ralphs", "vons", "winco", "lidl",
            "sam s club", "sams club", "harris teeter", "stop shop",
        ),
        StarterCategories.TRANSPORT to listOf(
            "uber", "lyft", "shell", "chevron", "exxon", "exxonmobil", "mobil", "bp", "texaco", "arco", "sunoco",
            "valero", "citgo", "marathon", "speedway", "wawa", "sheetz", "quiktrip", "circle k", "gas", "fuel",
            "parking", "transit", "metro", "mta", "bart", "toll", "tolls", "ez pass", "ezpass", "amtrak",
            "greyhound", "zipcar", "hertz", "avis", "jiffy lube", "autozone", "car wash",
        ),
        StarterCategories.TRAVEL to listOf(
            "airbnb", "vrbo", "hotel", "hotels", "motel", "inn", "resort", "marriott", "hilton", "hyatt",
            "expedia", "booking com", "airlines", "airline", "airways", "southwest", "jetblue", "kayak",
            "priceline",
        ),
        StarterCategories.SHOPPING to listOf(
            "amazon", "amzn", "walmart", "target", "best buy", "ebay", "etsy", "ikea", "home depot", "lowe s",
            "lowes", "macy", "macys", "nordstrom", "tj maxx", "tjmaxx", "marshalls", "ross", "kohl", "kohls",
            "shein", "temu", "apple com", "apple store", "old navy", "gap", "h m", "zara", "uniqlo", "sephora",
            "ulta", "nike", "adidas", "wayfair", "dollar tree", "dollar general", "five below", "michaels",
            "staples", "office depot", "flipkart", "myntra",
        ),
        StarterCategories.BILLS to listOf(
            "comcast", "xfinity", "verizon", "at t", "att", "t mobile", "tmobile", "sprint", "spectrum", "cox",
            "optimum", "electric", "electricity", "energy", "power", "water", "utility", "utilities", "pg e",
            "con edison", "insurance", "geico", "progressive", "state farm", "allstate", "rent", "mortgage",
            "internet", "wireless", "google fi", "mint mobile",
        ),
        StarterCategories.ENTERTAINMENT to listOf(
            "netflix", "spotify", "hulu", "disney", "hbo", "youtube", "steam", "playstation", "xbox", "nintendo",
            "apple music", "amc", "cinema", "cinemas", "theatre", "theater", "movies", "regal", "ticketmaster",
            "stubhub", "eventbrite", "twitch", "audible", "kindle", "patreon", "paramount", "peacock",
            "crunchyroll",
        ),
        StarterCategories.HEALTH to listOf(
            "cvs", "walgreens", "rite aid", "pharmacy", "clinic", "dental", "dentist", "hospital", "doctor",
            "medical", "health", "urgent care", "optometry", "vision", "gym", "fitness", "planet fitness",
            "equinox", "yoga", "labcorp", "quest diagnostics",
        ),
    )

    private val EXPENSE_RULES: List<Pair<String, Regex>> = EXPENSE_KEYWORDS.map { (category, words) ->
        category to Regex("""\b(?:${words.joinToString("|")})\b""")
    }

    private val REFUND = Regex("""\b(?:refunds?|refunded|reversal|reversed|returns?|returned|chargeback)\b""")
    private val PAY = Regex("""\b(?:payroll|direct deposit|dir dep|salary|paycheck|wages)\b""")
    private val NOT_LETTER_OR_DIGIT = Regex("""[^\p{L}\p{N}]+""")

    /** Every category name this can return, for checking against the starter list. */
    val categoryNames: Set<String> =
        EXPENSE_KEYWORDS.map { it.first }.toSet() + setOf(StarterCategories.REFUNDS, StarterCategories.PAYCHECK)

    /** The name of the starter category that fits, or null when nothing does. */
    fun suggest(kind: Kind, merchant: String?, rawText: String?): String? = when (kind) {
        Kind.INCOME -> {
            val text = listOfNotNull(merchant, rawText).joinToString(" ").lowercase()
            when {
                REFUND.containsMatchIn(text) -> StarterCategories.REFUNDS
                PAY.containsMatchIn(text) -> StarterCategories.PAYCHECK
                else -> null
            }
        }
        Kind.EXPENSE -> {
            val name = merchant?.let(::normalize).orEmpty()
            if (name.isEmpty()) null else EXPENSE_RULES.firstOrNull { it.second.containsMatchIn(name) }?.first
        }
    }

    /** How merchant rules are keyed, so "Blue Door Cafe" and "BLUE DOOR CAFÉ" share one rule. */
    fun merchantKey(merchant: String): String = merchant.lowercase().filter { it.isLetterOrDigit() }

    private fun normalize(name: String): String = name.lowercase().replace(NOT_LETTER_OR_DIGIT, " ").trim()
}
