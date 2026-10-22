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

class OnlineCatalogHttpInstrumentedTest{
    @Test fun receiptRecoveryLaterPublicationStaleReviewAndReauthenticationPreserveFrozenClosing()=runBlocking{
        val args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue("Owned catalog QA only",args.getString("ccsCatalogQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        val context=ApplicationProvider.getApplicationContext<Context>();val outlet=requireNotNull(args.getString("ccsQaOutlet"));val password=requireNotNull(args.getString("ccsQaPassword"))
        val username=requireNotNull(args.getString("ccsQaAdmin"));val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true))
        val admin=api.login(username,password);val employee=api.login(requireNotNull(args.getString("ccsQaEmployee")),password)
        val name="catalog-qa-${UUID.randomUUID()}.db";val db=Room.databaseBuilder(context,ClosingCountDatabase::class.java,name).build()
        val storage=OnlineCatalogStore(context);val sessions=OnlineSessionStore(context)
        fun repo()=OnlineRepository(db.onlineSyncDao(),sessions,catalogStore=storage)
        fun operation(session:OnlineSession,revision:Long,catalog:OnlineCatalog)=OnlineCatalogOperation(session.scope(outlet),UUID.randomUUID().toString(),revision,catalog.json().toString(),"SENT",true)
        try{
            val original=api.catalog(employee,outlet);assertEquals(1L,original.revision)
            var denied=false;try{api.publishCatalog(employee,operation(employee,1,original.catalog))}catch(e:OnlineApiException){denied=e.status==403};assertTrue(denied)
            val old=api.create(admin,outlet,"2026-06-01");val oldCatalog=JSONObject(old.header.snapshotJson).getJSONObject("catalog").toString()
            val first=original.catalog.copy(menus=original.catalog.menus.map{it.copy(name=it.name+" v2")}).validated()
            val r=repo();r.activate(admin);r.prepareCatalog(outlet,1,first)
            var guarded=false;try{r.logout()}catch(_:PendingOnlineDraftsException){guarded=true};assertTrue(guarded)
            guarded=false;try{r.activate(employee)}catch(_:PendingOnlineDraftsException){guarded=true};assertTrue(guarded);assertEquals(admin.identity.id,r.session()!!.identity.id)
            val pending=r.pendingCatalog(outlet)!!;val sent=pending.copy(status="SENT",wasSent=true);storage.save(sent)
            assertEquals(2L,api.publishCatalog(admin,sent).revision) // real commit; omit local acknowledgement intentionally
            val second=first.copy(ingredients=first.ingredients.map{it.copy(name=it.name+" v3")}).validated()
            assertEquals(3L,api.publishCatalog(admin,operation(admin,2,second)).revision)
            val resumed=OnlineRepository(db.onlineSyncDao(),OnlineSessionStore(context),catalogStore=OnlineCatalogStore(context));resumed.initialize()
            assertEquals(sent,resumed.pendingCatalog(outlet));val latest=resumed.syncCatalog(outlet)
            assertEquals(3L,latest.revision);assertEquals(second,latest.catalog);assertNull(resumed.pendingCatalog(outlet))
            resumed.prepareCatalog(outlet,3,first)
            assertEquals(4L,api.publishCatalog(admin,operation(admin,3,second)).revision)
            var conflict=false;try{resumed.syncCatalog(outlet)}catch(_:OnlineConflictChangedException){conflict=true};assertTrue(conflict)
            assertEquals("CONFLICT",resumed.pendingCatalog(outlet)!!.status);assertFalse(resumed.pendingCatalog(outlet)!!.wasSent)
            resumed.dismissCatalog(outlet)
            val final=second.copy(menus=second.menus.map{it.copy(name="Final "+it.name,ingredientIds=listOf(it.ingredientIds.first()))}).validated()
            resumed.prepareCatalog(outlet,4,final);assertEquals(5L,resumed.syncCatalog(outlet).revision)
            resumed.prepareCatalog(outlet,5,final);api.logout(admin)
            var expired=false;try{resumed.syncCatalog(outlet)}catch(e:OnlineApiException){expired=e.status==401};assertTrue(expired)
            assertEquals("AUTH_REQUIRED",resumed.pendingCatalog(outlet)!!.status)
            val fresh=api.login(username,password);resumed.activate(fresh);assertEquals("QUEUED",resumed.pendingCatalog(outlet)!!.status)
            assertEquals(6L,resumed.syncCatalog(outlet).revision)
            val historical=api.fetch(fresh,outlet,old.header.closingId)
            assertEquals(oldCatalog,JSONObject(historical.header.snapshotJson).getJSONObject("catalog").toString())
            val newer=api.create(fresh,outlet,"2026-06-02")
            val frozen=OnlineCatalog.parse(JSONObject(newer.header.snapshotJson).getJSONObject("catalog"))
            assertEquals(final.menus,frozen.menus);val used=final.menus.flatMap{it.ingredientIds}.toSet()
            assertEquals(final.ingredients.filter{it.id in used},frozen.ingredients)
            assertEquals(final.ingredientCategories.filter{c->frozen.ingredients.any{it.categoryId==c.id}},frozen.ingredientCategories)
            assertEquals(original.versionId,JSONObject(historical.header.snapshotJson).getString("catalogVersionId"))
            assertTrue(resumed.logout())
        }finally{
            storage.load(admin.scope(outlet))?.let{storage.remove(it.scope,it.operationId,true)}
            try{api.logout(admin)}catch(_:java.io.IOException){};api.logout(employee);sessions.clear();db.close();context.deleteDatabase(name)
        }
    }
}
