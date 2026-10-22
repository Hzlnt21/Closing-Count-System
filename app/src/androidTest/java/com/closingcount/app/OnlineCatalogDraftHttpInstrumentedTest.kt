package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OnlineCatalogDraftHttpInstrumentedTest{
    @Test fun reviewedDraftPersistsConflictsWithoutRebaseAndFreezesSameIdentityBeforeRecovery()=runBlocking{
        val args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue("Owned draft QA only",args.getString("ccsCatalogDraftQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        val context=ApplicationProvider.getApplicationContext<Context>();val outlet=requireNotNull(args.getString("ccsQaOutlet"));val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true))
        val session=api.login(requireNotNull(args.getString("ccsQaAdmin")),requireNotNull(args.getString("ccsQaPassword")))
        val name="catalog-draft-${UUID.randomUUID()}.db";val db=Room.databaseBuilder(context,ClosingCountDatabase::class.java,name).build()
        val sessions=OnlineSessionStore(context);val storage=OnlineCatalogStore(context)
        fun repo()=OnlineRepository(db.onlineSyncDao(),sessions,catalogStore=OnlineCatalogStore(context))
        try{
            val r=repo();r.activate(session);val initial=api.catalog(session,outlet);assertEquals(1L,initial.revision)
            r.startCatalogDraft(outlet,initial);val editing=r.pendingCatalog(outlet)!!;assertEquals("EDITING",editing.status)
            val changed=initial.catalog.copy(menus=initial.catalog.menus.map{it.copy(name="Draft "+it.name)}).validated()
            r.editCatalogDraft(outlet,changed)
            val reopened=repo();reopened.initialize();val retained=reopened.pendingCatalog(outlet)!!
            assertEquals(editing.operationId,retained.operationId);assertEquals(changed,OnlineCatalog.parse(JSONObject(retained.catalogJson)))
            var guarded=false;try{reopened.logout()}catch(_:PendingOnlineDraftsException){guarded=true};assertTrue(guarded)
            api.publishCatalog(session,OnlineCatalogOperation(session.scope(outlet),UUID.randomUUID().toString(),1,initial.catalog.json().toString(),"SENT",true))
            var stale=false;try{reopened.prepareCatalog(outlet,1,changed)}catch(_:OnlineConflictChangedException){stale=true};assertTrue(stale)
            assertEquals(retained,reopened.pendingCatalog(outlet)) // keep the same editable draft; no silent rebase
            reopened.dismissCatalog(outlet);assertNull(reopened.pendingCatalog(outlet));val fresh=api.catalog(session,outlet)
            reopened.startCatalogDraft(outlet,fresh);reopened.editCatalogDraft(outlet,changed);val draft=reopened.pendingCatalog(outlet)!!
            reopened.prepareCatalog(outlet,2,changed);val frozen=reopened.pendingCatalog(outlet)!!
            assertEquals(draft.operationId,frozen.operationId);assertEquals("QUEUED",frozen.status);assertEquals(draft.catalogJson,frozen.catalogJson)
            var locked=false;try{reopened.editCatalogDraft(outlet,initial.catalog)}catch(_:IllegalStateException){locked=true};assertTrue(locked)
            val sent=frozen.copy(status="SENT",wasSent=true);storage.save(sent);api.publishCatalog(session,sent)
            val recovered=repo();recovered.initialize();assertEquals(3L,recovered.syncCatalog(outlet).revision);assertNull(recovered.pendingCatalog(outlet))
            assertTrue(recovered.logout())
        }finally{storage.load(session.scope(outlet))?.let{storage.remove(it.scope,it.operationId,true)};try{api.logout(session)}catch(_:java.io.IOException){};sessions.clear();db.close();context.deleteDatabase(name)}
    }
}
