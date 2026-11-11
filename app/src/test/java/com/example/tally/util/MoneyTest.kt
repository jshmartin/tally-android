package com.example.tally.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesWhatPeopleType() {
        assertEquals(1200L, Money.parseInput("12"))
        assertEquals(1230L, Money.parseInput("12.3"))
        assertEquals(1234L, Money.parseInput("12.34"))
        assertEquals(123456L, Money.parseInput("1,234.56"))
        assertEquals(500L, Money.parseInput("\$5"))
        assertEquals(50L, Money.parseInput(".5"))
    }

    @Test
    fun commaCanBeTheDecimalSeparator() {
        assertEquals(1250L, Money.parseInput("12,50"))
        assertEquals(123456L, Money.parseInput("1.234,56"))
        assertEquals(123400L, Money.parseInput("1,234"))
    }

    @Test
    fun rejectsWhatIsNotAnAmount() {
        assertNull(Money.parseInput(""))
        assertNull(Money.parseInput("abc"))
        assertNull(Money.parseInput("."))
        assertNull(Money.parseInput("12.345"))
        assertNull(Money.parseInput("1.2.3"))
    }

    @Test
    fun editTextRoundTrips() {
        assertEquals("12", Money.toInput(1200))
        assertEquals("12.50", Money.toInput(1250))
        assertEquals(1250L, Money.parseInput(Money.toInput(1250)))
    }
}
