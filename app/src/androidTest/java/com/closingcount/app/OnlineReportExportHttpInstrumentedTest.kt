package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.ClosingCountDatabase
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlineReportExportHttpInstrumentedTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var fixture: ReportExportQaFixture
    private lateinit var db: ClosingCountDatabase
    private lateinit var repo: OnlineRepository
    @Before fun setup(): Unit=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ccsReportExportQa")=="true")
        fixture=ReportExportQaFixture(context);fixture.seed()
        db=Room.inMemoryDatabaseBuilder(context,ClosingCountDatabase::class.java).build()
        repo=OnlineRepository(db.onlineSyncDao(),OnlineSessionStore(context,"ccs-export-http-qa"))
        repo.activate(fixture.employee,true)
    }
    @After fun cleanup(): Unit=runBlocking {
        if(::fixture.isInitialized)fixture.close()
        OnlineSessionStore(context).clear()
        if(::db.isInitialized)db.close()
    }
    @Test fun exportsFrozenFourAfterCurrentSixWithFreshOutletName(): Unit=runBlocking {
        val original=fixture.report
        val settings=fixture.api.outletSettings(fixture.admin,fixture.outlet)
        val newName="Outlet Setelah Penguncian ${UUID.randomUUID().toString().take(8)}"
        fixture.api.updateSettings(fixture.admin,OnlineSettingsOperation(fixture.admin.scope(fixture.outlet),UUID.randomUUID().toString(),
            settings.revision,newName,settings.timezone,settings.cutoffMinutes,"SENT",true))
        var calls=0
        repo.withReportForExport(fixture.employee,original) { report,name,actor ->
            calls++;assertEquals(4L,report.entries.first { it.menuId==fixture.menuId }.quantity)
            assertEquals(newName,name);assertEquals(fixture.employee.identity.id,actor.id)
            val bytes=OnlineReportExcelExporter.create(ReportExportDocument(report,name,actor,Instant.parse("2026-10-23T05:00:00Z")))
            File(context.getExternalFilesDir(null),"report-export-http.xlsx").writeBytes(bytes)
            File(context.getExternalFilesDir(null),"report-export-http-source.json").writeText(fixture.raw)
            File(context.getExternalFilesDir(null),"report-export-http-context.json").writeText(JSONObject().put("outletName",name).put("exportedBy",actor.username).toString())
        }
        assertEquals(1,calls)
        assertEquals(6,fixture.api.fetch(fixture.employee,fixture.outlet,fixture.closingId).entries.first { it.menuId==fixture.menuId }.serverQuantity)
    }
    @Test fun changedAccountCannotWriteAnEarlierPickerTicket(): Unit=runBlocking {
        repo.activate(fixture.colleague,true)
        var wrote=false
        val error=runCatching { repo.withReportForExport(fixture.employee,fixture.report) { _,_,_ -> wrote=true } }.exceptionOrNull()
        assertNotNull(error);assertFalse(wrote)
    }
    @Test fun expiredServerSessionFailsBeforeWriterIsCalled(): Unit=runBlocking {
        fixture.api.logout(fixture.employee)
        var wrote=false
        val error=runCatching { repo.withReportForExport(fixture.employee,fixture.report) { _,_,_ -> wrote=true } }.exceptionOrNull()
        assertTrue(error is OnlineApiException && error.status==401);assertFalse(wrote)
    }
    @Test fun revokedMembershipFailsBeforeWriterIsCalled(): Unit=runBlocking {
        fixture.revoke()
        var wrote=false
        val error=runCatching { repo.withReportForExport(fixture.employee,fixture.report) { _,_,_ -> wrote=true } }.exceptionOrNull()
        assertTrue(error is OnlineApiException && error.status in listOf(401,403));assertFalse(wrote)
    }
    @Test fun alteredReportIdentityAndMalformedTotalsAreRejected(): Unit=runBlocking {
        val original=fixture.report
        var wrote=false
        assertNotNull(runCatching { repo.withReportForExport(fixture.employee,original.copy(id=UUID.randomUUID().toString())) { _,_,_ -> wrote=true } }.exceptionOrNull())
        assertFalse(wrote)
        val mutations: List<(JSONObject)->Unit> = listOf(
            { it.put("generation",1.5) },
            { it.put("outletId",UUID.randomUUID().toString()) },
            { it.getJSONObject("closing").getJSONArray("ingredientTotals").getJSONObject(0).put("total",99) },
            { it.getJSONObject("closing").getJSONArray("ingredientTotals").getJSONObject(0).put("total","4") },
            { it.getJSONObject("closing").getJSONArray("entries").getJSONObject(0).put("quantity",4294967296L) },
            { it.getJSONObject("reviewedBy").put("id",UUID.randomUUID().toString()) },
        )
        for(mutate in mutations){val payload=JSONObject(fixture.raw);mutate(payload);assertNotNull(runCatching { FrozenClosingReport.parse(payload.toString()) }.exceptionOrNull())}
    }
}
