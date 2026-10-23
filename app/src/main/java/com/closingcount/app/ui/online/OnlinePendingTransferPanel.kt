package com.closingcount.app.ui.online

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closingcount.app.data.transfer.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun pendingDate(value:String)=runCatching{LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("id-ID")))}.getOrDefault(value)
private fun pendingTime(value:String)=runCatching{Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm",Locale.forLanguageTag("id-ID")))}.getOrDefault(value)
private fun kindLabel(value:String)=when(value){"SETTINGS"->"Pengaturan outlet";"TEAM_CREATE"->"Pembuatan akun";"TEAM_UPDATE"->"Perubahan akses anggota";"CATALOG"->"Katalog & resep";"LIFECYCLE"->"Pengajuan / review closing";else->"Draft angka closing"}
private fun dispositionLabel(value:String)=when(value){"READY","PROPOSAL"->"Perlu ditinjau";"ALREADY_RECORDED"->"Referensi tercatat di server";"ALREADY_RECOVERED_LOCALLY"->"Pernah dipulihkan di perangkat ini";"BLOCKED_ROLE"->"Peran akun belum sesuai";"BLOCKED_STATE"->"Status closing belum sesuai";"BLOCKED_USERNAME"->"Username sudah dipakai di outlet ini";else->value}
private fun closingLabel(value:String)=when(value){"DRAFT"->"Draft";"SUBMITTED"->"Menunggu review";"LOCKED"->"Terkunci";else->value}
private fun roleLabel(value:String)=when(value){"ADMIN"->"Administrator";"SUPERVISOR"->"Supervisor";else->"Karyawan"}

@Composable fun OnlinePendingTransferPanel(outletId:String,onClose:()->Unit,onLogin:()->Unit,onContinue:(RecoveryDestination)->Unit,
    vm:OnlinePendingTransferViewModel=viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val locked=s.busy||s.waitingOpen||s.waitingSave!=null
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"),vm::finishSave)
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),vm::finishOpen)
    LaunchedEffect(outletId){vm.open(outletId)}
    fun back(){if(!locked){if(s.catalogSide!=null||s.quantity!=null||s.intent!=null||s.export!=null||s.importing!=null||s.file!=null)vm.back() else if(vm.close())onClose()}}
    val title=when {s.catalogSide!=null->if(s.catalogSide=="file")"Katalog dari file" else "Katalog server";s.quantity!=null->"Review angka closing";s.intent!=null->"Review tindakan";s.export!=null->"Cadangan siap disimpan";s.importing!=null->"Periksa file cadangan";s.file!=null->"Pekerjaan dalam cadangan";else->"Cadangan & pemulihan"}
    Dialog(onDismissRequest=::back,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler(onBack=::back)
        Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
            Surface(color=MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=::back,enabled=!locked,modifier=Modifier.testTag("online.pending.back")){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Kembali")}
                    Column(Modifier.weight(1f)){Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Closing bersama · data perangkatmu",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
        }) {padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("online.pending.page"),
                contentPadding=PaddingValues(horizontal=22.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(s.busy)item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp);Text(s.activity)}}
                s.message?.let{message->item{PendingNotice(message,s.failed){vm.clearMessage()}}}
                s.destination?.let{destination->item{
                    PendingPaper {Text("Draft siap ditinjau",fontWeight=FontWeight.Bold);Text("Lanjutkan di halaman terkait. Perubahan baru dikirim ketika kamu memilih sinkronisasi atau publikasi.",style=MaterialTheme.typography.bodyMedium)
                        PendingButton(if(destination.kind in setOf("CLOSING","LIFECYCLE"))"Buka draft closing" else "Lanjut ke "+kindLabel(destination.kind).lowercase(),"online.pending.continue",!locked){if(vm.close())onContinue(destination)}}
                }}
                when {
                    s.catalogSide!=null->{
                        item{PendingNotice("${if(s.catalogSide=="file")"Isi katalog dalam cadangan" else "Data katalog server saat review"}. Periksa kategori, bahan, menu, serta resep sebelum melanjutkan.")}
                        val lines=if(s.catalogSide=="file")s.fileCatalog else s.currentCatalog
                        if(lines.isEmpty())item{Text("Katalog ini belum berisi data.")}
                        items(lines){line->PendingPaper{Text(line.title,fontWeight=FontWeight.SemiBold);Text(line.detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    }
                    s.quantity!=null->{
                        val review=s.quantity!!
                        item{PendingPaper{Text(pendingDate(review.date),fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Text("Server: ${closingLabel(review.serverState)} · putaran revisi ${review.serverGeneration}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(review.fileGeneration!=review.serverGeneration||review.rows.any{row->row.sourceGenerations.any{it!=review.serverGeneration}})Text("Sebagian draft berasal dari putaran revisi sebelumnya. Periksa ulang setiap angka sebelum membuat draft pada data terbaru.",color=MaterialTheme.colorScheme.error)
                            Text("Angka tidak dijumlahkan otomatis. Tentukan total outlet yang memang ingin dipakai.")}}
                        items(review.rows,key={it.menuId}){row->PendingPaper{
                            Text(row.serverName,fontWeight=FontWeight.Bold,fontSize=18.sp)
                            if(row.fileName!=row.serverName)Text("Nama di file: ${row.fileName}",style=MaterialTheme.typography.bodySmall)
                            PendingStatus(dispositionLabel(row.disposition))
                            PendingValue("Angka server terakhir yang diketahui file",row.fileBase.toString())
                            PendingValue("Angka server sekarang",row.serverQuantity.toString())
                            PendingValue("Angka draft dari file",row.desired.toString(),emphasis=row.disposition=="PROPOSAL")
                        }}
                        item{
                            if(review.rows.any{it.disposition=="PROPOSAL"}){
                                PendingNotice("Ini hanya membuat draft di perangkat. Buka dan periksa draft tersebut, lalu tekan sinkronisasi saat siap mengirim.")
                                Spacer(Modifier.height(12.dp));PendingButton("Buat draft pada data terbaru","online.pending.apply.quantity",!locked){vm.applyQuantity(review.id)}
                            }else PendingNotice("Semua referensi sudah ditangani. Tidak ada angka yang akan dimasukkan kembali ke antrean.")
                            PendingSecondary("Tutup tinjauan","online.pending.review.close",!locked,vm::back)
                        }
                    }
                    s.intent!=null->{
                        val review=s.intent!!
                        item{PendingPaper{Text(kindLabel(review.kind),fontSize=24.sp,fontWeight=FontWeight.Bold);PendingStatus(dispositionLabel(review.disposition))
                            Text("Peran saat diperiksa: ${roleLabel(review.currentRole)}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            review.currentState?.let{Text("Status closing: ${closingLabel(it)} · putaran revisi ${review.currentGeneration}")}
                            if(review.sourceRevision!=null&&review.currentRevision!=null&&review.sourceRevision!=review.currentRevision)Text("Data server sudah berubah sejak cadangan dibuat. Usulan ini memakai versi server yang baru saja kamu periksa.")
                            if(review.sourceGeneration!=null&&review.currentGeneration!=null&&review.sourceGeneration!=review.currentGeneration)Text("Tindakan berasal dari putaran revisi sebelumnya. Pastikan tindakan dan alasannya masih tepat.",color=MaterialTheme.colorScheme.error)
                        }}
                        items(s.values){value->PendingPaper{Text(value.label,fontWeight=FontWeight.Bold);PendingValue("Dari file",value.file,true);value.current?.let{PendingValue("Server sekarang",it)}}}
                        if(review.kind=="CATALOG")item{PendingPaper{
                            Text("Katalog dipulihkan sebagai draft editable",fontWeight=FontWeight.Bold);Text("Kategori, bahan, menu dan resep tetap tersedia. Publikasi dilakukan melalui halaman katalog setelah review.")
                            PendingSecondary("Lihat seluruh katalog file","online.pending.catalog.file",!locked){vm.showCatalog("file")}
                            if(review.currentPayloadJson!=null)PendingSecondary("Bandingkan dengan katalog server","online.pending.catalog.current",!locked){vm.showCatalog("current")}
                        }}
                        item{
                            if(review.disposition=="READY"){
                                PendingNotice(if(review.passwordRequired)"Password tidak dibawa dalam file. Masukkan password akun baru di halaman tim setelah draft disiapkan." else "Tindakan disiapkan sebagai draft lokal. Belum ada perubahan yang dikirim ke server.")
                                Spacer(Modifier.height(12.dp));PendingButton("Siapkan draft tindakan","online.pending.apply.intent",!locked){vm.applyIntent(review.id)}
                            }else PendingNotice(when(review.disposition){"BLOCKED_ROLE"->"Gunakan akun asal yang memiliki hak akses sesuai. File tetap tersedia untuk ditinjau nanti.";"BLOCKED_STATE"->"Tindakan ini belum cocok dengan status closing sekarang. Alasan dan isi file tetap tersimpan.";"BLOCKED_USERNAME"->"Pembuatan akun dengan username yang sama tidak dilanjutkan. Periksa anggota yang sudah ada di halaman tim.";else->"Referensi ini sudah ditangani. Informasi di file tidak dimasukkan kembali sebagai tindakan baru."})
                            PendingSecondary("Periksa data terbaru","online.pending.review.refresh",!locked){if(review.kind=="LIFECYCLE")vm.compareClosing(requireNotNull(review.closingId)) else vm.compareAdmin(review.originalOperationId)}
                            PendingSecondary("Tutup tinjauan","online.pending.review.close",!locked,vm::back)
                        }
                    }
                    s.export!=null->{val review=s.export!!
                        item{PendingPaper{Text("Draft outlet ini",fontSize=24.sp,fontWeight=FontWeight.Bold);Text(review.outletLabel,fontWeight=FontWeight.SemiBold);Text("${review.closings} closing · ${review.entries} item angka")
                            if(review.adminKinds.isNotEmpty())Text(review.adminKinds.joinToString("\n"){kindLabel(it)})
                            Text("Akun: ${review.accountLabel}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Isi file dibekukan saat cadangan ini disiapkan. Draft di aplikasi tetap utuh.")}}
                        item{PendingButton("Pilih lokasi penyimpanan","online.pending.export.save",!locked){vm.beginSave(review.id)?.let{try{save.launch(it)}catch(_:Exception){vm.pickerUnavailable()}}}
                            PendingSecondary("Batal","online.pending.export.cancel",!locked,vm::back)}
                    }
                    s.importing!=null->{val review=s.importing!!
                        item{PendingFileSummary(review.summary)}
                        item{PendingNotice("Simpan salinan ini ke inbox untuk ditinjau. Belum ada login, penggantian draft, atau pengiriman data dari tindakan ini.")}
                        item{PendingButton("Simpan ke inbox","online.pending.import.accept",!locked){vm.acceptImport(review.id)}
                            PendingSecondary("Batal","online.pending.import.cancel",!locked,vm::back)}
                    }
                    s.model!=null&&s.file!=null->{val model=s.model!!;val file=s.file!!
                        item{PendingFileSummary(file)}
                        if(file.accountId!=s.account||file.origin!=s.origin)item{PendingNotice("File ini berasal dari akun atau layanan lain. Masuk dengan akun asal untuk membandingkan dan menyiapkan draft.",true,"Masuk akun asal"){if(vm.close())onLogin()}}
                        item{Text("Pilih pekerjaan untuk ditinjau",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Setiap pekerjaan dibandingkan dengan data server terbaru sebelum diterapkan.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        items(model.closings,key={it.id}){closing->PendingPaper{
                            Text(pendingDate(closing.date),fontWeight=FontWeight.Bold,fontSize=18.sp)
                            Text(if(closing.lifecycle==null)"${closing.entries.size} item angka" else "Pengajuan / review closing",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            PendingSecondary("Bandingkan dengan server","online.pending.compare.${closing.id}",!locked&&file.accountId==s.account&&file.origin==s.origin){vm.compareClosing(closing.id)}
                        }}
                        items(model.admin,key={it.operationId}){admin->PendingPaper{
                            Text(kindLabel(admin.kind),fontWeight=FontWeight.Bold,fontSize=18.sp)
                            PendingSecondary("Tinjau tindakan","online.pending.compare.${admin.operationId}",!locked&&file.accountId==s.account&&file.origin==s.origin){vm.compareAdmin(admin.operationId)}
                        }}
                        item{PendingSecondary("Kembali ke inbox","online.pending.inbox.back",!locked,vm::back)}
                    }
                    else->{
                        item{PendingPaper(accent=true){Icon(Icons.Outlined.Backup,null,Modifier.size(30.dp));Text("Lanjutkan pekerjaan dengan tenang",fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Text("Cadangkan draft closing, pengaturan, tim, dan katalog. Riwayat yang sudah tersinkron tetap mengikuti server.")}}
                        item{PendingButton("Cadangkan draft outlet","online.pending.export",!locked&&outletId.isNotBlank(),vm::prepareExport)
                            PendingSecondary("Buka file cadangan","online.pending.import",!locked){if(vm.beginOpen())try{open.launch(arrayOf("application/json","text/plain","application/octet-stream"))}catch(_:Exception){vm.pickerUnavailable()}}}
                        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Inbox pemulihan",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
                            IconButton(onClick=vm::reload,enabled=!locked,modifier=Modifier.testTag("online.pending.reload")){Icon(Icons.Outlined.Refresh,"Muat ulang inbox")}}
                            Text("File di inbox belum mengubah data aplikasi. Tinjau pekerjaan satu per satu saat kamu siap.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        if(s.entries.isEmpty()&&!s.busy)item{PendingPaper{Icon(Icons.Outlined.FolderOpen,null);Text("Belum ada salinan di inbox",fontWeight=FontWeight.SemiBold);Text("Buka file cadangan untuk menyimpannya di sini sebelum melakukan review.",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                        items(s.entries,key={it.fingerprint}){entry->PendingPaper{
                            if(entry.summary!=null){Text(entry.summary.outletLabel,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(entry.summary.accountLabel);Text(pendingTime(entry.summary.createdAt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${entry.summary.closings} closing · ${entry.summary.adminKinds.size} tindakan")
                                PendingSecondary("Lihat pekerjaan","online.pending.inbox.${entry.fingerprint}",!locked){vm.inspectFile(entry.fingerprint)}
                            }else{Text(if(entry.problem=="INCOMPLETE")"Salinan belum selesai" else "Salinan perlu diperiksa",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.error)
                                Text("File ini belum dapat dipakai. Salinan lain dan draft aplikasi tetap tersedia.")}
                            Text("ID file ${entry.fingerprint.take(12)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick={vm.prepareRemoval(entry.fingerprint)},enabled=!locked,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("online.pending.remove.${entry.fingerprint}")){Text("Hapus salinan dari inbox")}
                        }}
                    }
                }
            }
        }
        s.removal?.let{review->AlertDialog(onDismissRequest=vm::cancelRemoval,title={Text("Hapus salinan inbox?")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            review.summary?.let{Text(it.outletLabel,fontWeight=FontWeight.Bold);Text(it.accountLabel)}
            Text("Hanya salinan file di inbox ini yang dihapus. Draft yang sudah dibuat, catatan pemulihan, file asal, dan data server tetap utuh.")}},
            confirmButton={TextButton(onClick={vm.remove(review.id)},enabled=!s.busy,modifier=Modifier.testTag("online.pending.remove.confirm")){Text("Hapus salinan")}},
            dismissButton={TextButton(onClick=vm::cancelRemoval,enabled=!s.busy){Text("Batal")}})}
    }
}

@Composable private fun PendingPaper(accent:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=if(accent)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor=if(accent)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,tonalElevation=if(accent)0.dp else 1.dp) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable private fun PendingButton(text:String,tag:String,enabled:Boolean,onClick:()->Unit){Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag(tag)){Text(text)}}
@Composable private fun PendingSecondary(text:String,tag:String,enabled:Boolean,onClick:()->Unit){OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().padding(top=10.dp).heightIn(min=48.dp).testTag(tag)){Text(text)}}
@Composable private fun PendingStatus(text:String){Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer){Text(text,Modifier.padding(horizontal=10.dp,vertical=7.dp),style=MaterialTheme.typography.labelMedium)}}
@Composable private fun PendingValue(label:String,value:String,emphasis:Boolean=false){Column(verticalArrangement=Arrangement.spacedBy(3.dp)){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);SelectionContainer{Text(value,style=if(emphasis)MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,fontWeight=if(emphasis)FontWeight.Bold else FontWeight.Normal,color=if(emphasis)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}}}
@Composable private fun PendingNotice(text:String,error:Boolean=false,actionLabel:String="Tutup pesan",onAction:(()->Unit)?=null){Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=if(error)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=if(error)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(text,style=MaterialTheme.typography.bodyMedium);onAction?.let{TextButton(onClick=it,colors=ButtonDefaults.textButtonColors(contentColor=if(error)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary)){Text(actionLabel)}}}}}
@Composable private fun PendingFileSummary(file:PendingRecoverySummary){var details by remember(file.sha256){mutableStateOf(false)}
    PendingPaper{Text(file.outletLabel,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(file.accountLabel,fontWeight=FontWeight.SemiBold)
        Text(pendingTime(file.createdAt),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${file.closings} closing · ${file.entries} item angka · ${file.adminKinds.size} tindakan")
        file.firstDate?.let{Text(if(it==file.lastDate)pendingDate(it) else "${pendingDate(it)} – ${pendingDate(requireNotNull(file.lastDate))}")}
        if(file.adminKinds.isNotEmpty())Text(file.adminKinds.joinToString("\n"){kindLabel(it)},style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick={details=!details},modifier=Modifier.testTag("online.pending.file.details")){Text(if(details)"Tutup detail file" else "Detail file")}
        if(details){PendingValue("Layanan asal",file.origin);PendingValue("ID file",file.sha256);Text("${file.bytes} byte · perangkat asal ${file.sourceDeviceId.take(8)}",style=MaterialTheme.typography.bodySmall)}
    }
}
