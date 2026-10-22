package com.closingcount.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.closingcount.app.data.online.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class OnlineCatalogStoreInstrumentedTest{
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun op()=OnlineCatalogOperation("https://qa.example.test|${UUID.randomUUID()}|${UUID.randomUUID()}",UUID.randomUUID().toString(),0,OnlineCatalog.empty().json().toString())
    private fun rejects(action:()->Unit){assertThrows(IllegalArgumentException::class.java){action()}}
    @Test fun editableDraftCanChangeUntilItIsFrozenAndConfirmedCommandsStayImmutable(){
        val store=OnlineCatalogStore(context);val draft=op().copy(status="EDITING");store.save(draft)
        val changed=draft.copy(catalogJson=OnlineCatalog(listOf(CatalogCategory(UUID.randomUUID().toString(),"Bahan baru",0)),emptyList(),emptyList(),emptyList()).json().toString())
        try{
            store.save(changed);assertEquals(changed,OnlineCatalogStore(context).load(draft.scope))
            assertThrows(IllegalStateException::class.java){store.save(changed.copy(expectedRevision=1))}
            val frozen=changed.copy(status="QUEUED");store.save(frozen)
            assertThrows(IllegalStateException::class.java){store.save(frozen.copy(catalogJson=draft.catalogJson))}
            assertThrows(IllegalStateException::class.java){store.save(changed)}
            store.save(frozen.copy(status="CONFLICT"));store.remove(draft.scope,draft.operationId);assertNull(store.load(draft.scope))
            store.save(draft);store.remove(draft.scope,draft.operationId);assertNull(store.load(draft.scope))
        }finally{store.remove(draft.scope,draft.operationId,true)}
    }
    @Test fun exactJsonTypesAndUnknownFieldsAreRejected(){
        val id=UUID.randomUUID().toString();val category=UUID.randomUUID().toString()
        val c=OnlineCatalog(listOf(CatalogCategory(category,"Bahan",0)),listOf(CatalogIngredient(id,category,"Kopi",0,true)),emptyList(),emptyList())
        val json=c.json();json.getJSONArray("ingredients").getJSONObject(0).put("active","true")
        rejects{OnlineCatalog.parse(json)};rejects{OnlineCatalog.parse(c.json().put("actorId",id))}
        val order=c.json();order.getJSONArray("ingredients").getJSONObject(0).put("sortOrder",0.5);rejects{OnlineCatalog.parse(order)}
        assertEquals(c.validated(),OnlineCatalog.parse(c.json()))
    }
    @Test fun immutableCatalogSurvivesReopenAndGuardsUncertainOrAuthPausedRemoval(){
        val store=OnlineCatalogStore(context);val initial=op();store.save(initial)
        try{
            assertThrows(IllegalStateException::class.java){store.save(initial.copy(expectedRevision=1))}
            val sent=initial.copy(status="SENT",wasSent=true);store.save(sent)
            assertEquals(sent,OnlineCatalogStore(context).load(sent.scope))
            assertThrows(IllegalStateException::class.java){store.remove(sent.scope,sent.operationId)}
            store.save(sent.copy(status="AUTH_REQUIRED"));assertThrows(IllegalStateException::class.java){store.remove(sent.scope,sent.operationId)}
            assertNull(store.load(initial.scope+"-other"));assertEquals(1,store.pendingForAccount(initial.scope.substringBeforeLast('|')+"|"))
        }finally{store.remove(initial.scope,initial.operationId,true)}
    }
    @Test fun backupIsRecoveredAndDefinitiveConflictCanBeDismissed(){
        val store=OnlineCatalogStore(context);val initial=op();store.save(initial)
        val hash=MessageDigest.getInstance("SHA-256").digest(initial.scope.toByteArray()).joinToString(""){"%02x".format(it)}
        val base=File(context.noBackupFilesDir,"online-catalog-operations/$hash.json")
        try{
            assertTrue(base.renameTo(File(base.path+".bak")));File(base.path+".new").writeText("unfinished")
            assertEquals(1,store.pendingForAccount(initial.scope.substringBeforeLast('|')+"|"));assertEquals(initial,store.load(initial.scope))
            store.save(initial.copy(status="CONFLICT",error="CATALOG_CONFLICT"));store.remove(initial.scope,initial.operationId);assertNull(store.load(initial.scope))
        }finally{store.remove(initial.scope,initial.operationId,true)}
    }
}
