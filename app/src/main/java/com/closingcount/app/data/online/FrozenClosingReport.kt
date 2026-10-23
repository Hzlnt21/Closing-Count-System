package com.closingcount.app.data.online

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReportActor(val id: String, val username: String, val name: String)
data class ReportEntry(val menuId: String, val quantity: Long, val revision: Long, val actor: ReportActor?, val at: Instant?)
data class FrozenClosingReport(
    val id: String, val closingId: String, val outletId: String, val generation: Long, val revision: Long,
    val date: LocalDate, val timezone: String, val cutoffMinutes: Long, val catalogVersionId: String,
    val reviewer: ReportActor, val reviewedAt: Instant, val createdAt: Instant, val updatedAt: Instant,
    val catalog: OnlineCatalog, val entries: List<ReportEntry>, val ingredientTotals: Map<String, Long>,
) {
    companion object {
        private fun text(row: JSONObject, key: String): String = row.get(key).let { require(it is String);it }
        private fun number(row: JSONObject, key: String, minimum: Long = 0): Long {
            val value = row.get(key);require(value is Int || value is Long)
            return (value as Number).toLong().also { require(it in minimum..9007199254740991L) }
        }
        private fun id(row: JSONObject, key: String) = OnlineApi.uuid(text(row, key))
        private fun actor(row: JSONObject) = ReportActor(id(row, "id"), text(row, "username").also { require(it.length in 1..200) },
            text(row, "displayName").also { require(it.length in 1..200) })

        fun parse(raw: String): FrozenClosingReport {
            val report = JSONObject(raw);require(text(report, "format") == "closing-count-report" && number(report, "schemaVersion") == 1L)
            val closing = report.getJSONObject("closing")
            val reportId = id(report, "id");val closingId = id(report, "closingId");val outlet = id(report, "outletId")
            val generation = number(report, "generation", 1);val revision = number(report, "closingRevision", 1)
            require(id(closing, "id") == closingId && id(closing, "outletId") == outlet && text(closing, "state") == "LOCKED")
            require(number(closing, "generation", 1) == generation && number(closing, "closingRevision", 1) == revision)
            val reviewer = actor(report.getJSONObject("reviewedBy"));val at = Instant.parse(text(report, "reviewedAt"))
            require(actor(closing.getJSONObject("lockedBy")) == reviewer && Instant.parse(text(closing, "lockedAt")) == at)
            val date = LocalDate.parse(text(closing, "businessDate"));require(date.year in 1..9999 && date.toString() == text(closing, "businessDate"))
            val zone = text(closing, "timezone");ZoneId.of(zone)
            val cutoff = number(closing, "cutoffMinutes");require(cutoff <= 1439)
            val catalog = OnlineCatalog.parse(closing.getJSONObject("catalog"));require(catalog.menus.all { it.active })
            val rows = closing.getJSONArray("entries");require(rows.length() <= 500)
            val entries = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                ReportEntry(id(row, "menuId"), number(row, "quantity").also { require(it <= 9999) }, number(row, "entryRevision"),
                    if (row.isNull("updatedBy")) null else actor(row.getJSONObject("updatedBy")),
                    if (row.isNull("updatedAt")) null else Instant.parse(text(row, "updatedAt")))
            }.sortedBy { it.menuId }
            require(entries.map { it.menuId }.distinct().size == entries.size && entries.map { it.menuId }.toSet() == catalog.menus.map { it.id }.toSet())
            val quantities = entries.associate { it.menuId to it.quantity }
            val expected = mutableMapOf<String, Long>()
            for (menu in catalog.menus) for (ingredient in menu.ingredientIds) {
                expected[ingredient] = Math.addExact(expected[ingredient] ?: 0L, quantities.getValue(menu.id))
            }
            val totals = closing.getJSONArray("ingredientTotals");require(totals.length() <= 1000)
            val actual = mutableMapOf<String, Long>()
            for (i in 0 until totals.length()) {
                val line = totals.getJSONObject(i);val key = id(line, "id");val ingredient = requireNotNull(catalog.ingredients.find { it.id == key })
                require(text(line, "name") == ingredient.name && id(line, "categoryId") == ingredient.categoryId)
                require(actual.put(key, number(line, "total", 1)) == null)
            }
            require(actual == expected.filterValues { it > 0 }) { "Total bahan tidak sesuai resep historis." }
            return FrozenClosingReport(reportId, closingId, outlet, generation, revision, date, zone, cutoff, id(closing, "catalogVersionId"),
                reviewer, at, Instant.parse(text(closing, "createdAt")), Instant.parse(text(closing, "updatedAt")), catalog, entries, actual.toSortedMap())
        }
    }
}
