package com.example.tally.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxTest {
    private fun write(sheet: Sheet): Map<String, String> {
        val bytes = ByteArrayOutputStream().also { Xlsx.write(sheet, it) }.toByteArray()
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return parts
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    /** The sheet's cells by reference ("B2"), each as its style and the value or text in it. */
    private fun cells(sheetXml: String): Map<String, Pair<String, String>> {
        val nodes = parse(sheetXml).getElementsByTagName("c")
        return (0 until nodes.length).associate { i ->
            val c = nodes.item(i) as Element
            c.getAttribute("r") to (c.getAttribute("s") to c.textContent)
        }
    }

    private val columns = listOf(Column("Date", 12), Column("Name", 20), Column("Amount", 12))

    @Test
    fun `writes every part a spreadsheet app needs, all well-formed`() {
        val parts = write(Sheet("Transactions", columns, listOf(listOf(Cell.Text("Coffee")))))
        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
            ),
            parts.keys,
        )
        parts.values.forEach { parse(it) }
    }

    @Test
    fun `stores dates, times and amounts as numbers`() {
        val sheet = Sheet(
            "Transactions",
            listOf(Column("Date", 12), Column("Time", 10), Column("Amount", 12), Column("Refund", 12)),
            listOf(
                listOf(
                    Cell.Date(LocalDate.of(2026, 11, 11)),
                    Cell.Time(LocalTime.of(12, 30)),
                    Cell.Amount(123_456),
                    Cell.Amount(-5),
                ),
            ),
        )
        val cells = cells(write(sheet).getValue("xl/worksheets/sheet1.xml"))
        // Excel numbers days from 1899-12-30.
        assertEquals("2" to "46337", cells["A2"])
        assertEquals("3" to "0.5208333333", cells["B2"])
        assertEquals("4" to "1234.56", cells["C2"])
        assertEquals("4" to "-0.05", cells["D2"])
    }

    @Test
    fun `header row is bold, frozen and filterable`() {
        val parts = write(Sheet("Transactions", columns, listOf(listOf(Cell.Text("a")), listOf(Cell.Text("b")))))
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        val cells = cells(sheet)
        assertEquals("1" to "Date", cells["A1"])
        assertEquals("1" to "Amount", cells["C1"])
        assertTrue(sheet.contains("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>"""))
        assertTrue(sheet.contains("""<autoFilter ref="A1:C3"/>"""))
        assertTrue(sheet.contains("""<dimension ref="A1:C3"/>"""))
        assertTrue(parts.getValue("xl/workbook.xml").contains("'Transactions'!\$A\$1:\$C\$3</definedName>"))
    }

    @Test
    fun `leaves empty cells out`() {
        val sheet = Sheet("Transactions", columns, listOf(listOf(Cell.Text("x"), Cell.Empty, Cell.Text(""))))
        val cells = cells(write(sheet).getValue("xl/worksheets/sheet1.xml"))
        assertEquals(setOf("A1", "B1", "C1", "A2"), cells.keys)
    }

    @Test
    fun `text that looks like a formula stays text`() {
        val sheet = Sheet("Transactions", columns, listOf(listOf(Cell.Text("=HYPERLINK(\"x\")"))))
        val xml = write(sheet).getValue("xl/worksheets/sheet1.xml")
        assertFalse(xml.contains("<f>"))
        assertEquals("" to "=HYPERLINK(\"x\")", cells(xml)["A2"])
    }

    @Test
    fun `escapes markup and drops characters XML can't hold`() {
        val text = "Tim & Jo's <Café> \"#4\"\u0007😀\uD83D end"
        val sheet = Sheet("Transactions", columns, listOf(listOf(Cell.Text(text))))
        val cells = cells(write(sheet).getValue("xl/worksheets/sheet1.xml"))
        assertEquals("Tim & Jo's <Café> \"#4\"😀 end", cells.getValue("A2").second)
    }

    @Test
    fun `keeps line breaks and outer spaces in text`() {
        val sheet = Sheet("Transactions", columns, listOf(listOf(Cell.Text("  two\nlines "))))
        assertEquals("  two\nlines ", cells(write(sheet).getValue("xl/worksheets/sheet1.xml")).getValue("A2").second)
    }

    @Test
    fun `cuts text at Excel's cell limit`() {
        val sheet = Sheet("Transactions", columns, listOf(listOf(Cell.Text("x".repeat(40_000)))))
        assertEquals(32_767, cells(write(sheet).getValue("xl/worksheets/sheet1.xml")).getValue("A2").second.length)
    }

    @Test
    fun `quotes the sheet name in the filter range`() {
        val workbook = write(Sheet("Jo's & co", columns, emptyList())).getValue("xl/workbook.xml")
        val doc = parse(workbook)
        assertEquals("Jo's & co", (doc.getElementsByTagName("sheet").item(0) as Element).getAttribute("name"))
        assertEquals("'Jo''s & co'!\$A\$1:\$C\$1", doc.getElementsByTagName("definedName").item(0).textContent)
    }

    @Test
    fun `names columns like a spreadsheet`() {
        assertEquals("A", Xlsx.columnName(0))
        assertEquals("Z", Xlsx.columnName(25))
        assertEquals("AA", Xlsx.columnName(26))
        assertEquals("AB", Xlsx.columnName(27))
        assertEquals("ZZ", Xlsx.columnName(701))
        assertEquals("AAA", Xlsx.columnName(702))
    }
}
