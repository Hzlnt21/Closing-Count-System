package com.closingcount.app.ui.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.online.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class OnlineCatalogUi(val outlet:String="",val version:OnlineCatalogVersion?=null,val working:OnlineCatalog?=null,
    val pending:OnlineCatalogOperation?=null,val canManage:Boolean=false,val busy:Boolean=false,val error:String?=null,
    val notice:String?=null,val authRequired:Boolean=false)

class OnlineCatalogViewModel(app:Application):AndroidViewModel(app){
    private val repo=(app as ClosingCountApplication).onlineRepository
    private val mutable=MutableStateFlow(OnlineCatalogUi());val state=mutable.asStateFlow()
    private var epoch=0;private var job:Job?=null
    fun cancel(){epoch++;job?.cancel()}
    fun open(outlet:String){cancel();mutable.value=OnlineCatalogUi(outlet=outlet,canManage=repo.session()?.identity?.outlets?.firstOrNull{it.id==outlet}?.role=="ADMIN");reload()}
    private suspend fun refresh(outlet:String,run:Int){
        val pending=repo.pendingCatalog(outlet)
        if(run==epoch)mutable.update{it.copy(pending=pending,working=pending?.let{p->OnlineCatalog.parse(JSONObject(p.catalogJson))})}
        val settings=repo.outletSettings(outlet);val version=repo.catalog(outlet)
        if(run==epoch)mutable.update{it.copy(version=version,canManage=settings.role=="ADMIN",authRequired=false,
            working=pending?.let{p->OnlineCatalog.parse(JSONObject(p.catalogJson))}?:version.catalog)}
    }
    private fun action(block:suspend (String,Int)->Unit){
        val s=state.value;if(s.busy)return;val run=epoch;mutable.update{it.copy(busy=true,error=null)}
        job=viewModelScope.launch{try{block(s.outlet,run)}catch(c:CancellationException){throw c}catch(e:Exception){if(run==epoch){
            val auth=(e as? OnlineApiException)?.status in setOf(401,403)
            val message=when{auth->"Sesi atau akses perlu diperiksa. Masuk kembali dengan akun yang sama. Draft tetap tersimpan."
                e is OnlineConflictChangedException||(e as? OnlineApiException)?.code=="CATALOG_CONFLICT"->"Katalog server sudah berubah. Draft tidak digabung otomatis; periksa versi terbaru sebelum membuat draft pengganti."
                e is IllegalArgumentException->e.message?:"Periksa isian katalog."
                else->"Belum berhasil dikonfirmasi. Draft tersimpan tetap dipertahankan; coba lagi saat koneksi tersedia."}
            mutable.update{it.copy(error=message,authRequired=auth,canManage=if(auth)false else it.canManage)}
        }}finally{if(run==epoch){try{val p=repo.pendingCatalog(s.outlet);if(run==epoch)mutable.update{it.copy(pending=p)}}catch(_:Exception){}
            mutable.update{it.copy(busy=false)}}}}
    }
    fun reload(){action{outlet,run->refresh(outlet,run)}}
    fun start(){val v=state.value.version?:return;if(!state.value.canManage)return;action{outlet,run->repo.startCatalogDraft(outlet,v);refresh(outlet,run)}}
    fun change(catalog:OnlineCatalog,saved:()->Unit){if(!state.value.canManage||state.value.pending?.status!="EDITING")return
        action{outlet,run->repo.editCatalogDraft(outlet,catalog);if(run==epoch){mutable.update{it.copy(working=catalog.validated(),notice="Perubahan disimpan di draft perangkat.")};saved()}}}
    fun publish(){val s=state.value;val op=s.pending?:return;if(!s.canManage||op.status!="EDITING")return
        action{outlet,run->repo.prepareCatalog(outlet,op.expectedRevision,requireNotNull(s.working));repo.syncCatalog(outlet)
            refresh(outlet,run);if(run==epoch)mutable.update{it.copy(notice="Katalog terpublikasi. Closing lama tetap memakai snapshot sebelumnya.")}}}
    fun retry(){action{outlet,run->repo.syncCatalog(outlet);refresh(outlet,run);if(run==epoch)mutable.update{it.copy(notice="Publikasi katalog terkonfirmasi.")}}}
    fun discard(){action{outlet,run->repo.dismissCatalog(outlet);refresh(outlet,run);if(run==epoch)mutable.update{it.copy(notice="Draft ditutup. Versi server terbaru sudah dimuat.")}}}
}
