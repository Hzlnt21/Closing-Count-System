package com.closingcount.app.ui.online

import android.app.Application
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.online.OnlineApiException
import com.closingcount.app.data.online.OnlineCatalog
import com.closingcount.app.data.online.OnlineRepository
import com.closingcount.app.data.transfer.*
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RecoveryDestination(val kind:String,val outlet:String,val closing:String?=null,val date:String?=null)
data class RecoveryValue(val label:String,val file:String,val current:String?,val fileLabel:String="Dari file",val currentLabel:String="Server sekarang")
data class RecoveryCatalogLine(val title:String,val detail:String)
data class OnlinePendingTransferUi(val opened:Boolean=false,val outlet:String="",val account:String="",val origin:String="",
    val busy:Boolean=false,val activity:String="",val message:String?=null,val failed:Boolean=false,
    val entries:List<PendingRecoveryEntry> =emptyList(),val file:PendingRecoverySummary?=null,val model:OnlinePendingBackup?=null,
    val export:PendingBackupExportReview?=null,val importing:PendingRecoveryReview?=null,
    val quantity:QuantityRecoveryReview?=null,val intent:IntentRecoveryReview?=null,val values:List<RecoveryValue> =emptyList(),
    val fileCatalog:List<RecoveryCatalogLine> =emptyList(),val currentCatalog:List<RecoveryCatalogLine> =emptyList(),
    val catalogSide:String?=null,val removal:PendingRecoveryRemoval?=null,val destination:RecoveryDestination?=null,
    val waitingOpen:Boolean=false,val waitingSave:String?=null)

// Production passes Application only; the internal QA constructor supplies an application wrapper.
class OnlinePendingTransferViewModel private constructor(application:Application,@field:SuppressLint("StaticFieldLeak") private val app:Context,
    private val repo:OnlineRepository):AndroidViewModel(application) {
    constructor(application:Application):this(application,application,(application as ClosingCountApplication).onlineRepository)
    internal constructor(application:Application,context:Context,repository:OnlineRepository,isolatedQa:Boolean):this(application,context,repository){check(isolatedQa)}
    private val mutable=MutableStateFlow(OnlinePendingTransferUi());val state=mutable.asStateFlow()
    private var epoch=0;private var job:Job?=null
    private val stateLock=Any();private val taskEpoch=ThreadLocal<Int>()
    private var inbox=OnlinePendingRecoveryInbox(app)
    private var exporter=OnlinePendingBackupTransfer(app.contentResolver,repo,OnlineBackupDeviceIdentity(app))
    private var importer=OnlinePendingRecoveryTransfer(app.contentResolver,inbox)
    private var quantity=OnlineQuantityRecovery(inbox,repo)
    private var intent=OnlineIntentRecovery(inbox,repo)
    private fun resetControllers() {
        inbox=OnlinePendingRecoveryInbox(app);exporter=OnlinePendingBackupTransfer(app.contentResolver,repo,OnlineBackupDeviceIdentity(app))
        importer=OnlinePendingRecoveryTransfer(app.contentResolver,inbox);quantity=OnlineQuantityRecovery(inbox,repo);intent=OnlineIntentRecovery(inbox,repo)
    }
    fun open(outlet:String) = synchronized(stateLock) {
        val session=repo.session();val account=session?.identity?.id.orEmpty();val origin=session?.origin.orEmpty()
        val old=state.value
        if(old.opened&&old.outlet==outlet&&old.account==account&&old.origin==origin)return@synchronized
        epoch++;job?.cancel();resetControllers()
        mutable.value=OnlinePendingTransferUi(opened=true,outlet=outlet,account=account,origin=origin);reload()
    }
    fun close():Boolean = synchronized(stateLock) {
        if(locked())return@synchronized false
        epoch++;job?.cancel();resetControllers();mutable.value=OnlinePendingTransferUi();true
    }
    private fun locked()=state.value.busy||state.value.waitingOpen||state.value.waitingSave!=null
    fun reload(){task("Membaca inbox"){val entries=inbox.inventory();publishUi{it.copy(entries=entries)}}}
    fun clearMessage(){publish{it.copy(message=null)}}
    fun prepareExport() {val outlet=state.value.outlet;if(outlet.isBlank())return
        task("Menyiapkan cadangan") {
            clearReviews();val review=exporter.prepare(outlet)
            if(review.closings==0&&review.adminKinds.isEmpty()) {
                exporter.discard(review.id);publishUi{it.copy(message="Tidak ada draft tertunda untuk outlet ini. Data yang sudah tersinkron tetap tersedia di server.")}
            }else publishUi{it.copy(export=review)}
        }
    }
    fun beginSave(id:String):String? {
        val s=state.value;if(locked()||s.export?.id!=id)return null
        publish{it.copy(waitingSave=id)};return s.export.filename
    }
    fun finishSave(uri:Uri?) {
        val id=state.value.waitingSave
        publish{it.copy(waitingSave=null)}
        if(id==null){publish{it.copy(message="Pilihan penyimpanan sebelumnya sudah berakhir. Siapkan cadangan kembali.",failed=true)};return}
        task("Menyimpan cadangan") {
            if(uri==null){exporter.discard(id);publishUi{it.copy(export=null,message="Penyimpanan dibatalkan. Draft tetap utuh.")}}
            else {exporter.write(id,uri);publishUi{it.copy(export=null,message="Cadangan draft berhasil disimpan. Simpan file ini di tempat yang kamu percayai.")}}
        }
    }
    fun beginOpen():Boolean {if(locked())return false;publish{it.copy(waitingOpen=true)};return true}
    fun pickerUnavailable(){publish{it.copy(waitingOpen=false,waitingSave=null,failed=true,message="Pemilih file belum dapat dibuka. Coba kembali dari halaman ini.")}}
    fun finishOpen(uri:Uri?) {
        if(!state.value.waitingOpen){publish{it.copy(message="Pilihan file sebelumnya sudah berakhir. Pilih file kembali.",failed=true)};return}
        publish{it.copy(waitingOpen=false)}
        if(uri==null){publish{it.copy(message="Pemilihan file dibatalkan. Tidak ada data yang diubah.")};return}
        task("Memeriksa file") {clearReviews();val review=importer.prepare(uri);publishUi{it.copy(importing=review)}}
    }
    fun acceptImport(id:String) {if(state.value.importing?.id!=id)return
        task("Menyimpan ke inbox") {
            val result=importer.accept(id);val decoded=inbox.read(result.summary.sha256);val entries=inbox.inventory()
            publishUi{it.copy(importing=null,file=result.summary,model=decoded.model,entries=entries,
                message=if(result.duplicate)"Salinan yang sama sudah ada di inbox. Pilih pekerjaan yang ingin ditinjau." else "File tersimpan di inbox. Belum ada draft yang diterapkan atau dikirim.")}
        }
    }
    fun inspectFile(fingerprint:String){task("Membuka cadangan") {
        clearReviews();val decoded=inbox.read(fingerprint)
        publishUi{it.copy(file=OnlinePendingRecoveryInbox.summary(decoded),model=decoded.model,destination=null)}
    }}
    fun compareClosing(id:String){val file=state.value.file?:return;val closing=state.value.model?.closings?.singleOrNull{it.id==id}?:return
        task("Memeriksa data server") {
            clearReviews()
            if(closing.lifecycle==null){val review=quantity.prepare(file.sha256,id);publishUi{it.copy(quantity=review)}}
            else {val review=intent.prepareLifecycle(file.sha256,id);val values=intentValues(review);publishUi{it.copy(intent=review,values=values)}}
        }
    }
    fun compareAdmin(id:String){val file=state.value.file?:return
        task("Memeriksa data server") {
            clearReviews();val review=intent.prepareAdmin(file.sha256,id)
            val desired=if(review.kind=="CATALOG")catalogLines(OnlineCatalog.parse(JSONObject(review.sourcePayloadJson))) else emptyList()
            val current=if(review.kind=="CATALOG"&&review.currentPayloadJson!=null)catalogLines(OnlineCatalog.parse(JSONObject(review.currentPayloadJson))) else emptyList()
            val values=intentValues(review)
            publishUi{it.copy(intent=review,values=values,fileCatalog=desired,currentCatalog=current)}
        }
    }
    fun applyQuantity(id:String){val review=state.value.quantity?.takeIf{it.id==id}?:return
        task("Menyiapkan draft closing") {
            val result=quantity.apply(id)
            publishUi{it.copy(quantity=null,destination=if(result.newOperations.isEmpty())null else RecoveryDestination("CLOSING",review.outletId,review.closingId,review.date),
                message=if(result.newOperations.isEmpty())"Referensi ini sudah ditangani. Tidak ada draft baru." else "Draft closing siap. Buka dan periksa angkanya, lalu tekan sinkronisasi saat siap mengirim.")}
        }
    }
    fun applyIntent(id:String){val review=state.value.intent?.takeIf{it.id==id}?:return
        task("Menyiapkan draft tindakan") {
            val result=intent.apply(id)
            val closing=state.value.model?.closings?.firstOrNull{it.id==review.closingId}
            publishUi{it.copy(intent=null,values=emptyList(),fileCatalog=emptyList(),currentCatalog=emptyList(),
                destination=if(result.newOperationId==null)null else RecoveryDestination(review.kind,review.outletId,review.closingId,closing?.date),
                message=if(result.newOperationId==null)"Referensi ini sudah ditangani. Tidak ada draft baru." else "Draft siap di halaman terkait. Belum ada perubahan yang dikirim ke server.")}
        }
    }
    fun back(){if(locked())return
        if(state.value.catalogSide!=null){publish{it.copy(catalogSide=null)};return}
        task("Menutup tinjauan") {
            val s=state.value;val hadReview=s.export!=null||s.importing!=null||s.quantity!=null||s.intent!=null
            clearReviews()
            if(!hadReview){val entries=inbox.inventory();publishUi{it.copy(file=null,model=null,destination=null,entries=entries)}}
        }
    }
    fun showCatalog(side:String){if(!locked()&&side in setOf("file","current"))publish{it.copy(catalogSide=side)}}
    fun prepareRemoval(fingerprint:String){task("Memeriksa salinan") {val review=inbox.prepareRemoval(fingerprint);publishUi{it.copy(removal=review)}}}
    fun cancelRemoval(){val id=state.value.removal?.id?:return;task("Membatalkan penghapusan"){inbox.cancelRemoval(id);publishUi{it.copy(removal=null)}}}
    fun remove(id:String){if(state.value.removal?.id!=id)return;task("Menghapus salinan inbox") {
        val hash=inbox.remove(id);clearReviews();val entries=inbox.inventory()
        publishUi{it.copy(removal=null,file=if(it.file?.sha256==hash)null else it.file,model=if(it.file?.sha256==hash)null else it.model,
            entries=entries,destination=null,message="Salinan di inbox dihapus. Draft yang sudah dibuat dan data server tetap utuh.")}
    }}
    private data class Handles(val state:OnlinePendingTransferUi,val exporter:OnlinePendingBackupTransfer,
        val importer:OnlinePendingRecoveryTransfer,val quantity:OnlineQuantityRecovery,val intent:OnlineIntentRecovery)
    private fun ensureEpoch(){val run=taskEpoch.get();if(run!=null&&(run!=epoch))throw CancellationException("Transfer context changed")}
    private fun publish(action:(OnlinePendingTransferUi)->OnlinePendingTransferUi)=synchronized(stateLock){
        check(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) { "Transfer UI state must be published on the main thread" }
        ensureEpoch();mutable.update(action)
    }
    // Keep parsing, storage and network work on IO. Await the main-thread state
    // handoff so epoch checks and successive actions retain their original order.
    private suspend fun publishUi(action:(OnlinePendingTransferUi)->OnlinePendingTransferUi)=withContext(Dispatchers.Main.immediate){publish(action)}
    private suspend fun clearReviews() {
        val handles=synchronized(stateLock){ensureEpoch();Handles(state.value,exporter,importer,quantity,intent)}
        val s=handles.state;s.export?.let{handles.exporter.discard(it.id)};s.importing?.let{handles.importer.discard(it.id)}
        s.quantity?.let{handles.quantity.discard(it.id)};s.intent?.let{handles.intent.discard(it.id)}
        publishUi{it.copy(export=null,importing=null,quantity=null,intent=null,values=emptyList(),fileCatalog=emptyList(),currentCatalog=emptyList(),catalogSide=null,destination=null)}
    }
    private fun task(label:String,action:suspend()->Unit) {
        if(locked())return
        val run=epoch;publish{it.copy(busy=true,activity=label,message=null,failed=false)}
        job=viewModelScope.launch(taskEpoch.asContextElement(run)) {
            try{withContext(Dispatchers.IO){action()}}
            catch(c:CancellationException){throw c}
            catch(e:Exception){if(run==epoch)publish{it.copy(export=null,importing=null,quantity=null,intent=null,removal=null,catalogSide=null,message=message(e),failed=true)}}
            finally{if(run==epoch)publish{it.copy(busy=false,activity="")}}
        }
    }
    private fun message(error:Exception):String {
        if(error is OnlineApiException)return when(error.status){401->"Sesi berakhir. Masuk kembali dengan akun asal file.";403->"Akses saat ini tidak mengizinkan tindakan ini. Periksa akun dan outlet.";409->"Data server berubah. Muat dan tinjau kembali.";else->"Server belum dapat diperiksa. Coba lagi saat koneksi tersedia."}
        if(error is SecurityException)return "File tidak dapat diakses. Pilih kembali dan izinkan akses."
        val detail=error.message.orEmpty()
        return when {
            "RECOVERY_LOCAL_PENDING" in detail->"Masih ada draft pada pekerjaan yang sama. Periksa atau selesaikan draft tersebut sebelum memulihkan usulan lain."
            "RECOVERY_REVIEW_CHANGED" in detail||"RECOVERY_SESSION_CHANGED" in detail->"Akun atau data berubah setelah review. Periksa kembali sebelum membuat draft."
            "RECOVERY_DATE_CONFLICT" in detail->"Identitas closing dan tanggal tidak cocok. Periksa cadangan dan data outlet."
            "RECOVERY_PROVENANCE_CONFLICT" in detail||"RECOVERY_OPERATION_ORDER" in detail->"Referensi pekerjaan tidak cocok. Gunakan salinan cadangan asli."
            "Inbox pemulihan penuh" in detail->"Inbox penuh. Hapus salinan yang tidak diperlukan, lalu pilih file kembali."
            "RECOVERY_NOT_WRITABLE" in detail->"Closing belum dapat diedit. Periksa statusnya sebelum memulihkan draft."
            "RECOVERY_NOT_READY" in detail->"Tindakan ini belum memenuhi syarat. Periksa status dan hak akses terbaru."
            error is android.util.MalformedJsonException->"File cadangan tidak valid. Pilih file cadangan draft online Closing Count."
            error is java.io.IOException->"File atau layanan belum dapat diakses. Periksa koneksi atau pilih lokasi file lain."
            error is IllegalArgumentException||error is org.json.JSONException->"File atau akun tidak sesuai. Gunakan cadangan draft online Closing Count dan akun asalnya."
            else->"Tindakan belum berhasil. Data yang ada tetap tersedia; periksa kembali sebelum mencoba."
        }
    }
    private fun intentValues(review:IntentRecoveryReview):List<RecoveryValue> {
        val file=JSONObject(review.sourcePayloadJson);val current=review.currentPayloadJson?.let(::JSONObject)
        fun text(value:JSONObject?,key:String)=value?.opt(key)?.takeIf{it!=JSONObject.NULL}?.toString()
        fun value(label:String,key:String)=RecoveryValue(label,text(file,key).orEmpty(),text(current,key))
        return when(review.kind){
            "SETTINGS"->listOf(value("Nama outlet","name"),value("Zona waktu","timezone"),RecoveryValue("Batas hari kerja",clock(file.getInt("cutoffMinutes")),current?.optInt("cutoffMinutes")?.let(::clock)))
            "TEAM_CREATE"->listOf(RecoveryValue("Username",file.getString("username"),current?.optBoolean("existsInOutlet")?.let{if(it)"Sudah terdaftar di outlet ini" else "Belum terdaftar di outlet ini"},currentLabel="Status di outlet ini"),value("Nama pengguna","displayName"),RecoveryValue("Peran",role(file.getString("role")),null))
            "TEAM_UPDATE"->listOf(RecoveryValue("Anggota yang dirujuk file",current?.optString("displayName")?.takeIf{it.isNotBlank()}?:"Nama belum tersedia",current?.optString("username"),fileLabel="Nama dari server",currentLabel="Username dari server"),
                RecoveryValue("Peran",role(file.getString("role")),current?.optString("role")?.let(::role)),RecoveryValue("Akses outlet",if(file.getBoolean("active"))"Aktif" else "Nonaktif",current?.optBoolean("active")?.let{if(it)"Aktif" else "Nonaktif"}))
            "LIFECYCLE"->listOf(RecoveryValue("Tindakan",action(file.getString("action")),null),RecoveryValue("Alasan",file.getString("reason").ifBlank{"Tidak diperlukan"},null))
            else->emptyList()
        }
    }
    private fun catalogLines(catalog:OnlineCatalog):List<RecoveryCatalogLine> {
        val ingredients=catalog.ingredients.associateBy{it.id};val ic=catalog.ingredientCategories.associateBy{it.id};val mc=catalog.menuCategories.associateBy{it.id}
        return catalog.ingredientCategories.sortedBy{it.sortOrder}.map{RecoveryCatalogLine(it.name,"Kategori bahan · urutan ${it.sortOrder}")}+
            catalog.menuCategories.sortedBy{it.sortOrder}.map{RecoveryCatalogLine(it.name,"Kategori menu · urutan ${it.sortOrder}")}+
            catalog.ingredients.sortedBy{it.sortOrder}.map{RecoveryCatalogLine(it.name,"Bahan · ${ic[it.categoryId]?.name.orEmpty()} · ${if(it.active)"Aktif" else "Nonaktif"} · urutan ${it.sortOrder}")}+
            catalog.menus.sortedBy{it.sortOrder}.map{RecoveryCatalogLine(it.name,"Menu · ${mc[it.categoryId]?.name.orEmpty()} · ${if(it.active)"Aktif" else "Nonaktif"} · urutan ${it.sortOrder}\nResep: "+it.ingredientIds.joinToString(", "){id->ingredients[id]?.name.orEmpty()})}
    }
    private fun clock(minutes:Int)="%02d:%02d".format(minutes/60,minutes%60)
    private fun role(value:String)=when(value){"ADMIN"->"Administrator";"SUPERVISOR"->"Supervisor";else->"Karyawan"}
    private fun action(value:String)=when(value){"submit"->"Ajukan untuk review";"return"->"Kembalikan untuk koreksi";"lock"->"Kunci closing";"reopen"->"Buka kembali closing";else->value}
}
