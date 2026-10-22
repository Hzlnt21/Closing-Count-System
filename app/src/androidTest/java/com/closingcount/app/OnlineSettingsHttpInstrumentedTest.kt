package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OnlineSettingsHttpInstrumentedTest {
    @Test fun durableSettingsReceiptRetryConflictAndLogoutGuardUseRealService()=runBlocking{
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Owned settings QA only",args.getString("ccsSettingsQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        val context=ApplicationProvider.getApplicationContext<Context>();val outlet=requireNotNull(args.getString("ccsQaOutlet"))
        val password=requireNotNull(args.getString("ccsQaPassword"));val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true))
        val a=api.login(requireNotNull(args.getString("ccsQaAdmin")),password);val e=api.login(requireNotNull(args.getString("ccsQaEmployee")),password)
        val name="settings-qa-${UUID.randomUUID()}.db"
        val db=Room.databaseBuilder(context,ClosingCountDatabase::class.java,name).build()
        val storage=OnlineSettingsStore(context);val sessions=OnlineSessionStore(context)
        val repo=OnlineRepository(db.onlineSyncDao(),sessions,storage)
        try{
            assertEquals("EMPLOYEE",api.outletSettings(e,outlet).role)
            var denied=false;try{api.updateSettings(e,OnlineSettingsOperation(e.scope(outlet),UUID.randomUUID().toString(),0,"Forbidden","Asia/Jakarta",0,"SENT",true))}catch(error:OnlineApiException){denied=error.status==403};assertTrue(denied)
            repo.activate(a);val old=api.create(a,outlet,"2026-07-01")
            repo.prepareSettings(outlet,"Kedai pertama","Asia/Makassar",240,0)
            var guarded=false;try{repo.logout()}catch(_:PendingOnlineDraftsException){guarded=true};assertTrue(guarded);assertNotNull(repo.session())
            val queued=repo.pendingSettings(outlet)!!;val sent=queued.copy(status="SENT",wasSent=true);storage.save(sent)
            api.updateSettings(a,sent) // real commit; deliberately skip local acknowledgement
            val reopenedStore=OnlineSettingsStore(context);val resumed=OnlineRepository(db.onlineSyncDao(),OnlineSessionStore(context),reopenedStore)
            resumed.initialize();assertEquals(sent,resumed.pendingSettings(outlet))
            assertEquals(1L,resumed.syncSettings(outlet).revision);assertNull(resumed.pendingSettings(outlet))
            resumed.prepareSettings(outlet,"Proposed stale change","Asia/Jakarta",0,1)
            api.updateSettings(a,OnlineSettingsOperation(a.scope(outlet),UUID.randomUUID().toString(),1,"Other admin view","Asia/Makassar",300,"SENT",true))
            var conflict=false;try{resumed.syncSettings(outlet)}catch(_:OnlineConflictChangedException){conflict=true};assertTrue(conflict)
            assertEquals("CONFLICT",resumed.pendingSettings(outlet)!!.status);assertFalse(resumed.pendingSettings(outlet)!!.wasSent)
            resumed.dismissSettings(outlet);resumed.prepareSettings(outlet,"Kedai final","Asia/Jayapura",360,2)
            assertEquals(3L,resumed.syncSettings(outlet).revision)
            val newer=api.create(a,outlet,"2026-07-02")
            assertEquals("Asia/Jakarta",api.fetch(a,outlet,old.header.closingId).let{org.json.JSONObject(it.header.snapshotJson).getString("timezone")})
            assertEquals("Asia/Jayapura",org.json.JSONObject(newer.header.snapshotJson).getString("timezone"))
            assertTrue(resumed.logout())
        }finally{
            storage.load(a.scope(outlet))?.let{storage.remove(it.scope,it.operationId,confirmed=true)}
            api.logout(e);try{api.logout(a)}catch(_:java.io.IOException){};sessions.clear();db.close();context.deleteDatabase(name)
        }
    }
}
