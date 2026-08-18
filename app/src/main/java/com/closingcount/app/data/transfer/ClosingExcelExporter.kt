package com.closingcount.app.data.transfer

import com.closingcount.app.data.local.ClosingIngredientResultEntity
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ClosingExcelExporter {
    fun create(
        date: String,
        entries: List<ClosingMenuEntryEntity>,
        results: List<ClosingIngredientResultEntity>,
    ): ByteArray {
        val ingredientRows = buildList {
            add(listOf(Cell("Closing Count System")))
            add(listOf(Cell("Tanggal"), Cell(date)))
            add(emptyList())
            add(listOf(Cell("Kategori Bahan"), Cell("Bahan"), Cell("Terjual / Out")))
            results.forEach { result ->
                add(
                    listOf(
                        Cell(result.ingredientCategoryName),
                        Cell(result.ingredientName),
                        Cell(result.total.toString(), numeric = true),
                    ),
                )
            }
        }
        val menuRows = buildList {
            add(listOf(Cell("Closing Count System")))
            add(listOf(Cell("Tanggal"), Cell(date)))
            add(emptyList())
            add(listOf(Cell("Kategori Menu"), Cell("Menu"), Cell("Jumlah Terjual")))
            entries.filter { it.quantity > 0 }.forEach { entry ->
                add(
                    listOf(
                        Cell(entry.menuCategoryName),
                        Cell(entry.menuName),
                        Cell(entry.quantity.toString(), numeric = true),
                    ),
                )
            }
        }

        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.writeEntry("[Content_Types].xml", contentTypes)
                zip.writeEntry("_rels/.rels", rootRelationships)
                zip.writeEntry("xl/workbook.xml", workbook)
                zip.writeEntry("xl/_rels/workbook.xml.rels", workbookRelationships)
                zip.writeEntry("xl/worksheets/sheet1.xml", worksheet(ingredientRows))
                zip.writeEntry("xl/worksheets/sheet2.xml", worksheet(menuRows))
            }
            bytes.toByteArray()
        }
    }

    private data class Cell(val value: String, val numeric: Boolean = false)

    private fun worksheet(rows: List<List<Cell>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            val number = rowIndex + 1
            append("<row r=\"").append(number).append("\">")
            cells.forEachIndexed { columnIndex, cell ->
                val reference = "${columnName(columnIndex)}$number"
                if (cell.numeric) {
                    append("<c r=\"").append(reference).append("\"><v>")
                        .append(cell.value).append("</v></c>")
                } else {
                    append("<c r=\"").append(reference).append("\" t=\"inlineStr\"><is><t>")
                        .append(escapeXml(cell.value)).append("</t></is></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        return buildString {
            while (value > 0) {
                insert(0, ('A'.code + (value - 1) % 26).toChar())
                value = (value - 1) / 26
            }
        }
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private val contentTypes = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        </Types>
    """.trimIndent()

    private val rootRelationships = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private val workbook = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Ringkasan Bahan" sheetId="1" r:id="rId1"/>
            <sheet name="Penjualan Menu" sheetId="2" r:id="rId2"/>
          </sheets>
        </workbook>
    """.trimIndent()

    private val workbookRelationships = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
        </Relationships>
    """.trimIndent()
}
