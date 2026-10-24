package com.closingcount.app.ui.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.closingcount.app.data.online.*
import java.util.UUID

private val catalogSections=listOf("Menu","Bahan","Kategori menu","Kategori bahan")
private fun catalogName(c:OnlineCatalog,kind:Int,id:String)=when(kind){0->c.menus.find{it.id==id}?.name;1->c.ingredients.find{it.id==id}?.name;2->c.menuCategories.find{it.id==id}?.name;else->c.ingredientCategories.find{it.id==id}?.name}.orEmpty()
private fun catalogIds(c:OnlineCatalog,kind:Int):List<String> = when(kind){
    0->c.menus.sortedWith(compareBy<CatalogMenu>{it.sortOrder}.thenBy{it.name}).map{it.id}
    1->c.ingredients.sortedWith(compareBy<CatalogIngredient>{it.sortOrder}.thenBy{it.name}).map{it.id}
    2->c.menuCategories.sortedWith(compareBy<CatalogCategory>{it.sortOrder}.thenBy{it.name}).map{it.id}
    else->c.ingredientCategories.sortedWith(compareBy<CatalogCategory>{it.sortOrder}.thenBy{it.name}).map{it.id}}

@Composable fun OnlineCatalogPanel(outlet:String,actor:String,onClose:()->Unit,onLogin:()->Unit,vm:OnlineCatalogViewModel=viewModel()){
    val s by vm.state.collectAsStateWithLifecycle()
    var section by rememberSaveable(outlet,actor){mutableIntStateOf(0)}
    var query by rememberSaveable(outlet,actor){mutableStateOf("")}
    var editing by rememberSaveable(outlet,actor){mutableStateOf<String?>(null)}
    var deleting by rememberSaveable(outlet,actor){mutableStateOf<String?>(null)}
    var review by remember{mutableStateOf(false)};var discard by remember{mutableStateOf(false)}
    LaunchedEffect(outlet,actor){vm.open(outlet)}
    DisposableEffect(vm){onDispose{vm.cancel()}}
    val c=s.working;val editable=s.canManage&&s.pending?.status=="EDITING"&&!s.busy
    fun close(){if(!s.busy)onClose()}
    Dialog(onDismissRequest=::close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        BackHandler{close()}
        Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=::close,enabled=!s.busy,modifier=Modifier.testTag("online.catalog.close")){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Kembali ke akun")}
            Column{Text("Katalog & resep",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Satu katalog, perhitungan konsisten",style=MaterialTheme.typography.bodySmall)}
        }}){padding->LazyColumn(Modifier.testTag("online.catalog.list").fillMaxSize().padding(padding).imePadding().padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{Spacer(Modifier.height(12.dp));Text("Menu yang rapi.\nClosing yang pasti.",fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)}
            item{Text("Resep menghitung satu penggunaan tiap bahan per menu terjual. Publikasi berlaku untuk closing baru; riwayat tersimpan tetap utuh.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(s.busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            s.error?.let{message->item{Text(message,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("online.catalog.error"))}}
            s.notice?.let{message->item{Text(message,modifier=Modifier.testTag("online.catalog.notice"))}}
            if(s.authRequired)item{TextButton(onClick=onLogin,enabled=!s.busy){Text("Masuk kembali")}}
            if(!s.canManage&&s.version!=null&&!s.authRequired)item{Text("Mode baca. Hanya administrator outlet yang dapat mengubah katalog.")}
            item{Text("Versi server ${s.version?.revision?:"belum dimuat"}",fontWeight=FontWeight.SemiBold)}
            s.pending?.let{op->item{Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(if(op.status=="EDITING")"Draft perangkat tersimpan" else "Publikasi tersimpan",fontWeight=FontWeight.Bold)
                Text("Berdasarkan versi ${op.expectedRevision}. "+if(op.status=="EDITING")"Perubahan belum memengaruhi server." else "Isi pengajuan dikunci sampai hasilnya jelas.")
                if(op.status=="EDITING"){
                    Button(onClick={review=true},enabled=editable,modifier=Modifier.fillMaxWidth().testTag("online.catalog.publish")){Text("Review & publikasikan")}
                    TextButton(onClick={discard=true},enabled=!s.busy,modifier=Modifier.testTag("online.catalog.discard")){Text("Buang draft dan muat versi terbaru")}
                }else{
                    if(op.status in setOf("QUEUED","SENT"))Button(onClick=vm::retry,enabled=!s.busy,modifier=Modifier.testTag("online.catalog.retry")){Text("Periksa hasil publikasi")}
                    if(op.status in setOf("CONFLICT","FAILED"))TextButton(onClick={discard=true},enabled=!s.busy){Text("Tutup pengajuan yang ditolak")}
                }
            }}}}
            if(s.pending==null&&s.canManage&&s.version!=null)item{Button(onClick=vm::start,enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("online.catalog.start")){Text("Mulai draft perubahan")}}
            if(c!=null){
                item {
                    Column {
                        catalogSections.chunked(2).forEachIndexed { row, labels ->
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                labels.forEachIndexed { col, label ->
                                    val index=row*2+col
                                    FilterChip(selected=section==index,onClick={section=index;query=""},label={Text(label)},modifier=Modifier.weight(1f).testTag("online.catalog.section.$index"))
                                }
                            }
                        }
                    }
                }

                item{OutlinedTextField(query,{query=it},label={Text("Cari ${catalogSections[section].lowercase()}")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("online.catalog.search"))}
                if(editable)item{OutlinedButton(onClick={editing="$section|${UUID.randomUUID()}"},modifier=Modifier.fillMaxWidth().testTag("online.catalog.add")){Text("Tambah ${catalogSections[section].lowercase()}")}}
                val rows=catalogIds(c,section).filter{catalogName(c,section,it).contains(query,true)}
                if(rows.isEmpty())item{Text("Belum ada data yang sesuai.")}
                items(rows,key={"$section|$it"}){id->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(catalogName(c,section,id),fontWeight=FontWeight.Bold,fontSize=18.sp)
                    when(section){0->{val m=c.menus.first{it.id==id};Text("${c.menuCategories.first{it.id==m.categoryId}.name} / "+if(m.active)"Aktif" else "Nonaktif");Text(m.ingredientIds.joinToString(" + "){key->c.ingredients.first{it.id==key}.name},style=MaterialTheme.typography.bodySmall)}
                        1->{val i=c.ingredients.first{it.id==id};Text("${c.ingredientCategories.first{it.id==i.categoryId}.name} / "+if(i.active)"Aktif" else "Nonaktif")}
                        else->Text("Kategori ${if(section==2)"menu" else "bahan"}")}
                    if(editable)Row{TextButton(onClick={editing="$section|$id"},modifier=Modifier.testTag("online.catalog.edit.$id")){Text("Ubah")};TextButton(onClick={deleting="$section|$id"},modifier=Modifier.testTag("online.catalog.delete.$id")){Text("Hapus")}}
                }}}
            }
            item{TextButton(onClick=vm::reload,enabled=!s.busy,modifier=Modifier.testTag("online.catalog.reload")){Text("Muat ulang versi server")};Spacer(Modifier.height(28.dp))}
        }}
        if(editing!=null&&c!=null){val parts=editing!!.split('|');CatalogRowForm(parts[0].toInt(),parts[1],c,s.busy,s.error,onClose={editing=null},onSave={next->vm.change(next){editing=null}})}
        if(deleting!=null&&c!=null){val parts=deleting!!.split('|');val kind=parts[0].toInt();val id=parts[1]
            val next=when(kind){0->c.copy(menus=c.menus.filter{it.id!=id});1->c.copy(ingredients=c.ingredients.filter{it.id!=id});2->c.copy(menuCategories=c.menuCategories.filter{it.id!=id});else->c.copy(ingredientCategories=c.ingredientCategories.filter{it.id!=id})}
            val references=when(kind){1->c.menus.filter{id in it.ingredientIds}.map{it.name};2->c.menus.filter{it.categoryId==id}.map{it.name};3->c.ingredients.filter{it.categoryId==id}.map{it.name};else->emptyList()}
            val blocked=if(references.isNotEmpty())"Masih dipakai: ${references.take(3).joinToString()}. Ubah referensinya terlebih dahulu." else null
            val valid=runCatching{next.validated()};AlertDialog(onDismissRequest={if(!s.busy)deleting=null},title={Text("Hapus dari draft?")},text={Column{Text(catalogName(c,kind,id));Text(blocked?:valid.exceptionOrNull()?.message?:"Perubahan baru berlaku setelah katalog dipublikasikan. Riwayat closing tetap utuh.")}},confirmButton={TextButton(onClick={vm.change(next){deleting=null}},enabled=!s.busy&&valid.isSuccess,modifier=Modifier.testTag("online.catalog.delete.confirm")){Text("Hapus dari draft")}},dismissButton={TextButton(onClick={deleting=null},enabled=!s.busy){Text("Batal")}})
        }
        if(review&&c!=null){AlertDialog(onDismissRequest={review=false},title={Text("Publikasikan katalog?")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Berdasarkan versi ${s.pending?.expectedRevision}");Text("${c.menus.size} menu / ${c.ingredients.size} bahan\n${c.menuCategories.size} kategori menu / ${c.ingredientCategories.size} kategori bahan");Text("Katalog akan diperiksa server. Closing yang sudah dibuat tetap memakai nama dan resep sebelumnya.")}},confirmButton={TextButton(onClick={review=false;vm.publish()},modifier=Modifier.testTag("online.catalog.publish.confirm")){Text("Ya, publikasikan")}},dismissButton={TextButton(onClick={review=false}){Text("Periksa lagi")}})}
        if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("Tutup draft ini?")},text={Text("Perubahan draft yang belum dipublikasikan akan dibuang. Katalog server terbaru akan dimuat tanpa menggabungkan isian lama.")},confirmButton={TextButton(onClick={discard=false;vm.discard()},modifier=Modifier.testTag("online.catalog.discard.confirm")){Text("Tutup dan muat ulang")}},dismissButton={TextButton(onClick={discard=false}){Text("Batal")}})
    }
}

@Composable private fun CatalogRowForm(kind:Int,id:String,c:OnlineCatalog,busy:Boolean,serverError:String?,onClose:()->Unit,onSave:(OnlineCatalog)->Unit){
    val menu=c.menus.find{it.id==id};val ingredient=c.ingredients.find{it.id==id};val categories=if(kind==0)c.menuCategories else c.ingredientCategories
    var name by rememberSaveable(kind,id){mutableStateOf(catalogName(c,kind,id))}
    val initialOrder=when(kind){0->menu?.sortOrder;1->ingredient?.sortOrder;2->c.menuCategories.find{it.id==id}?.sortOrder;else->c.ingredientCategories.find{it.id==id}?.sortOrder}?:0
    var order by rememberSaveable(kind,id){mutableStateOf(initialOrder.toString())}
    var category by rememberSaveable(kind,id){mutableStateOf(menu?.categoryId?:ingredient?.categoryId?:categories.firstOrNull()?.id.orEmpty())}
    var active by rememberSaveable(kind,id){mutableStateOf(menu?.active?:ingredient?.active?:true)}
    var selected by rememberSaveable(kind,id){mutableStateOf(menu?.ingredientIds?.joinToString(",").orEmpty())}
    var expanded by remember{mutableStateOf(false)};var search by rememberSaveable(kind,id){mutableStateOf("")};var leave by remember{mutableStateOf(false)}
    val ids=selected.split(',').filter{it.isNotEmpty()};val orderValue=order.toIntOrNull()?.takeIf{it in 0..1000000&&order.matches(Regex("[0-9]{1,7}"))}
    val next=runCatching{
        require(name.trim().length in 1..120){"Isi nama 1 sampai 120 karakter."};require(orderValue!=null){"Isi urutan 0 sampai 1000000."}
        when(kind){0->c.copy(menus=c.menus.filter{it.id!=id}+CatalogMenu(id,category,name,orderValue,active,ids))
            1->c.copy(ingredients=c.ingredients.filter{it.id!=id}+CatalogIngredient(id,category,name,orderValue,active))
            2->c.copy(menuCategories=c.menuCategories.filter{it.id!=id}+CatalogCategory(id,name,orderValue))
            else->c.copy(ingredientCategories=c.ingredientCategories.filter{it.id!=id}+CatalogCategory(id,name,orderValue))}.validated()
    }
    val dirty=name!=catalogName(c,kind,id)||order!=initialOrder.toString()||category!=(menu?.categoryId?:ingredient?.categoryId?:categories.firstOrNull()?.id.orEmpty())||active!=(menu?.active?:ingredient?.active?:true)||selected!=menu?.ingredientIds?.joinToString(",").orEmpty()
    fun close(){if(!busy){if(dirty)leave=true else onClose()}}
    AlertDialog(onDismissRequest=::close,title={Text("${if(catalogName(c,kind,id).isEmpty())"Tambah" else "Ubah"} ${catalogSections[kind].lowercase()}")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(name,{name=it},label={Text("Nama")},modifier=Modifier.fillMaxWidth().testTag("online.catalog.form.name"),singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done))
        OutlinedTextField(order,{order=it},label={Text("Urutan tampilan")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("online.catalog.form.order"),singleLine=true,enabled=!busy)
        if(kind<2){Box{OutlinedButton(onClick={expanded=true},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("online.catalog.form.category")){Text(categories.find{it.id==category}?.name?:"Buat kategori terlebih dahulu")};DropdownMenu(expanded,onDismissRequest={expanded=false}){categories.sortedBy{it.sortOrder}.forEach{row->DropdownMenuItem(text={Text(row.name)},onClick={category=row.id;expanded=false},modifier=Modifier.testTag("online.catalog.category.${row.id}"))}}}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Aktif",Modifier.weight(1f));Switch(active,{active=it},enabled=!busy,modifier=Modifier.testTag("online.catalog.form.active"))}}
        if(kind==0){Text("Bahan resep (${"%d".format(ids.size)}/100)",fontWeight=FontWeight.SemiBold);Text("Satu penggunaan setiap bahan untuk satu menu terjual.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(search,{search=it},label={Text("Cari bahan resep")},modifier=Modifier.fillMaxWidth().testTag("online.catalog.form.search"),singleLine=true)
            LazyColumn(Modifier.testTag("online.catalog.recipe.list").fillMaxWidth().height(220.dp)){items(c.ingredients.filter{it.name.contains(search,true)}.sortedBy{it.sortOrder},key={it.id}){row->
                Row(Modifier.fillMaxWidth().toggleable(value=row.id in ids,enabled=!busy,role=Role.Checkbox,onValueChange={selected=if(row.id in ids)(ids-row.id).joinToString(",")else(ids+row.id).joinToString(",")}).padding(vertical=5.dp).testTag("online.catalog.recipe.${row.id}"),verticalAlignment=Alignment.CenterVertically){Checkbox(row.id in ids,onCheckedChange=null);Column{Text(row.name);if(!row.active)Text("Bahan nonaktif",color=MaterialTheme.colorScheme.error)}}
            }}}

        next.exceptionOrNull()?.message?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("online.catalog.form.validation"))}
        serverError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={next.getOrNull()?.let(onSave)},enabled=!busy&&next.isSuccess,modifier=Modifier.testTag("online.catalog.form.save")){Text("Simpan ke draft")}},dismissButton={TextButton(onClick=::close,enabled=!busy){Text("Batal")}})
    if(leave)AlertDialog(onDismissRequest={leave=false},title={Text("Isian belum disimpan")},text={Text("Isian pada form ini belum masuk draft. Tetap mengedit atau buang isian form?")},confirmButton={TextButton(onClick=onClose){Text("Buang isian form")}},dismissButton={TextButton(onClick={leave=false}){Text("Kembali mengedit")}})
}
