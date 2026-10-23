package com.closingcount.app.data.transfer

import com.closingcount.app.data.online.FrozenClosingReport
import com.closingcount.app.data.online.ReportActor
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max

data class ReportExportDocument(val report: FrozenClosingReport, val outletName: String, val exportedBy: ReportActor, val exportedAt: Instant)

/** A portable read-only representation of one locked generation; no session or endpoint enters this model. */
object OnlineReportExcelExporter {
    private data class Cell(val value: String, val style: Int = 0, val numeric: Boolean = false, val formula: String? = null)
    private data class Sheet(val name: String, val xml: String)
    fun create(document: ReportExportDocument): ByteArray {
        require(document.outletName.length in 1..200)
        val report = document.report
        require(report.entries.size <= 500 && report.entries.all { it.quantity in 0..9999 })
        require(report.ingredientTotals.values.all { it in 1..4999500 })
        val ic = report.catalog.ingredientCategories.associateBy { it.id }
        val mc = report.catalog.menuCategories.associateBy { it.id }
        val entries = report.entries.associateBy { it.menuId }
        val ingredientRows = report.catalog.ingredients.filter { (report.ingredientTotals[it.id] ?: 0) > 0 }
            .sortedWith(compareBy({ ic.getValue(it.categoryId).sortOrder }, { it.sortOrder }, { it.id })).map {
                listOf(Cell(ic.getValue(it.categoryId).name), Cell(it.name), Cell(report.ingredientTotals.getValue(it.id).toString(), 3, true))
            }
        val menuRows = report.catalog.menus.sortedWith(compareBy({ mc.getValue(it.categoryId).sortOrder }, { it.sortOrder }, { it.id })).map { menu ->
            val entry = entries.getValue(menu.id)
            listOf(Cell(mc.getValue(menu.categoryId).name), Cell(menu.name), Cell(entry.quantity.toString(), 3, true),
                Cell(entry.actor?.let { "${it.name} (${it.username})" } ?: if(entry.quantity == 0L && entry.revision == 0L) "Belum diubah" else "Tidak tercatat"))
        }
        val sheets = listOf(
            table(document, "Pemakaian Bahan", "Pemakaian bahan", listOf("Kategori bahan", "Bahan", "Terjual / Out"), listOf(26, 52, 20), ingredientRows, false),
            table(document, "Penjualan Menu", "Penjualan menu", listOf("Kategori menu", "Menu", "Jumlah (porsi)", "Pencatat saat dikunci"), listOf(24, 48, 18, 34), menuRows, true),
        )
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                fun part(name: String, text: String) { zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray(Charsets.UTF_8));zip.closeEntry() }
                part("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""")
                part("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
                part("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView activeTab="0"/></bookViews><sheets>${sheets.mapIndexed { i, sheet -> "<sheet name=\"${xml(sheet.name)}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>" }.joinToString("")}</sheets><calcPr fullCalcOnLoad="1"/></workbook>""")
                part("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.indices.joinToString("") { "<Relationship Id=\"rId${it+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it+1}.xml\"/>" }}<Relationship Id="styles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
                part("xl/styles.xml", styles)
                sheets.forEachIndexed { i, sheet -> part("xl/worksheets/sheet${i+1}.xml", sheet.xml) }
            }
            bytes.toByteArray()
        }
    }

    private fun table(d: ReportExportDocument, name: String, title: String, headers: List<String>, widths: List<Int>, data: List<List<Cell>>, total: Boolean): Sheet {
        val r = d.report
        val rows = mutableListOf<List<Cell>>(emptyList(), listOf(Cell(title, 1)),
            listOf(Cell("Outlet saat ekspor", 6), Cell(d.outletName)),
            listOf(Cell("Tanggal bisnis", 6), date(r.date)),
            listOf(Cell("Generasi / revisi", 6), Cell("${r.generation} / ${r.revision}")),
            listOf(Cell("Dikunci oleh", 6), Cell("${r.reviewer.name} (${r.reviewer.username})")),
            listOf(Cell("Penguncian", 6), Cell("${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(r.reviewedAt.atZone(ZoneId.of(r.timezone)))} (${r.timezone})")),
            emptyList(), headers.map { Cell(it, 2) })
        rows.addAll(data)
        if (total) {
            val sum = r.entries.fold(0L) { value, entry -> Math.addExact(value, entry.quantity) }
            rows += listOf(Cell("Total porsi", 6), Cell(""), Cell(sum.toString(), 4, true, if (data.isEmpty()) "0" else "SUM(C10:C${9+data.size})"))
        } else if (data.isEmpty()) rows += listOf(Cell("Tidak ada penggunaan bahan.", 7))
        rows += emptyList<Cell>()
        for ((label, value) in listOf("ID laporan" to r.id, "ID closing" to r.closingId, "ID outlet" to r.outletId,
            "Akun pengekspor" to "${d.exportedBy.name} (${d.exportedBy.username})", "Ekspor (UTC)" to d.exportedAt.toString())) {
            rows += listOf(Cell(label, 6), Cell(value, 7))
        }
        val lastCol = ('A'.code+headers.size-1).toChar()
        val body = buildString {
            rows.forEachIndexed { i, cells ->
                val lines = cells.mapIndexed { col, cell ->
                    cell.value.split('\n').sumOf { max(1, ceil(it.codePoints().map { cp -> if (cp > 255) 2 else 1 }.sum().toDouble() / (widths[col]*0.85)).toInt()) }
                }.maxOrNull() ?: 1
                val height = when (i) { 0, 7 -> 8;1 -> 26;8 -> 32;else -> (lines*16+6).coerceAtMost(400) }
                append("<row r=\"${i+1}\" ht=\"$height\" customHeight=\"1\">")
                cells.forEachIndexed { j, c ->
                    val address = "${('A'.code+j).toChar()}${i+1}"
                    if (c.numeric) {
                        require(c.value.matches(Regex("[0-9]+")))
                        append("<c r=\"$address\" s=\"${c.style}\">")
                        c.formula?.let { append("<f>${xml(it)}</f>") }
                        append("<v>${c.value}</v></c>")
                    } else append("<c r=\"$address\" s=\"${c.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(c.value)}</t></is></c>")
                }
                append("</row>")
            }
        }
        return Sheet(name, """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><dimension ref="A1:$lastCol${rows.size}"/><sheetViews><sheetView workbookViewId="0" showGridLines="0"><pane ySplit="9" topLeftCell="A10" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="22"/><cols>${widths.mapIndexed { i,w -> "<col min=\"${i+1}\" max=\"${i+1}\" width=\"$w\" customWidth=\"1\"/>" }.joinToString("")}</cols><sheetData>$body</sheetData>${if(data.isEmpty()) "" else "<autoFilter ref=\"A9:$lastCol${9+data.size}\"/>"}<pageMargins left="0.3" right="0.3" top="0.4" bottom="0.4" header="0.2" footer="0.2"/><pageSetup paperSize="9" orientation="landscape" fitToWidth="1" fitToHeight="0"/></worksheet>""")
    }
    private fun date(value: LocalDate): Cell {
        if (value.year < 1900) return Cell(value.toString())
        val serial = ChronoUnit.DAYS.between(LocalDate.of(1899,12,31), value) + if (value >= LocalDate.of(1900,3,1)) 1 else 0
        return Cell(serial.toString(), 5, true)
    }
    private fun xml(value: String): String {
        require(value.codePoints().allMatch { it in 0x20..0xD7FF || it in 0xE000..0xFFFD || it in 0x10000..0x10FFFF || it in listOf(9,10,13) }) { "Teks laporan memuat karakter yang tidak didukung Excel." }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    }
    private val styles = """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="yyyy-mm-dd"/></numFmts><fonts count="5"><font><sz val="11"/><color rgb="FF142E43"/><name val="Arial"/></font><font><b/><sz val="16"/><color rgb="FF003459"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FF003459"/><name val="Arial"/></font><font><sz val="9.5"/><color rgb="FF4E6679"/><name val="Arial"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF007EA7"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border/><border><top style="thin"><color rgb="FF007EA7"/></top></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="top" indent="1"/></xf><xf numFmtId="3" fontId="3" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="top" indent="1"/></xf><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="left" vertical="top"/></xf><xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
