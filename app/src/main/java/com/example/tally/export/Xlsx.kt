package com.example.tally.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One cell of a [Sheet]. Text, amounts, dates and times are stored as their own types, so they sort and add up. */
sealed interface Cell {
    data class Text(val value: String) : Cell

    /** Shown with two decimals and a thousands separator. */
    data class Amount(val cents: Long) : Cell

    data class Date(val date: LocalDate) : Cell

    data class Time(val time: LocalTime) : Cell

    data object Empty : Cell
}

/** [width] is in characters. */
data class Column(val header: String, val width: Int)

data class Sheet(val name: String, val columns: List<Column>, val rows: List<List<Cell>>) {
    init {
        require(columns.isNotEmpty()) { "A sheet needs at least one column" }
        require(rows.all { it.size <= columns.size }) { "A row has more cells than there are columns" }
    }
}

/**
 * Writes an Excel workbook (.xlsx) with one sheet: a bold header row that stays put while scrolling,
 * with filters on it. Excel, Google Sheets, Numbers and LibreOffice all open it.
 *
 * It's the smallest package those apps accept: the workbook, the sheet and its styles. Text goes in
 * each cell rather than a shared-strings table. Since the file has no formula parts, text that starts
 * with "=" stays text, which a CSV can't promise.
 */
object Xlsx {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Excel stops a cell at this many characters. */
    private const val MAX_CELL_CHARS = 32_767

    /** Excel's serial day number for 1970-01-01. Day numbers count from 1899-12-30. */
    private const val EPOCH_SERIAL = 25_569L

    // Indexes into the cellXfs list in STYLES.
    private const val STYLE_HEADER = 1
    private const val STYLE_DATE = 2
    private const val STYLE_TIME = 3
    private const val STYLE_AMOUNT = 4

    /** Writes [sheet] to [out] and closes it. */
    fun write(sheet: Sheet, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            // Not closed: closing it would close the zip. Each part is flushed instead.
            val writer = OutputStreamWriter(zip, Charsets.UTF_8).buffered()
            fun part(name: String, content: (Appendable) -> Unit) {
                zip.putNextEntry(ZipEntry(name))
                content(writer)
                writer.flush()
                zip.closeEntry()
            }
            part("[Content_Types].xml") { it.append(CONTENT_TYPES) }
            part("_rels/.rels") { it.append(ROOT_RELS) }
            part("xl/workbook.xml") { it.append(workbook(sheet)) }
            part("xl/_rels/workbook.xml.rels") { it.append(WORKBOOK_RELS) }
            part("xl/styles.xml") { it.append(STYLES) }
            part("xl/worksheets/sheet1.xml") { writeSheet(sheet, it) }
        }
    }

    private fun workbook(sheet: Sheet): String {
        val name = escape(sheet.name)
        // Excel keeps a filter's range as this hidden name, and writes it whenever a sheet has filters.
        val filterRange = escape("'${sheet.name.replace("'", "''")}'!${absolute(usedRange(sheet))}")
        return XML_DECLARATION +
            """<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS">""" +
            """<bookViews><workbookView/></bookViews>""" +
            """<sheets><sheet name="$name" sheetId="1" r:id="rId1"/></sheets>""" +
            """<definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">$filterRange</definedName></definedNames>""" +
            """</workbook>"""
    }

    private fun writeSheet(sheet: Sheet, out: Appendable) {
        val range = usedRange(sheet)
        out.append(XML_DECLARATION)
        out.append("""<worksheet xmlns="$MAIN_NS">""")
        out.append("""<dimension ref="$range"/>""")
        out.append(
            """<sheetViews><sheetView workbookViewId="0">""" +
                """<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""" +
                """<selection pane="bottomLeft" activeCell="A2" sqref="A2"/>""" +
                """</sheetView></sheetViews>"""
        )
        out.append("""<sheetFormatPr defaultRowHeight="15"/>""")
        out.append("<cols>")
        sheet.columns.forEachIndexed { i, column ->
            out.append("""<col min="${i + 1}" max="${i + 1}" width="${column.width}" customWidth="1"/>""")
        }
        out.append("</cols>")
        out.append("<sheetData>")
        writeRow(1, sheet.columns.map { Cell.Text(it.header) }, out, header = true)
        sheet.rows.forEachIndexed { i, cells -> writeRow(i + 2, cells, out, header = false) }
        out.append("</sheetData>")
        out.append("""<autoFilter ref="$range"/>""")
        out.append("</worksheet>")
    }

    private fun writeRow(number: Int, cells: List<Cell>, out: Appendable, header: Boolean) {
        out.append("""<row r="$number">""")
        cells.forEachIndexed { i, cell ->
            val ref = columnName(i) + number
            when (cell) {
                is Cell.Text -> if (cell.value.isNotEmpty()) {
                    val style = if (header) """ s="$STYLE_HEADER"""" else ""
                    out.append("""<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">""")
                    out.append(escape(cell.value.limitLength()))
                    out.append("</t></is></c>")
                }
                is Cell.Amount ->
                    out.append("""<c r="$ref" s="$STYLE_AMOUNT"><v>${BigDecimal.valueOf(cell.cents, 2).toPlainString()}</v></c>""")
                is Cell.Date ->
                    out.append("""<c r="$ref" s="$STYLE_DATE"><v>${cell.date.toEpochDay() + EPOCH_SERIAL}</v></c>""")
                is Cell.Time -> {
                    // A time is the fraction of a day that has passed.
                    val fraction = BigDecimal(cell.time.toSecondOfDay())
                        .divide(BigDecimal(86_400), 10, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                    out.append("""<c r="$ref" s="$STYLE_TIME"><v>$fraction</v></c>""")
                }
                Cell.Empty -> Unit
            }
        }
        out.append("</row>")
    }

    /** "A1:I43": the header and every row, across every column. */
    private fun usedRange(sheet: Sheet) = "A1:${columnName(sheet.columns.lastIndex)}${sheet.rows.size + 1}"

    /** "A1:I43" as "$A$1:$I$43". */
    private fun absolute(range: String) = range.split(':').joinToString(":") { ref ->
        val letters = ref.takeWhile { it.isLetter() }
        "\$$letters\$${ref.drop(letters.length)}"
    }

    /** 0 is "A", 25 is "Z", 26 is "AA". */
    internal fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            name.append('A' + (n - 1) % 26)
            n = (n - 1) / 26
        }
        return name.reverse().toString()
    }

    private fun String.limitLength(): String {
        if (length <= MAX_CELL_CHARS) return this
        val cut = take(MAX_CELL_CHARS)
        return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    /**
     * Escapes [text] for XML, and drops the characters XML can't hold at all: control characters
     * other than tab and line breaks, and halves of broken emoji. Notification text can carry either.
     */
    internal fun escape(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> append(ch)
                ch < ' ' || ch == '￾' || ch == '￿' -> Unit
                ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                    append(ch).append(text[i + 1])
                    i++
                }
                ch.isSurrogate() -> Unit
                else -> append(ch)
            }
            i++
        }
    }

    private const val XML_DECLARATION = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

    private const val CONTENT_TYPES = XML_DECLARATION +
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
        """<Default Extension="xml" ContentType="application/xml"/>""" +
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
        """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
        """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
        """</Types>"""

    private const val ROOT_RELS = XML_DECLARATION +
        """<Relationships xmlns="$PACKAGE_REL_NS">""" +
        """<Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/>""" +
        """</Relationships>"""

    private const val WORKBOOK_RELS = XML_DECLARATION +
        """<Relationships xmlns="$PACKAGE_REL_NS">""" +
        """<Relationship Id="rId1" Type="$REL_NS/worksheet" Target="worksheets/sheet1.xml"/>""" +
        """<Relationship Id="rId2" Type="$REL_NS/styles" Target="styles.xml"/>""" +
        """</Relationships>"""

    /** cellXfs, in order: default, header, date, time, amount. */
    private const val STYLES = XML_DECLARATION +
        """<styleSheet xmlns="$MAIN_NS">""" +
        """<numFmts count="3">""" +
        """<numFmt numFmtId="164" formatCode="yyyy-mm-dd"/>""" +
        """<numFmt numFmtId="165" formatCode="h:mm AM/PM"/>""" +
        """<numFmt numFmtId="166" formatCode="#,##0.00"/>""" +
        """</numFmts>""" +
        """<fonts count="2">""" +
        """<font><sz val="11"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>""" +
        """</fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="5">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """</cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """</styleSheet>"""
}
