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
import java.io.IOException

data class OnlineTeamUi(val outlet:String="",val members:List<OnlineTeamMember> = emptyList(),val cursor:String?=null,
    val canManage:Boolean=false,val busy:Boolean=false,val loaded:Boolean=false,val pending:OnlineTeamOperation?=null,
    val error:String?=null,val notice:String?=null,val authRequired:Boolean=false,val passwordNeeded:Boolean=false,val accessChanged:Boolean=false)

class OnlineTeamViewModel(application:Application):AndroidViewModel(application){
    private val repo=(application as ClosingCountApplication).onlineRepository
    private val mutable=MutableStateFlow(OnlineTeamUi());val state=mutable.asStateFlow()
    private var job:Job?=null;private var epoch=0
    fun cancel(){epoch++;job?.cancel()}
    fun open(outlet:String){cancel();mutable.value=OnlineTeamUi(outlet=outlet);reload()}
    fun reload(more:Boolean=false){
        val s=state.value;if(s.busy||s.accessChanged||(more&&s.cursor==null))return;val run=epoch
        mutable.update{it.copy(busy=true,error=null)}
        job=viewModelScope.launch{
            try{val pending=repo.pendingTeam(s.outlet);if(run==epoch)mutable.update{it.copy(pending=pending)}
                val settings=repo.outletSettings(s.outlet)
                if(settings.role!="ADMIN"){if(run==epoch)mutable.update{it.copy(canManage=false,members=emptyList(),error="Halaman tim hanya tersedia untuk administrator outlet.")};return@launch}
                val page=repo.team(s.outlet,if(more)s.cursor else null)
                if(run==epoch)mutable.update{it.copy(canManage=true,loaded=true,members=(if(more)it.members+page.items else page.items).distinctBy{m->m.id},cursor=page.nextAfter,authRequired=false)}
            }catch(c:CancellationException){throw c}catch(e:Exception){if(run==epoch)fail(e)}finally{if(run==epoch)mutable.update{it.copy(busy=false)}}
        }
    }
    fun create(username:String,name:String,role:String,password:String,prepared:()->Unit){
        if(!state.value.canManage)return
        execute{repo.prepareTeamCreate(state.value.outlet,username,name,role);prepared();repo.syncTeam(state.value.outlet,password)}
    }
    fun edit(member:OnlineTeamMember,role:String,active:Boolean,prepared:()->Unit){
        if(!state.value.canManage)return
        execute{repo.prepareTeamEdit(state.value.outlet,member,role,active);prepared();repo.syncTeam(state.value.outlet)}
    }
    fun retry(password:String?=null){execute{repo.syncTeam(state.value.outlet,password)}}
    fun dismiss(){
        val s=state.value;if(s.busy||s.pending?.status !in setOf("CONFLICT","FAILED"))return
        val run=epoch;mutable.update{it.copy(busy=true,error=null)}
        job=viewModelScope.launch{try{repo.dismissTeam(s.outlet);if(run==epoch)mutable.update{it.copy(pending=null,passwordNeeded=false,notice="Tindakan yang ditolak ditutup. Muat dan review data terbaru.")}}
            catch(c:CancellationException){throw c}catch(e:Exception){if(run==epoch)fail(e)}finally{if(run==epoch)mutable.update{it.copy(busy=false)}}}
    }
    private fun execute(action:suspend()->OnlineTeamReceipt){
        val s=state.value;if(s.busy)return;val run=epoch;val actor=repo.session()?.identity?.id
        mutable.update{it.copy(busy=true,error=null,passwordNeeded=false,notice=null)}
        job=viewModelScope.launch{
            try{val receipt=action();if(run!=epoch)return@launch
                val ownLoss=receipt.member.id==actor&&(!receipt.member.active||receipt.member.role!="ADMIN")
                mutable.update{it.copy(pending=null,notice="Tindakan tim terkonfirmasi.",accessChanged=ownLoss,canManage=!ownLoss,members=if(ownLoss)emptyList()else it.members)}
                if(!ownLoss){val page=repo.team(s.outlet);if(run==epoch)mutable.update{it.copy(members=page.items,cursor=page.nextAfter,loaded=true)}}
            }catch(c:CancellationException){throw c}catch(e:Exception){if(run==epoch)fail(e)}finally{if(run==epoch){
                try{val pending=repo.pendingTeam(s.outlet);if(run==epoch)mutable.update{it.copy(pending=pending)}}catch(c:CancellationException){throw c}catch(e:Exception){fail(e)}
                mutable.update{it.copy(busy=false)}
            }}
        }
    }
    private fun fail(e:Exception){
        val code=(e as? OnlineApiException)?.code;val auth=code in setOf("SESSION_REQUIRED","ACCOUNT_DISABLED","OUTLET_FORBIDDEN")
        val message=when{
            e is TeamPasswordRequiredException->if(state.value.pending?.let{it.kind=="CREATE_ACCOUNT"&&!it.wasSent}==true)
                "Tindakan belum dikirim. Tentukan password awal akun baru untuk melanjutkan ID yang sama."
                else "Tindakan belum ditemukan di server. Ketik ulang password pengajuan pertama untuk melanjutkan ID yang sama."
            code=="LAST_ADMIN_REQUIRED"->"Minimal satu administrator aktif harus dipertahankan."
            code=="USERNAME_UNAVAILABLE"->"Username sudah digunakan. Tutup tindakan yang ditolak dan pilih username lain."
            code=="MEMBERSHIP_CONFLICT"||e is OnlineConflictChangedException->"Data anggota sudah berubah. Tutup tindakan yang ditolak, muat ulang dan review versi terbaru."
            auth->"Sesi atau hak akses perlu diperiksa. Masuk kembali dengan akun yang sama; tindakan tersimpan tetap dipertahankan."
            e is IOException->"Hasil belum terkonfirmasi. Periksa kembali saat koneksi tersedia; hindari membuat tindakan baru yang sama."
            else->"Tindakan belum berhasil. Periksa isian dan coba lagi."
        }
        mutable.update{it.copy(error=message,authRequired=auth,passwordNeeded=e is TeamPasswordRequiredException,
            canManage=if(auth)false else it.canManage,members=if(auth)emptyList()else it.members)}
    }
}
