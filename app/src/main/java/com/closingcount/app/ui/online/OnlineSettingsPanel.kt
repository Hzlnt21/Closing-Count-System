package com.closingcount.app.ui.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private fun zoneLabel(zone:String)=when(zone){"Asia/Jakarta"->"WIB - Jakarta";"Asia/Makassar"->"WITA - Makassar";"Asia/Jayapura"->"WIT - Jayapura";else->zone}

@Composable fun OnlineSettingsPanel(outletId: String, onClose: () -> Unit, onLogin: () -> Unit,
    vm: OnlineSettingsViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var leave by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(false) }
    var reviewed by remember { mutableStateOf<SettingsReview?>(null) }
    fun close() { if(!s.busy){if(s.dirty)leave=true else onClose()} }
    LaunchedEffect(outletId){vm.open(outletId)}
    DisposableEffect(vm){onDispose{vm.cancel()}}
    Dialog(onDismissRequest=::close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        BackHandler{close()}
        Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=::close,enabled=!s.busy,modifier=Modifier.testTag("online.settings.close")){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Kembali ke akun")}
                Column{Text("Pengaturan outlet",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Aturan hari kerja tim",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }){padding->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement=Arrangement.spacedBy(18.dp)){
                Text("Satu aturan.\nSeluruh tim selaras.",fontSize=29.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
                Text("Nama dan pergantian hari bisnis digunakan untuk closing baru. Tanggal serta snapshot closing lama tetap dipertahankan.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                s.error?.let { SettingsCard {
                    Text(it,color=MaterialTheme.colorScheme.error)
                    if(s.authRequired)TextButton(onClick=onLogin,enabled=!s.busy,modifier=Modifier.testTag("online.settings.login")){Text("Masuk kembali")}
                } }
                s.pending?.let { op -> SettingsCard {
                    Text("Perubahan tersimpan",fontWeight=FontWeight.Bold,modifier=Modifier.testTag("online.settings.pending"))
                    Text(when(op.status){"CONFLICT","FAILED"->"Tindakan ditolak. Tutup tindakan ini dan periksa pengaturan terbaru sebelum mencoba lagi."
                        "AUTH_REQUIRED"->"Menunggu pemeriksaan sesi atau izin akun. Jangan kirim atas nama akun lain."
                        else->"Hasil belum terkonfirmasi. Aplikasi akan memeriksa tindakan yang sama, bukan membuat perubahan baru."},style=MaterialTheme.typography.bodySmall)
                    if(op.status in setOf("QUEUED","SENT"))Button(onClick=vm::retry,enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("online.settings.retry")){Text("Periksa konfirmasi")}
                    if(op.status=="AUTH_REQUIRED" && !s.authRequired)TextButton(onClick=onLogin,enabled=!s.busy){Text("Masuk kembali")}
                    if(op.status in setOf("CONFLICT","FAILED"))TextButton(onClick=vm::dismiss,enabled=!s.busy,modifier=Modifier.testTag("online.settings.dismiss")){Text("Tutup tindakan yang ditolak")}
                } }
                SettingsCard {
                    Icon(Icons.Outlined.Storefront,null,tint=MaterialTheme.colorScheme.primary)
                    Text("Identitas outlet",fontSize=18.sp,fontWeight=FontWeight.Bold)
                    OutlinedTextField(s.name,vm::name,label={Text("Nama outlet")},readOnly=!s.editable,
                        modifier=Modifier.fillMaxWidth().testTag("online.settings.name"),singleLine=true,isError=s.name.trim().length>120,
                        supportingText={Text("${s.name.trim().length}/120 karakter")},shape=RoundedCornerShape(13.dp))
                }
                SettingsCard {
                    Icon(Icons.Outlined.Schedule,null,tint=MaterialTheme.colorScheme.primary)
                    Text("Hari bisnis",fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text("Zona waktu",style=MaterialTheme.typography.labelLarge)
                    var zones by remember {mutableStateOf(false)}
                    Box{
                        if(s.editable)OutlinedButton(onClick={zones=true},modifier=Modifier.fillMaxWidth().testTag("online.settings.zone")){Text(zoneLabel(s.timezone))}
                        else Surface(shape=RoundedCornerShape(13.dp),color=MaterialTheme.colorScheme.surfaceContainer){
                            Text(zoneLabel(s.timezone),Modifier.fillMaxWidth().padding(15.dp).testTag("online.settings.zone"),
                                color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded=zones,onDismissRequest={zones=false}){
                            (listOf("Asia/Jakarta","Asia/Makassar","Asia/Jayapura")+s.timezone).distinct().forEach{zone->
                                DropdownMenuItem(text={Text(zoneLabel(zone))},onClick={vm.timezone(zone);zones=false})
                            }
                        }
                    }
                    OutlinedTextField(s.cutoff,vm::cutoff,label={Text("Pergantian hari (HH:mm)")},readOnly=!s.editable,
                        singleLine=true,modifier=Modifier.fillMaxWidth().testTag("online.settings.cutoff"),isError=s.cutoffMinutes==null,
                        supportingText={Text("00:00 sampai 23:59, mengikuti zona waktu outlet")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text),shape=RoundedCornerShape(13.dp))
                    Text("Sebelum jam ini, tanggal bisnis masih hari sebelumnya. Jam 00:00 berarti tanggal berganti pada tengah malam.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.snapshot?.let { snapshot->
                    Text("Versi pengaturan ${snapshot.revision}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(snapshot.role!="ADMIN")Text("Hanya administrator outlet yang dapat mengubah aturan ini.",style=MaterialTheme.typography.bodyMedium)
                    else if(s.pending==null)Button(onClick={reviewed=SettingsReview(s.name.trim(),s.timezone,requireNotNull(s.cutoffMinutes),snapshot.revision)},
                        enabled=s.editable&&s.valid&&s.dirty,shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag("online.settings.save")){Text("Periksa perubahan")}
                }
                s.notice?.let {Text(it,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("online.settings.notice"))}
                TextButton(onClick={if(s.dirty)reload=true else vm.open(outletId,discard=true)},enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("online.settings.reload")){Text("Muat pengaturan terbaru")}
                Spacer(Modifier.navigationBarsPadding().height(12.dp))
            }
        }
        reviewed?.let { review->AlertDialog(onDismissRequest={reviewed=null},title={Text("Terapkan aturan baru?")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(review.name,fontWeight=FontWeight.Bold);Text("${zoneLabel(review.timezone)} / ${s.cutoff}")
                Text("Berlaku untuk closing baru. Closing lama tidak diubah. Perubahan yang masuk dari admin lain harus diperiksa ulang.")
            }
        },confirmButton={TextButton(onClick={reviewed=null;vm.save(review)},modifier=Modifier.testTag("online.settings.confirm")){Text("Ya, simpan pengaturan")}},dismissButton={TextButton(onClick={reviewed=null}){Text("Periksa lagi")}}) }
        if(leave||reload)AlertDialog(onDismissRequest={leave=false;reload=false},title={Text("Perubahan belum disimpan")},text={Text("Isian ini belum menjadi tindakan tersimpan. Buang isian untuk melanjutkan?")},
            confirmButton={TextButton(onClick={val loading=reload;leave=false;reload=false;if(loading)vm.open(outletId,discard=true)else{vm.forgetForm();onClose()}}){Text("Buang isian")}},
            dismissButton={TextButton(onClick={leave=false;reload=false}){Text("Kembali mengedit")}})
    }
}

@Composable private fun SettingsCard(content:@Composable ColumnScope.()->Unit){
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(13.dp),content=content)
    }
}
