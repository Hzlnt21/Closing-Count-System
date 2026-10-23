package com.closingcount.app

import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class OnlineReportExcelExporterTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12,'0')}"
    private fun sample() = FrozenClosingReport(id(1), id(2), id(3), 1, 4, LocalDate.of(2026,9,21), "Asia/Jakarta", 0, id(14),
        ReportActor(id(7), "arif.qa", "Arif"), Instant.parse("2026-09-21T12:00:00Z"), Instant.parse("2026-09-21T01:00:00Z"), Instant.parse("2026-09-21T12:00:00Z"),
        OnlineCatalog(listOf(CatalogCategory(id(10), "Bahan", 1)), listOf(CatalogIngredient(id(12), id(10), "Susu & kopi", 1, true)),
            listOf(CatalogCategory(id(11), "Coffee", 1)), listOf(CatalogMenu(id(13), id(11), "Latte", 1, true, listOf(id(12))))),
        listOf(ReportEntry(id(13), 4, 1, ReportActor(id(8), "rani.qa", "Rani"), Instant.parse("2026-09-21T11:00:00Z"))), mapOf(id(12) to 4L))
    private fun document(report: FrozenClosingReport = sample()) = ReportExportDocument(report, "Outlet saat ekspor", ReportActor(id(9), "dimas.qa", "Dimas"), Instant.parse("2026-10-23T05:00:00Z"))
    private fun parts(bytes: ByteArray): Map<String, ByteArray> {
        val values = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while(true) {
            val entry = zip.nextEntry ?: break
            assertFalse(values.containsKey(entry.name));values[entry.name] = zip.readBytes()
        } }
        return values
    }
    private fun sheet(parts: Map<String,ByteArray>, number: Int) = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }.newDocumentBuilder().parse(ByteArrayInputStream(parts.getValue("xl/worksheets/sheet$number.xml")))
    private fun cell(parts: Map<String,ByteArray>, number: Int, address: String): Element {
        val nodes = sheet(parts, number).getElementsByTagName("c")
        return (0 until nodes.length).map { nodes.item(it) as Element }.single { it.getAttribute("r") == address }
    }
    private fun text(cell: Element): String = cell.getElementsByTagName("t").let { if(it.length>0)it.item(0).textContent else cell.getElementsByTagName("v").item(0).textContent }

    @Test fun frozenCountsActorsMetadataAndFormulaArePreserved() {
        val bytes = OnlineReportExcelExporter.create(document());val values = parts(bytes)
        assertEquals(7, values.size)
        assertEquals("4", text(cell(values,1,"C10")));assertEquals("4", text(cell(values,2,"C10")))
        assertEquals("Rani (rani.qa)", text(cell(values,2,"D10")))
        assertEquals("Arif (arif.qa)", text(cell(values,1,"B6")))
        assertEquals("Outlet saat ekspor", text(cell(values,1,"B3")))
        val total = cell(values,2,"C11")
        assertEquals("SUM(C10:C10)", total.getElementsByTagName("f").item(0).textContent)
        assertEquals("4", total.getElementsByTagName("v").item(0).textContent)
        assertTrue(values.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8).contains("showGridLines=\"0\""))
        File("build/reports/report-export").mkdirs();File("build/reports/report-export/online-frozen-report.xlsx").writeBytes(bytes)
    }
    @Test fun namesRemainLiteralAndNeverBecomeFormulasOrExternalRelationships() {
        val original = sample();val dangerous = "=HYPERLINK(\"https://example.invalid\",\"<Kopi & susu>\")"
        val report = original.copy(catalog = original.catalog.copy(menus = listOf(original.catalog.menus.single().copy(name = dangerous))))
        val values = parts(OnlineReportExcelExporter.create(document(report)))
        val name = cell(values,2,"B10")
        assertEquals("inlineStr",name.getAttribute("t"));assertEquals(dangerous,text(name));assertEquals(0,name.getElementsByTagName("f").length)
        assertTrue(values.keys.none { it.contains("vba",true) || it.contains("external",true) })
        assertFalse(values.getValue("xl/_rels/workbook.xml.rels").toString(Charsets.UTF_8).contains("TargetMode"))
    }
    @Test fun invalidXmlAndUnsafeCountsFailBeforeAFileIsProduced() {
        val r = sample()
        val bad = r.copy(catalog=r.catalog.copy(menus=listOf(r.catalog.menus.single().copy(name="Invalid\u0001name"))))
        assertThrows(IllegalArgumentException::class.java) { OnlineReportExcelExporter.create(document(bad)) }
        assertThrows(IllegalArgumentException::class.java) { OnlineReportExcelExporter.create(document(r.copy(entries=listOf(r.entries.single().copy(quantity=Long.MAX_VALUE))))) }
        assertThrows(IllegalArgumentException::class.java) { OnlineReportExcelExporter.create(document(r.copy(ingredientTotals=mapOf(id(12) to -1L)))) }
    }
    @Test fun datesUseNumericExcelCalendarWithLiteralFallbackBefore1900() {
        for ((date, serial) in listOf("1900-01-01" to "1", "1900-02-28" to "59", "1900-03-01" to "61")) {
            val values=parts(OnlineReportExcelExporter.create(document(sample().copy(date=LocalDate.parse(date)))))
            assertEquals(serial,text(cell(values,1,"B4")));assertEquals("5",cell(values,1,"B4").getAttribute("s"))
        }
        val old=parts(OnlineReportExcelExporter.create(document(sample().copy(date=LocalDate.of(1,1,1)))))
        assertEquals("inlineStr",cell(old,1,"B4").getAttribute("t"));assertEquals("0001-01-01",text(cell(old,1,"B4")))
    }
    @Test fun largeGenerationAndRevisionRemainExactTextIdentifiers() {
        val values=parts(OnlineReportExcelExporter.create(document(sample().copy(generation=9007199254740991L,revision=9007199254740991L))))
        val id=cell(values,1,"B5");assertEquals("inlineStr",id.getAttribute("t"));assertEquals("9007199254740991 / 9007199254740991",text(id))
    }
    @Test fun emptyReportKeepsARealZeroWithoutCircularTotalsOrSyntheticIngredients() {
        val r=sample().copy(catalog=OnlineCatalog.empty(),entries=emptyList(),ingredientTotals=emptyMap())
        val values=parts(OnlineReportExcelExporter.create(document(r)))
        assertEquals("Tidak ada penggunaan bahan.",text(cell(values,1,"A10")))
        val total=cell(values,2,"C10");assertEquals("0",total.getElementsByTagName("f").item(0).textContent);assertEquals("0",text(total))
        assertFalse(values.getValue("xl/worksheets/sheet2.xml").toString(Charsets.UTF_8).contains("autoFilter"))
    }
}
