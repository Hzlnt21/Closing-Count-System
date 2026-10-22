package com.closingcount.app

import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import com.closingcount.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OnlineHistoryHttpInstrumentedTest {
    @Test fun realHistoryAuditAndReportPaginationRetainHistoricalQuantitiesAndRejectRevokedSession()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Owned history QA only",args.getString("ccsHistoryQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true));val outlet=requireNotNull(args.getString("ccsQaOutlet"))
        val password=requireNotNull(args.getString("ccsQaPassword"))
        val a=api.login(requireNotNull(args.getString("ccsQaEmployee")),password)
        val s=api.login(requireNotNull(args.getString("ccsQaSupervisor")),password)
        try {
            val initial=api.create(a,outlet,"2026-08-01");val id=initial.header.closingId;val menu=initial.entries.first().menuId
            api.create(a,outlet,"2026-08-02");api.create(a,outlet,"2026-08-03")
            var cursor:String?="2026-08-04";val dates=mutableListOf<String>()
            do {val page=api.history(a,outlet,cursor,1);dates+=page.items.map{it.date};cursor=page.nextBefore}while(cursor!=null)
            assertEquals(listOf("2026-08-03","2026-08-02","2026-08-01"),dates)
            suspend fun quantity(value:Int){val current=api.fetch(a,outlet,id);val row=current.entries.first{it.menuId==menu}
                api.send(a,OnlineOperationEntity(operationId=UUID.randomUUID().toString(),scope=a.scope(outlet),closingId=id,menuId=menu,
                    generation=current.header.generation,expectedRevision=row.serverRevision,quantity=value,status="SENT",errorCode=null))}
            suspend fun act(session:OnlineSession,action:String,reason:String=""){
                val current=api.fetch(session,outlet,id)
                api.lifecycle(session,OnlineLifecycleEntity(session.scope(outlet),id,UUID.randomUUID().toString(),action,
                    current.header.generation,current.header.revision,reason,"SENT",true,null))}
            quantity(4);act(a,"submit");act(s,"lock");val original=api.report(a,outlet,id,1)
            act(s,"reopen","Koreksi laporan historis");quantity(6);act(a,"submit");act(s,"lock")
            val reports=api.reports(a,outlet,id,null,1);assertEquals(2L,reports.items.single().generation);assertEquals(2L,reports.nextBefore)
            val older=api.reports(a,outlet,id,reports.nextBefore,1);assertEquals(1L,older.items.single().generation);assertNull(older.nextBefore)
            assertEquals(original,api.report(a,outlet,id,1))
            val frozen=JSONObject(original).getJSONObject("closing").getJSONArray("entries")
            assertEquals(4,(0 until frozen.length()).map{frozen.getJSONObject(it)}.first{it.getString("menuId")==menu}.getInt("quantity"))
            var after="0";val audit=mutableListOf<OnlineAuditRecord>()
            do {val page=api.audit(a,outlet,id,after,2);audit+=page.items;after=page.nextAfter?:""}while(after.isNotEmpty())
            assertEquals(8,audit.size);assertEquals(audit.size,audit.map{it.id}.distinct().size)
            assertEquals(listOf("CREATE","ENTRY_UPDATED","SUBMIT","LOCK","REOPEN","ENTRY_UPDATED","SUBMIT","LOCK"),audit.map{it.action})
            assertEquals("Koreksi laporan historis",audit.first{it.action=="REOPEN"}.reason)
            assertEquals(listOf(4,6),audit.filter{it.action=="ENTRY_UPDATED"}.map{it.after})
            assertTrue(audit.filter{it.action in setOf("LOCK","REOPEN")}.all{it.actor=="Dummy arif"})
            api.logout(a);var denied=false
            try{api.history(a,outlet)}catch(e:OnlineApiException){denied=e.status==401};assertTrue(denied)
        } finally {try{api.logout(a)}catch(_:java.io.IOException){};api.logout(s)}
    }
}
