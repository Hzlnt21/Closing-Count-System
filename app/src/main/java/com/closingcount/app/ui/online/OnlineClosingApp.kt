package com.closingcount.app.ui.online

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closingcount.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF11263B)
private val Blue = Color(0xFF0877AE)
private val Soft = Color(0xFFF6F9FC)
private val Rounded = RoundedCornerShape(20.dp)
private fun displayDate(date: String) = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id-ID"))) }.getOrDefault(date)
private fun stateLabel(state: String) = when (state) { "DRAFT" -> "Draft bersama"; "SUBMITTED" -> "Dikirim untuk review"; "LOCKED" -> "Terkunci"; else -> "Belum dibuka" }

@Composable
fun OnlineClosingApp(onExit: () -> Unit, vm: OnlineViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Ringkasan") }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var conflict by remember { mutableStateOf<OnlineMenuUi?>(null) }
    var lifecycleReview by remember { mutableStateOf<Pair<String, OnlineUiState>?>(null) }
    LaunchedEffect(s.identity?.id) { if (s.identity != null) tab = "Ringkasan" }
    LaunchedEffect(tab, s.identity?.id, s.outletId, s.loginVisible, s.historyDetail?.id, s.frozenReport != null) {
        if (tab == "Riwayat" && s.identity != null && !s.loginVisible) {
            val detail = s.historyDetail
            if (detail == null) vm.loadHistory()
            else if (s.frozenReport == null && !s.detailLoading) vm.inspectHistory(detail)
        }
    }
    val pageScroll = remember(tab, s.closingId, s.historyDetail?.id, s.frozenReport != null) { androidx.compose.foundation.ScrollState(0) }
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF86D0F3), onPrimary = Ink, background = Color(0xFF0E1E2C),
        surface = Color(0xFF142838), surfaceContainer = Color(0xFF1C3344), surfaceContainerHigh = Color(0xFF203B4D), secondaryContainer = Color(0xFF24475D),
        onSecondaryContainer = Color(0xFFD8EFFB), onSurface = Color(0xFFE5EFF6), onBackground = Color(0xFFE5EFF6))
    else lightColorScheme(primary = Blue, onPrimary = Color.White, background = Soft, surface = Color.White,
        surfaceContainer = Color(0xFFEDF5FA), surfaceContainerHigh = Color(0xFFF1F7FB), secondaryContainer = Color(0xFFDDEFF9), onSecondaryContainer = Color(0xFF12648C),
        onSurface = Ink, onBackground = Ink, onSurfaceVariant = Color(0xFF52677A), outline = Color(0xFF7890A3))
    fun navigate(action: () -> Unit) { if (!s.working) { if (s.dirty) pendingNavigation = action else action() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.pause() }
    DisposableEffect(vm) { onDispose { vm.pause() } }
    BackHandler { navigate {
        when { s.loginVisible && s.identity != null -> vm.cancelLogin()
            tab == "Riwayat" && s.frozenReport != null -> vm.closeFrozenReport()
            tab == "Riwayat" && s.historyDetail != null -> vm.closeHistoryDetail()
            else -> onExit() }
    } }
    MaterialTheme(colorScheme = scheme) {
        Scaffold(containerColor = scheme.background, topBar = {
            Surface(color = scheme.background) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navigate(onExit) }, enabled = !s.working) {
                        Icon(Icons.Outlined.ArrowBack, "Kembali ke data perangkat")
                    }
                    Image(painterResource(R.drawable.closing_count_symbol), null, Modifier.size(width = 43.dp, height = 32.dp))
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text("Closing Count", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(s.identity?.outlets?.firstOrNull { it.id == s.outletId }?.name ?: "Closing bersama, satu tim", fontSize = 11.sp,
                            color = scheme.onSurfaceVariant, maxLines = 2)
                    }
                    if (s.identity != null && !s.loginVisible) IconButton(onClick = { navigate { tab = "Akun" } }) {
                        Icon(Icons.Outlined.AccountCircle, "Buka akun")
                    }
                }
            }
        }, bottomBar = {
            if (s.identity != null && !s.loginVisible) NavigationBar(containerColor = scheme.surface) {
                listOf("Ringkasan" to Icons.Outlined.Home, "Input" to Icons.Outlined.EditNote,
                    "Review" to Icons.Outlined.FactCheck, "Riwayat" to Icons.Outlined.History, "Akun" to Icons.Outlined.PersonOutline).forEach { (name, icon) ->
                    NavigationBarItem(selected = tab == name, onClick = { navigate { tab = name } },
                        icon = { Icon(icon, null) }, label = { Text(name, fontSize = 11.sp) }, modifier = Modifier.testTag("online.nav.$name"))
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight().verticalScroll(pageScroll)
                    .padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (!s.initialized) { CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)); Text("Menyiapkan data perangkat…") }
                    else if (s.loginVisible || s.identity == null) {
                        LoginContent(s, vm)
                    } else {
                        s.error?.let { MessageCard(it, error = true) {
                            if (s.authRequired) TextButton(onClick = { navigate { vm.showLogin() } }) { Text("Masuk kembali") }
                        } }
                        when (tab) {
                            "Akun" -> AccountContent(s, vm, onNavigate = ::navigate)
                            "Riwayat" -> HistoryContent(s, vm) { row -> navigate { vm.openHistory(row); tab = "Ringkasan" } }
                            "Review" -> ReviewContent(s, vm, onAction = { action -> navigate { lifecycleReview = action to s } })
                            else -> {
                                OutletDate(s, onOutlet = { id -> navigate { vm.selectOutlet(id) } }, onDate = { date -> navigate { vm.selectDate(date) } })
                                if (s.closingId == null) {
                                    Text("Siap merapikan\nhari yang produktif?", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                                    Paper {
                                        Icon(Icons.Outlined.Today, null, Modifier.size(30.dp), tint = scheme.primary)
                                        Text("Belum ada closing tersimpan untuk tanggal ini.", fontWeight = FontWeight.SemiBold)
                                        Text("Hubungkan jaringan untuk membuka closing bersama. Data tanggal lain tetap tersedia di Riwayat.", style = MaterialTheme.typography.bodySmall)
                                        Button(onClick = vm::openClosing, enabled = !s.working && s.outletId.isNotEmpty(),
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("online.open"), shape = RoundedCornerShape(13.dp)) {
                                            Text(if (s.working) "Membuka…" else "Buka closing")
                                        }
                                    }
                                } else if (tab == "Input") {
                                    Text("Catat menu terjual.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    Text("Isi total penjualan outlet, bukan tambahan per karyawan.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                    SyncStatus(s, vm::sync)
                                    if (s.closingState != "DRAFT") MessageCard("Closing ${stateLabel(s.closingState).lowercase()}. Jumlah tidak dapat diedit pada status ini.") {}
                                    Paper {
                                        Text("Jumlah per menu", fontWeight = FontWeight.Bold)
                                        s.menus.forEach { menu ->
                                            MenuInput(menu, s.buffers[menu.id], enabled = !s.working && s.lifecycle == null && s.closingState == "DRAFT",
                                                onChange = { vm.change(menu.id, it) }, onConflict = { navigate { conflict = menu } })
                                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .5f))
                                        }
                                    }
                                    Text(if (s.dirty) "Perubahan belum disimpan. Tekan Simpan draft sebelum meninggalkan halaman."
                                        else "Isi 0 jika tidak ada penjualan. Draft yang sudah disimpan tetap tersedia di perangkat ini.",
                                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                    if (!s.valid) Text("Jumlah harus bilangan bulat 0–9999; kolom tidak boleh kosong.", color = scheme.error, style = MaterialTheme.typography.bodySmall)
                                    if (s.closingState == "DRAFT") Button(onClick = vm::save,
                                        enabled = s.dirty && s.valid && !s.working && s.lifecycle == null,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("online.save"), shape = RoundedCornerShape(13.dp)) {
                                        Text(if (s.working) "Menyimpan…" else "Simpan draft")
                                    }
                                    s.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("online.notice")) }
                                } else {
                                    Text("Selamat bekerja, ${s.identity!!.displayName}", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                    Text("Closing rapi.\nTim satu angka.", fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                                    Hero(s)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Stat("Menu tercatat", s.menus.size.toString(), Modifier.weight(1f), Icons.Outlined.Layers)
                                        Stat("Perubahan antre", s.pending.toString(), Modifier.weight(1f), Icons.Outlined.Sync)
                                    }
                                    SyncStatus(s, vm::sync)
                                    Paper {
                                        Text("Satu outlet, satu closing", fontWeight = FontWeight.Bold)
                                        Text("Perubahan tim diperiksa sebelum digabung. Angka yang bertentangan menunggu pilihanmu.",
                                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                    }
                                    Button(onClick = { navigate { tab = "Input" } }, Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(13.dp)) {
                                        Text(if (s.closingState == "DRAFT") "Lanjutkan input" else "Lihat jumlah menu")
                                        Spacer(Modifier.width(10.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        pendingNavigation?.let { action -> AlertDialog(onDismissRequest = { pendingNavigation = null }, title = { Text("Perubahan belum disimpan") },
            text = { Text("Angka yang sedang diketik belum menjadi draft tersimpan. Kembali untuk menyimpan, atau buang perubahan kolom ini.") },
            confirmButton = { TextButton(onClick = { vm.discardForm(); pendingNavigation = null; action() }) { Text("Buang perubahan") } },
            dismissButton = { TextButton(onClick = { pendingNavigation = null }) { Text("Kembali mengedit") } }) }
        if (s.confirmLogout) AlertDialog(onDismissRequest = vm::cancelLogout, title = { Text("Masih ada pekerjaan belum terkonfirmasi") },
            text = { Text("Draft atau tindakan tetap disimpan untuk akun ini. Masuk kembali dengan akun yang sama untuk melanjutkan pemeriksaan server.") },
            confirmButton = { TextButton(onClick = { vm.logout(true) }) { Text("Keluar, simpan pekerjaan") } }, dismissButton = { TextButton(onClick = vm::cancelLogout) { Text("Batal") } })
        s.confirmSwitch?.let { name -> AlertDialog(onDismissRequest = { vm.confirmSwitch(false) }, title = { Text("Beralih ke $name?") },
            text = { Text("Akun sebelumnya masih memiliki draft belum terkirim. Draft tetap terpisah dan tidak akan dikirim atas nama akun baru.") },
            confirmButton = { TextButton(onClick = { vm.confirmSwitch(true) }) { Text("Beralih akun") } }, dismissButton = { TextButton(onClick = { vm.confirmSwitch(false) }) { Text("Batal") } }) }
        conflict?.let { menu -> ConflictDialog(menu, onDismiss = { conflict = null }, onResolve = { reapply -> conflict = null; vm.resolve(menu, reapply) }) }
        lifecycleReview?.let { (action, reviewed) -> LifecycleDialog(action, reviewed, onDismiss = { lifecycleReview = null },
            onConfirm = { reason -> lifecycleReview = null; vm.lifecycle(action, reason, reviewed) }) }
    }
}

@Composable private fun Paper(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = Rounded, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
    }
}

@Composable private fun MessageCard(text: String, error: Boolean = false, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text(text, fontSize = 12.sp, lineHeight = 19.sp, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@Composable private fun LoginContent(s: OnlineUiState, vm: OnlineViewModel) {
    var username by remember { mutableStateOf(s.identity?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Text("Closing rapi.\nTim satu angka.", fontSize = 36.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
    Text("Masuk untuk melanjutkan closing outlet bersama tim.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 23.sp)
    Paper {
        Text("Selamat datang kembali", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, enabled = !s.working,
            modifier = Modifier.fillMaxWidth().testTag("online.username"), shape = RoundedCornerShape(13.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, enabled = !s.working,
            modifier = Modifier.fillMaxWidth().testTag("online.password"), shape = RoundedCornerShape(13.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = {
                IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (visible) "Sembunyikan password" else "Tampilkan password") }
            })
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { vm.login(username, password); password = "" }, enabled = username.isNotBlank() && password.isNotBlank() && !s.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("online.login"), shape = RoundedCornerShape(13.dp)) {
            Text(if (s.working) "Memeriksa akun…" else "Masuk")
        }
    }
    Text("Gunakan akun pribadi yang diberikan pengelola. Perubahan tercatat atas nama akunmu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    s.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (s.identity != null) TextButton(onClick = vm::cancelLogin) { Text("Kembali ke draft perangkat") }
}

@Composable private fun OutletDate(s: OnlineUiState, onOutlet: (String) -> Unit, onDate: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }; val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { expanded = true }, enabled = !s.working) {
                Icon(Icons.Outlined.Storefront, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp))
                Text(s.identity?.outlets?.firstOrNull { it.id == s.outletId }?.name ?: "Pilih outlet", maxLines = 2, fontSize = 11.sp)
            }
            DropdownMenu(expanded, { expanded = false }) { s.identity?.outlets?.forEach { outlet ->
                DropdownMenuItem(text = { Text(outlet.name) }, onClick = { expanded = false; onOutlet(outlet.id) })
            } }
        }
        TextButton(onClick = {
            val d = LocalDate.parse(s.date)
            DatePickerDialog(context, { _, y, m, day -> onDate(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }, enabled = !s.working) { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(displayDate(s.date), fontSize = 11.sp) }
    }
}

@Composable private fun Hero(s: OnlineUiState) {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF087DB5), Color(0xFF176294))), Rounded)
        .padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Closing outlet", color = Color.White, fontSize = 12.sp)
            Text(stateLabel(s.closingState), color = Color.White, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) { Text(s.total.toString(), fontSize = 45.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(" menu terjual", Modifier.padding(bottom = 8.dp), color = Color.White, fontSize = 13.sp) }
        Text(displayDate(s.date), color = Color(0xFFE4F4FF), fontSize = 12.sp)
    }
}

@Composable private fun Stat(label: String, value: String, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Paper(modifier) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.bodySmall) }
}

@Composable private fun SyncStatus(s: OnlineUiState, onSync: () -> Unit) {
    val conflict = s.menus.any { it.status in setOf("CONFLICT", "FAILED", "AUTH_REQUIRED") }
    val text = when {
        s.dirty -> "Perubahan kolom belum disimpan"
        s.lifecycle?.status in setOf("CONFLICT", "FAILED") -> "Tindakan ditolak · periksa di Review"
        s.lifecycle?.status == "AUTH_REQUIRED" -> "Tindakan menunggu pemeriksaan sesi"
        s.lifecycle != null -> "Input menunggu konfirmasi tindakan"
        s.syncing -> "Memeriksa dan mengirim perubahan…"
        conflict -> "Ada perubahan yang perlu ditinjau"
        s.connectionUnavailable && s.pending == 0 -> "Menampilkan data tersimpan · koneksi belum tersedia"
        s.pending > 0 -> "${s.pending} perubahan tersimpan di perangkat · belum terkirim"
        s.menus.any { it.status != "SYNCED" } -> "Perubahan sedang diperiksa"
        !s.syncVerified -> "Data tersimpan · sinkronisasi belum terverifikasi"
        else -> "Perangkat ini tersinkron"
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (s.connectionUnavailable) Icons.Outlined.CloudOff else if (conflict) Icons.Outlined.ErrorOutline else Icons.Outlined.CloudDone,
                null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Text(text, Modifier.weight(1f).padding(horizontal = 9.dp).testTag("online.status"), fontSize = 11.sp, lineHeight = 17.sp)
            IconButton(onClick = onSync, enabled = !s.syncing && !s.working, modifier = Modifier.testTag("online.sync")) {
                Icon(Icons.Outlined.Sync, "Sinkronkan sekarang", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun actionLabel(action: String) = when (action) { "submit" -> "Kirim untuk review"; "return" -> "Kembalikan ke draft"; "lock" -> "Kunci closing"; "reopen" -> "Buka kembali closing"; else -> "Tindakan closing" }

@Composable private fun ReviewContent(s: OnlineUiState, vm: OnlineViewModel, onAction: (String) -> Unit) {
    Text("Periksa. Lalu pastikan.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    if (s.closingId == null) { Paper { Text("Buka closing dari Ringkasan untuk memeriksa jumlah dan statusnya.") }; return }
    Text("${displayDate(s.date)} · ${stateLabel(s.closingState)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    SyncStatus(s, vm::sync)
    s.lifecycle?.let { op -> Paper {
        Text(actionLabel(op.action), fontWeight = FontWeight.Bold)
        Text(if (op.status in setOf("CONFLICT", "FAILED")) "Tindakan ditolak dan belum diterapkan. Tutup tindakan ini, lalu periksa data terbaru sebelum mencoba lagi."
            else "Hasil belum terkonfirmasi. Jangan mengulang sebagai tindakan baru; aplikasi akan memeriksa ID yang sama.", style = MaterialTheme.typography.bodySmall)
        if (op.status in setOf("CONFLICT", "FAILED")) TextButton(onClick = vm::dismissLifecycle, enabled = !s.working) { Text("Tutup tindakan yang ditolak") }
        else TextButton(onClick = vm::sync, enabled = !s.syncing && !s.working) { Text("Periksa konfirmasi") }
    } }
    Paper {
        Text("Menu pada server terakhir", fontWeight = FontWeight.Bold)
        s.reviewMenus.forEach { line -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(line.name, Modifier.weight(1f)); Text(line.quantity.toString(), fontWeight = FontWeight.SemiBold)
        } }
        HorizontalDivider(); Text("Total ${s.reviewMenus.sumOf { it.quantity }} porsi", fontWeight = FontWeight.Bold)
    }
    Paper {
        Text("Pemakaian bahan", fontWeight = FontWeight.Bold)
        if (s.ingredients.isEmpty()) Text("Belum ada pemakaian pada snapshot server ini.", style = MaterialTheme.typography.bodySmall)
        s.ingredients.forEach { line -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(line.name, Modifier.weight(1f)); Text(line.quantity.toString(), fontWeight = FontWeight.SemiBold)
        } }
        Text("Sesuai resep yang tersimpan pada closing ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    s.submittedBy?.let { Text("Dikirim oleh $it", style = MaterialTheme.typography.bodySmall) }
    s.lockedBy?.let { Text("Dikunci oleh $it", style = MaterialTheme.typography.bodySmall) }
    val supervisor = s.identity?.outlets?.firstOrNull { it.id == s.outletId }?.role in setOf("SUPERVISOR", "ADMIN")
    val ready = s.syncVerified && !s.dirty && s.pending == 0 && s.lifecycle == null && !s.working && !s.syncing
    val actions = when (s.closingState) { "DRAFT" -> listOf("submit"); "SUBMITTED" -> if (supervisor) listOf("lock", "return") else emptyList()
        "LOCKED" -> if (supervisor) listOf("reopen") else emptyList(); else -> emptyList() }
    if (s.closingState == "SUBMITTED" && !supervisor) Text("Menunggu pemeriksaan supervisor. Jumlah tidak dapat diedit setelah diajukan.", style = MaterialTheme.typography.bodySmall)
    if (!ready && s.lifecycle == null) Text("Simpan perubahan dan selesaikan sinkronisasi/konflik di perangkat ini sebelum mengubah status.", style = MaterialTheme.typography.bodySmall)
    actions.forEach { action ->
        val modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp).testTag("online.action.$action")
        if (action in setOf("return", "reopen")) OutlinedButton(onClick = { onAction(action) }, enabled = ready,
            modifier = modifier, shape = RoundedCornerShape(13.dp)) { Text(actionLabel(action)) }
        else Button(onClick = { onAction(action) }, enabled = ready, modifier = modifier, shape = RoundedCornerShape(13.dp)) { Text(actionLabel(action)) }
    }
}

@Composable private fun LifecycleDialog(action: String, reviewed: OnlineUiState, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }; val needsReason = action in setOf("return", "reopen")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${actionLabel(action)}?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("${displayDate(reviewed.date)} · ${reviewed.reviewMenus.sumOf { it.quantity }} porsi", fontWeight = FontWeight.SemiBold)
            Text(when (action) {
                "submit" -> "Jumlah server yang direview akan diajukan. Perangkat lain mungkin masih memiliki draft offline."
                "lock" -> "Penguncian menyimpan snapshot laporan. Perubahan berikutnya memerlukan pembukaan ulang dengan alasan."
                "return" -> "Closing menjadi draft revisi baru. Alasan pengembalian dicatat dalam riwayat."
                else -> "Snapshot terkunci sebelumnya tetap disimpan. Alasan pembukaan ulang dicatat dalam riwayat."
            }, style = MaterialTheme.typography.bodyMedium)
            if (needsReason) OutlinedTextField(reason, { reason = it }, label = { Text("Alasan") }, modifier = Modifier.fillMaxWidth().testTag("online.lifecycle.reason"),
                supportingText = { Text("${reason.trim().length}/500 karakter · wajib diisi") }, isError = reason.trim().length > 500, minLines = 2, maxLines = 4)
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(reason.trim()) }, enabled = !needsReason || reason.trim().length in 1..500,
        modifier = Modifier.testTag("online.lifecycle.confirm")) { Text("Ya, lanjutkan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Periksa lagi") } })
}

@Composable private fun MenuInput(menu: OnlineMenuUi, buffer: String?, enabled: Boolean, onChange: (String) -> Unit, onConflict: () -> Unit) {
    val blocked = menu.status in setOf("CONFLICT", "FAILED", "AUTH_REQUIRED")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(menu.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(menu.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(buffer ?: menu.quantity.toString(), onChange, enabled = enabled && !blocked, singleLine = true,
            modifier = Modifier.width(98.dp).testTag("online.qty.${menu.id}"), label = { Text("Porsi", fontSize = 11.sp) }, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    if (blocked) {
        Text("Draft perangkat: ${menu.quantity} · angka server: ${menu.serverQuantity}", style = MaterialTheme.typography.bodySmall)
        if (menu.status != "AUTH_REQUIRED") TextButton(onClick = onConflict) { Text("Tinjau ${menu.name}") }
        else Text("Masuk kembali untuk melanjutkan draft.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun displayTime(at: String, zone: String = java.time.ZoneId.systemDefault().id): String = runCatching {
    java.time.Instant.parse(at).atZone(java.time.ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z", Locale.forLanguageTag("id-ID")))
}.getOrDefault(at)
private fun auditLabel(action: String) = when(action) {
    "CREATE" -> "Closing dibuka"; "ENTRY_UPDATED" -> "Jumlah diperbarui"; "SUBMIT" -> "Diajukan untuk review"
    "RETURN" -> "Dikembalikan ke draft"; "LOCK" -> "Closing dikunci"; "REOPEN" -> "Closing dibuka kembali"; else -> "Perubahan closing"
}

@Composable private fun HistoryContent(s: OnlineUiState, vm: OnlineViewModel, onSelect: (OnlineHistoryUi) -> Unit) {
    if (s.frozenReport != null) { FrozenReportContent(s.frozenReport, vm::closeFrozenReport); return }
    val detail = s.historyDetail
    if (detail != null) {
        TextButton(onClick = vm::closeHistoryDetail, modifier = Modifier.testTag("online.history.back")) {
            Icon(Icons.Outlined.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Semua riwayat")
        }
        Text(displayDate(detail.date), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("${stateLabel(detail.state)} · jejak perubahan server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        s.detailError?.let { MessageCard("Jejak dan laporan belum dapat dimuat. $it", error = true) {
            TextButton(onClick = { vm.inspectHistory(detail) }, enabled = !s.detailLoading) { Text("Muat ulang detail") }
            if (s.authRequired) TextButton(onClick = vm::showLogin) { Text("Masuk kembali") }
        } }
        Paper {
            Text("Laporan terkunci", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Snapshot saat dikunci tetap sama setelah closing dibuka ulang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.reports.isEmpty() && !s.detailLoading && s.detailError == null) Text("Belum ada laporan terkunci.", style = MaterialTheme.typography.bodySmall)
            s.reports.forEach { report ->
                HorizontalDivider()
                Text("Generasi ${report.generation} · revisi ${report.revision}", fontWeight = FontWeight.SemiBold)
                Text("${report.reviewer} · ${displayTime(report.at,s.historyTimezone)}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.openReport(report.generation) }, enabled = !s.detailLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("online.report.${report.generation}")) { Text("Lihat laporan generasi ${report.generation}") }
            }
            if (s.reportCursor != null) TextButton(onClick = { vm.loadMoreDetail(true) }, enabled = !s.detailLoading) { Text("Laporan sebelumnya") }
        }
        Paper {
            Text("Jejak perubahan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Urutan dari awal. Hanya tindakan yang diterima server yang ditampilkan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.audit.forEach { event ->
                HorizontalDivider()
                Text(auditLabel(event.action), fontWeight = FontWeight.SemiBold)
                Text("${event.actor} · ${displayTime(event.at,s.historyTimezone)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.menuId != null) Text("${s.historyMenuNames[event.menuId] ?: "Menu"}: ${event.before} → ${event.after}", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("online.audit.${event.id}"))
                event.reason?.let { Text("Alasan: $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Generasi ${event.generation} · revisi ${event.revision}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.auditCursor != null) TextButton(onClick = { vm.loadMoreDetail(false) }, enabled = !s.detailLoading,
                modifier = Modifier.testTag("online.audit.more")) { Text("Perubahan berikutnya") }
        }
        Button(onClick = { onSelect(detail) }, enabled = !s.working && !s.detailLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("online.history.open")) { Text("Buka closing ini") }
        return
    }
    Text("Jejak tetap tersimpan.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Riwayat tim dari server dan salinan yang tersedia di perangkat ini.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Dari server", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = { vm.loadHistory() }, enabled = !s.historyLoading, modifier = Modifier.testTag("online.history.refresh")) {
            Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Muat ulang")
        }
    }
    if (s.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    s.historyError?.let { MessageCard("Riwayat server belum dapat dimuat. $it", error = true) {
        if (s.authRequired) TextButton(onClick = vm::showLogin) { Text("Masuk kembali") }
    } }
    if (s.historyLoaded && s.serverHistory.isEmpty() && s.historyError == null) Paper { Text("Belum ada closing di server untuk outlet ini.") }
    s.serverHistory.forEach { row -> Paper {
        Text(displayDate(row.date), fontWeight = FontWeight.Bold)
        Text(stateLabel(row.state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { vm.inspectHistory(row) }, modifier = Modifier.testTag("online.history.detail.${row.id}")) { Text("Lihat jejak & laporan") }
    } }
    if (s.historyCursor != null) OutlinedButton(onClick = { vm.loadHistory(true) }, enabled = !s.historyLoading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("online.history.more")) { Text("Tanggal sebelumnya") }
    Text("Tersimpan di perangkat", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text("Salinan terakhir untuk akun dan outlet ini. Buka closing untuk memeriksa sinkronisasi terbaru.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (s.history.isEmpty()) Paper { Text("Belum ada salinan closing di perangkat ini.") }
    s.history.forEach { row -> Paper { Text(displayDate(row.date), fontWeight = FontWeight.Bold); Text(stateLabel(row.state), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onSelect(row) }) { Text("Buka closing") } } }
}

@Composable private fun FrozenReportContent(raw: String, onBack: () -> Unit) {
    val report = remember(raw) { org.json.JSONObject(raw) }; val closing = report.getJSONObject("closing")
    val quantities = closing.getJSONArray("entries").let { entries -> (0 until entries.length()).associate {
        val row = entries.getJSONObject(it); row.getString("menuId") to row.getLong("quantity") } }
    val menus = closing.getJSONObject("catalog").getJSONArray("menus"); val totals = closing.getJSONArray("ingredientTotals")
    TextButton(onClick = onBack, modifier = Modifier.testTag("online.report.back")) {
        Icon(Icons.Outlined.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Jejak & laporan")
    }
    Text("Laporan terkunci", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text(displayDate(closing.getString("businessDate")), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Paper {
        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
        Text("Generasi ${report.getLong("generation")} · revisi ${report.getLong("closingRevision")}", fontWeight = FontWeight.Bold)
        Text("Dikunci oleh ${report.getJSONObject("reviewedBy").getString("displayName")}", style = MaterialTheme.typography.bodyMedium)
        Text(displayTime(report.getString("reviewedAt"),closing.getString("timezone")), style = MaterialTheme.typography.bodySmall)
        Text("Snapshot tersimpan saat penguncian. Angka di sini tidak mengikuti perubahan closing berikutnya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Paper {
        Text("Menu pada laporan", fontWeight = FontWeight.Bold)
        for (i in 0 until menus.length()) { val menu = menus.getJSONObject(i)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(menu.getString("name"),Modifier.weight(1f)); Text(quantities[menu.getString("id")].toString(), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("online.report.quantity.${menu.getString("id")}"))
            }
        }
        HorizontalDivider(); Text("Total ${quantities.values.sum()} porsi", fontWeight = FontWeight.Bold)
    }
    Paper {
        Text("Pemakaian bahan pada laporan", fontWeight = FontWeight.Bold)
        for (i in 0 until totals.length()) { val line = totals.getJSONObject(i)
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                Text(line.getString("name"),Modifier.weight(1f)); Text(line.getLong("total").toString(),fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Menggunakan resep historis closing ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AccountContent(s: OnlineUiState, vm: OnlineViewModel, onNavigate: (() -> Unit) -> Unit) {
    Text("Akun & akses outlet", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Paper { Text(s.identity!!.displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(s.identity.username, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.identity.outlets.forEach { outlet -> Text(outlet.name, fontWeight = FontWeight.SemiBold); Text(when (outlet.role) { "ADMIN" -> "Administrator"; "SUPERVISOR" -> "Supervisor"; else -> "Karyawan" }, style = MaterialTheme.typography.bodySmall) }
    }
    Text("Draft tersimpan mengikuti akun dan outlet. Pergantian akun tidak memindahkan perubahan ke pengguna lain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { onNavigate { vm.showLogin() } }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Masuk kembali / ganti akun") }
    TextButton(onClick = { onNavigate { vm.logout() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Keluar akun") }
}

@Composable private fun ConflictDialog(menu: OnlineMenuUi, onDismiss: () -> Unit, onResolve: (Boolean) -> Unit) {
    var choice by remember { mutableStateOf<Boolean?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tentukan angka yang benar") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(menu.name, fontWeight = FontWeight.Bold)
            Text("Angka berbeda tidak digabung otomatis. Konfirmasikan total outlet dengan tim terlebih dahulu.")
            Text("Server: ${menu.serverQuantity} · ${menu.actor}")
            Text("Draft perangkatmu: ${menu.quantity}")
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(choice == false, role = Role.RadioButton, onClick = { choice = false }), verticalAlignment = Alignment.CenterVertically) { RadioButton(choice == false, null); Spacer(Modifier.width(12.dp)); Text("Gunakan angka server terbaru") }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(choice == true, role = Role.RadioButton, onClick = { choice = true }), verticalAlignment = Alignment.CenterVertically) { RadioButton(choice == true, null); Spacer(Modifier.width(12.dp)); Text("Kirim ulang draft saya: ${menu.quantity}") }
        }
    }, confirmButton = { TextButton(onClick = { choice?.let(onResolve) }, enabled = choice != null) { Text("Terapkan pilihan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Periksa nanti") } })
}
