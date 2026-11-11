package com.example.tally.capture

import com.example.tally.data.StarterCategories
import com.example.tally.data.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizerTest {
    private fun expense(merchant: String) = Categorizer.suggest(Kind.EXPENSE, merchant, null)

    @Test
    fun knownMerchants() {
        assertEquals(StarterCategories.DINING, expense("Starbucks"))
        assertEquals(StarterCategories.GROCERIES, expense("Whole Foods Market"))
        assertEquals(StarterCategories.TRANSPORT, expense("Shell"))
        assertEquals(StarterCategories.SHOPPING, expense("Target"))
        assertEquals(StarterCategories.ENTERTAINMENT, expense("Netflix.com"))
        assertEquals(StarterCategories.HEALTH, expense("CVS/Pharmacy"))
        assertEquals(StarterCategories.BILLS, expense("AT&T"))
        assertEquals(StarterCategories.TRAVEL, expense("Marriott Downtown"))
    }

    @Test
    fun uberEatsIsDiningButUberIsTransport() {
        assertEquals(StarterCategories.DINING, expense("Uber Eats"))
        assertEquals(StarterCategories.TRANSPORT, expense("Uber Trip"))
    }

    @Test
    fun keywordsMatchWholeWordsOnly() {
        // "bar" is a dining keyword; "Barnes" must not match it.
        assertNull(expense("Barnes"))
    }

    @Test
    fun unknownMerchantHasNoGuess() {
        assertNull(expense("Jordan Lee"))
        assertNull(Categorizer.suggest(Kind.EXPENSE, null, "You spent \$3.00"))
    }

    @Test
    fun incomeIsSortedByWording() {
        assertEquals(StarterCategories.REFUNDS, Categorizer.suggest(Kind.INCOME, "Amazon.com", "A refund of \$19.99"))
        assertEquals(StarterCategories.PAYCHECK, Categorizer.suggest(Kind.INCOME, null, "Direct deposit of \$1,250.00"))
        assertNull(Categorizer.suggest(Kind.INCOME, "Sam", "You received \$40 from Sam"))
    }

    @Test
    fun everySuggestionIsAStarterCategory() {
        val starters = StarterCategories.ALL.map { it.name }.toSet()
        assertTrue(Categorizer.categoryNames.all { it in starters })
    }

    @Test
    fun merchantKeyIgnoresCaseAndPunctuation() {
        assertEquals("bluedoorcafe", Categorizer.merchantKey("BLUE DOOR CAFE"))
        assertEquals(Categorizer.merchantKey("Blue Door Cafe"), Categorizer.merchantKey("blue-door cafe."))
    }
}
