package com.example.tally.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyTextTest {
    private val lines = "CIBC Dividend Visa Card, 1234\n CANADIAN TIRE #608\n \$4.47"
    private val runTogether = "CIBC Dividend Visa Card, 1234 CANADIAN TIRE #608 \$4.47"

    @Test
    fun expandedTextWinsWhenItSaysMore() =
        assertEquals("You spent \$4.47 at Canadian Tire", bodyText("You spent \$4.47…", "You spent \$4.47 at Canadian Tire"))

    @Test
    fun shortTextWinsWhenOnlyItKeptTheLineBreaks() = assertEquals(lines, bodyText(lines, runTogether))

    @Test
    fun expandedTextWinsWhenItKeptTheLineBreaks() = assertEquals(lines, bodyText(runTogether, lines))

    @Test
    fun eitherOneAloneIsUsed() {
        assertEquals(lines, bodyText(lines, null))
        assertEquals(lines, bodyText(null, lines))
        assertNull(bodyText(null, null))
    }
}
