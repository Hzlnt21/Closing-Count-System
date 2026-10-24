package com.closingcount.app.ui.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closingcount.app.data.online.OnlineTeamMember
import org.json.JSONObject

private fun teamRole(role:String)=when(role){"ADMIN"->"Administrator";"SUPERVISOR"->"Supervisor";else->"Karyawan"}
private fun memberJson(m:OnlineTeamMember)=JSONObject().put("id",m.id).put("username",m.username).put("name",m.name).put("role",m.role).put("active",m.active).put("accountActive",m.accountActive).put("revision",m.revision).toString()
private fun parseMember(raw:String)=JSONObject(raw).let{OnlineTeamMember(it.getString("id"),it.getString("username"),it.getString("name"),it.getString("role"),it.getBoolean("active"),it.getBoolean("accountActive"),it.getLong("revision"))}

@Composable fun OnlineTeamPanel(outlet:String,actor:String,onClose:()->Unit,onLogin:()->Unit,vm:OnlineTeamViewModel=viewModel()){
    val s by vm.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable(outlet,actor){mutableStateOf(false)}
    var editing by rememberSaveable(outlet,actor){mutableStateOf<String?>(null)}
    var passwordDialog by remember{mutableStateOf(false)}
    LaunchedEffect(outlet,actor){vm.open(outlet)}
    LaunchedEffect(s.pending?.operationId){if(s.pending!=null){creating=false;editing=null}}
    DisposableEffect(vm){onDispose{vm.cancel()}}
    fun close(){if(!s.busy)onClose()}
    Dialog(onDismissRequest=::close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        BackHandler{close()}
        Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=::close,enabled=!s.busy,modifier=Modifier.testTag("online.team.close")){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Kembali ke akun")}
                Column{Text("Tim outlet",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Akun individual, tanggung jawab jelas",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }){padding->Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            Icon(Icons.Outlined.Group,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp))
            Text("Orang yang tepat.\nAkses yang sesuai.",fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
            Text("Setiap perubahan akses diperiksa server dan dicatat. Akun nonaktif tidak dapat memakai akses outlet ini.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let{TeamCard{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("online.team.error"))
                if(s.authRequired)TextButton(onClick=onLogin,enabled=!s.busy){Text("Masuk kembali")}}}
            s.notice?.let{Text(it,modifier=Modifier.testTag("online.team.notice"))}
            if(s.accessChanged)TeamCard{
                Text("Akses akunmu sudah diperbarui",fontWeight=FontWeight.Bold)
                Text("Tutup halaman ini untuk memuat ulang akses yang sekarang tersedia.")
                Button(onClick=onClose,modifier=Modifier.fillMaxWidth().testTag("online.team.access.close")){Text("Tutup dan perbarui akun")}
            }
            s.pending?.let{op->val p=JSONObject(op.payload);TeamCard{
                Text("Tindakan tersimpan",fontWeight=FontWeight.Bold,modifier=Modifier.testTag("online.team.pending"))
                Text(if(op.kind=="CREATE_ACCOUNT")"Buat akun ${p.getString("username")}" else "Perbarui akses anggota",fontWeight=FontWeight.SemiBold)
                Text("${teamRole(p.getString("role"))}"+(if(op.kind=="UPDATE_MEMBERSHIP")if(p.getBoolean("active"))" / aktif" else " / nonaktif" else ""))
                Text(if(op.status in setOf("CONFLICT","FAILED"))"Tindakan ditolak. Tutup lalu review versi terbaru untuk membuat tindakan baru." else "Hasil belum terkonfirmasi. Periksa ID yang sama sebelum membuat tindakan lain.",style=MaterialTheme.typography.bodySmall)
                if(op.status in setOf("QUEUED","SENT"))Button(onClick={vm.retry()},enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("online.team.retry")){Text("Periksa hasil tindakan")}
                if(s.passwordNeeded)TextButton(onClick={passwordDialog=true},enabled=!s.busy,modifier=Modifier.testTag("online.team.retry.password.open")){Text(if(op.kind=="CREATE_ACCOUNT"&&!op.wasSent)"Tentukan password akun baru" else "Ketik ulang password pengajuan pertama")}
                if(op.status=="AUTH_REQUIRED"&&!s.authRequired)TextButton(onClick=onLogin,enabled=!s.busy){Text("Masuk kembali")}
                if(op.status in setOf("CONFLICT","FAILED"))TextButton(onClick=vm::dismiss,enabled=!s.busy,modifier=Modifier.testTag("online.team.dismiss")){Text("Tutup tindakan yang ditolak")}
            }}
            if(s.canManage&&!s.accessChanged){
                Button(onClick={creating=true},enabled=!s.busy&&s.pending==null,modifier=Modifier.fillMaxWidth().heightIn(min=49.dp).testTag("online.team.create"),shape=RoundedCornerShape(13.dp)){Text("Tambah anggota")}
                Text("Daftar terakhir dimuat",fontSize=18.sp,fontWeight=FontWeight.Bold)
                s.members.forEach{m->TeamCard{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text(m.name,fontWeight=FontWeight.Bold);Text(m.username,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        if(m.id==actor)Text("Akunmu",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }
                    Text(teamRole(m.role),fontWeight=FontWeight.SemiBold)
                    Text(if(!m.accountActive)"Akun dinonaktifkan" else if(m.active)"Akses outlet aktif" else "Akses outlet nonaktif",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={editing=memberJson(m)},enabled=!s.busy&&s.pending==null,modifier=Modifier.testTag("online.team.edit.${m.id}")){Text("Ubah akses")}
                }}
                if(s.cursor!=null)OutlinedButton(onClick={vm.reload(true)},enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("online.team.more")){Text("Anggota berikutnya")}
            }
            if(!s.accessChanged)TextButton(onClick={vm.reload()},enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("online.team.reload")){Text("Muat ulang tim")}
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }}
        if(creating)CreateTeamDialog(s.busy,s.error,onCancel={creating=false},onCreate={username,name,role,password->vm.create(username,name,role,password){creating=false}})
        editing?.let{raw->EditTeamDialog(parseMember(raw),actor,s.busy,s.error,onCancel={editing=null},onApply={m,role,active->vm.edit(m,role,active){editing=null}})}
        if(passwordDialog)TeamPasswordDialog(s.busy,previouslySent=s.pending?.wasSent?:true,onCancel={passwordDialog=false},onApply={passwordDialog=false;vm.retry(it)})
    }
}

@Composable private fun TeamRolePicker(role:String,onRole:(String)->Unit,enabled:Boolean=true){
    var expanded by remember{mutableStateOf(false)}
    Box{OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("online.team.role")){Text(teamRole(role))}
        DropdownMenu(expanded,onDismissRequest={expanded=false}){listOf("EMPLOYEE","SUPERVISOR","ADMIN").forEach{value->DropdownMenuItem(text={Text(teamRole(value))},onClick={onRole(value);expanded=false},modifier=Modifier.testTag("online.team.role.$value"))}}}
}
@Composable private fun CreateTeamDialog(busy:Boolean,error:String?,onCancel:()->Unit,onCreate:(String,String,String,String)->Unit){
    var username by rememberSaveable{mutableStateOf("")};var name by rememberSaveable{mutableStateOf("")};var role by rememberSaveable{mutableStateOf("EMPLOYEE")}
    var password by remember{mutableStateOf("")};var discard by remember{mutableStateOf(false)}
    val valid=username.trim().lowercase(java.util.Locale.ROOT).matches(Regex("[a-z0-9][a-z0-9._-]{2,63}"))&&name.trim().length in 1..120&&password.codePointCount(0,password.length)>=12&&password.toByteArray().size<=256
    fun cancel(){if(!busy){if(username.isNotEmpty()||name.isNotEmpty()||password.isNotEmpty())discard=true else onCancel()}}
    AlertDialog(onDismissRequest=::cancel,title={Text("Tambah anggota")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Buat akun individual untuk outlet ini. Review nama dan peran sebelum menyimpan.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(username,{username=it},label={Text("Username")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("online.team.username"),supportingText={Text("3-64 huruf/angka, titik, _ atau -")})
        OutlinedTextField(name,{name=it},label={Text("Nama anggota")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("online.team.name"))
        TeamRolePicker(role,{role=it},!busy)
        OutlinedTextField(password,{password=it},label={Text("Password awal")},enabled=!busy,singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth().testTag("online.team.password"),supportingText={Text("Minimal 12 karakter. Tidak disimpan di perangkat.")})
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={onCreate(username,name,role,password)},enabled=valid&&!busy,modifier=Modifier.testTag("online.team.create.confirm")){Text("Buat akun")}},dismissButton={TextButton(onClick=::cancel,enabled=!busy){Text("Batal")}})
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("Buang isian anggota?")},text={Text("Isian ini belum menjadi tindakan tersimpan.")},confirmButton={TextButton(onClick=onCancel){Text("Buang isian")}},dismissButton={TextButton(onClick={discard=false}){Text("Kembali mengedit")}})
}
@Composable private fun EditTeamDialog(member:OnlineTeamMember,actor:String,busy:Boolean,error:String?,onCancel:()->Unit,onApply:(OnlineTeamMember,String,Boolean)->Unit){
    var role by rememberSaveable(member.id,member.revision){mutableStateOf(member.role)}
    var active by rememberSaveable(member.id,member.revision){mutableStateOf(member.active)}
    var review by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!busy)onCancel()},title={Text("Akses ${member.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(13.dp)){
        Text(member.username,style=MaterialTheme.typography.bodySmall);TeamRolePicker(role,{role=it},!busy)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Akses outlet aktif",Modifier.weight(1f));Switch(active,{active=it},enabled=!busy,modifier=Modifier.testTag("online.team.active"))}
        if(!member.accountActive)Text("Akun ini dinonaktifkan secara global. Mengaktifkan akses outlet tidak mengaktifkan akun global.",style=MaterialTheme.typography.bodySmall)
        if(member.id==actor)Text("Ini akunmu. Penurunan peran atau penonaktifan akan mengubah halaman yang dapat kamu akses.",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={review=true},enabled=!busy&&(role!=member.role||active!=member.active),modifier=Modifier.testTag("online.team.edit.review")){Text("Periksa perubahan")}},dismissButton={TextButton(onClick={if(!busy)onCancel()},enabled=!busy){Text("Batal")}})
    if(review)AlertDialog(onDismissRequest={review=false},title={Text("Terapkan akses baru?")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(member.name,fontWeight=FontWeight.Bold);Text("${teamRole(role)} / ${if(active)"aktif" else "nonaktif"}")
        Text("Perubahan dicatat atas akunmu. Data anggota yang berubah sejak direview harus diperiksa ulang.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={onApply(member,role,active)},enabled=!busy,modifier=Modifier.testTag("online.team.edit.confirm")){Text("Ya, ubah akses")}},dismissButton={TextButton(onClick={review=false},enabled=!busy){Text("Periksa lagi")}})
}
@Composable private fun TeamPasswordDialog(busy:Boolean,previouslySent:Boolean,onCancel:()->Unit,onApply:(String)->Unit){
    var password by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onCancel,title={Text("Lanjutkan pembuatan akun")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(if(previouslySent)"Ketik password yang sama dengan pengajuan pertama. Nama, peran dan ID tindakan tetap sama." else "Tentukan password awal untuk akun baru ini. Nama, peran dan ID tindakan tetap sama. Password tidak disimpan di perangkat.")
        OutlinedTextField(password,{password=it},label={Text(if(previouslySent)"Password pengajuan pertama" else "Password awal akun baru")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("online.team.retry.password"))
    }},confirmButton={TextButton(onClick={onApply(password)},enabled=!busy&&password.codePointCount(0,password.length)>=12&&password.toByteArray().size<=256,modifier=Modifier.testTag("online.team.retry.password.confirm")){Text("Periksa dan lanjutkan")}},dismissButton={TextButton(onClick=onCancel){Text("Batal")}})
}
@Composable private fun TeamCard(content:@Composable ColumnScope.()->Unit){Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}
