package com.closingcount.app

import com.closingcount.app.data.local.ClosingIngredientResultEntity
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import com.closingcount.app.data.transfer.ClosingExcelExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ClosingExcelExporterTest {
    @Test
    fun workbookContainsIngredientAndMenuSheetsWithValues() {
        val bytes = ClosingExcelExporter.create(
            date = "2026-08-18",
            entries = listOf(
                ClosingMenuEntryEntity(1, 1, "Latte", 1, 1, "Coffee", 1, 3),
                ClosingMenuEntryEntity(1, 2, "Cappuccino", 2, 1, "Coffee", 1, 0),
            ),
            results = listOf(
                ClosingIngredientResultEntity(1, 1, "Fresh Milk", 1, "Bahan Baku", 1, 1, 3),
            ),
        )
        val entries = unzip(bytes)

        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/worksheets/sheet1.xml",
                "xl/worksheets/sheet2.xml",
            ),
            entries.keys,
        )
        assertTrue(entries.getValue("xl/workbook.xml").contains("Ringkasan Bahan"))
        assertTrue(entries.getValue("xl/workbook.xml").contains("Penjualan Menu"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("Fresh Milk"))
        assertTrue(entries.getValue("xl/worksheets/sheet2.xml").contains("Latte"))
        assertTrue(!entries.getValue("xl/worksheets/sheet2.xml").contains("Cappuccino"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
