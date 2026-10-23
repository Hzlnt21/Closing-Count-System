package com.closingcount.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closingcount.app.BuildConfig
import com.closingcount.app.ui.ingredients.IngredientsScreen
import com.closingcount.app.ui.menus.MenusScreen
import com.closingcount.app.ui.closing.ClosingScreen
import com.closingcount.app.ui.history.HistoryScreen
import com.closingcount.app.ui.transfer.DataTransferViewModel
import java.time.LocalDate
import com.closingcount.app.ui.online.OnlineClosingApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosingCountApp() {
    var showOnline by rememberSaveable { mutableStateOf(false) }
    if (showOnline) {
        OnlineClosingApp(onExit = { showOnline = false })
        return
    }
    var currentDestinationName by rememberSaveable {
        mutableStateOf(AppDestination.Home.name)
    }
    var requestedClosingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val currentDestination = AppDestination.valueOf(currentDestinationName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDestination.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Tentang aplikasi",
                        )
                    }
                },
            )
        },
        bottomBar = {
            LocalNavigation(currentDestination) { currentDestinationName = it.name }
        },
    ) { innerPadding ->
        when (currentDestination) {
            AppDestination.Home -> HomeScreen(innerPadding, onOpenOnline = { showOnline = true })
            AppDestination.Closing -> ClosingScreen(
                contentPadding = innerPadding,
                requestedDate = requestedClosingDate?.let(LocalDate::parse),
                onRequestedDateHandled = { requestedClosingDate = null },
            )
            AppDestination.History -> HistoryScreen(
                contentPadding = innerPadding,
                onEditClosing = { date ->
                    requestedClosingDate = date.toString()
                    currentDestinationName = AppDestination.Closing.name
                },
            )
            AppDestination.Menus -> MenusScreen(innerPadding)
            AppDestination.Ingredients -> IngredientsScreen(innerPadding)
        }
    }

    if (showAbout) {
        AboutAppDialog(onDismissRequest = { showAbout = false })
    }
}

@Composable
private fun LocalNavigation(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    val density = LocalDensity.current
    val height = LocalWindowInfo.current.containerSize.height
    val compact = density.fontScale >= 1.3f || with(density) { height.toDp() } < 480.dp
    if (compact) {
        var expanded by remember { mutableStateOf(false) }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 7.dp)) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("local.nav.menu")) {
                    Text(selected.label, Modifier.weight(1f));Icon(Icons.Default.ExpandMore, "Pilih halaman lokal")
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    AppDestination.entries.forEach { page ->
                        DropdownMenuItem(text = { Text(page.label) }, leadingIcon = { Icon(page.icon, null) },
                            onClick = { expanded = false;onSelect(page) }, modifier = Modifier.testTag("local.nav.${page.name}"))
                    }
                }
            }
        }
    } else NavigationBar {
        AppDestination.entries.forEach { page ->
            NavigationBarItem(selected = selected == page, onClick = { onSelect(page) }, icon = { Icon(page.icon, page.label) },
                label = { Text(page.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.testTag("local.nav.${page.name}"))
        }
    }
}

@Composable
private fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenOnline: () -> Unit,
    viewModel: DataTransferViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    LaunchedEffect(state.message) { if (state.message != null) scroll.animateScrollTo(0) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::writeBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::inspect)
    }
    Scaffold(
        modifier = Modifier.padding(contentPadding).fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onOpenOnline, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Closing bersama") }
            state.message?.let { message ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
                    if (state.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, Modifier.testTag("local.transfer.message"))
                        TextButton(onClick = viewModel::clearMessage) { Text("Tutup pesan") }
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Coffee, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Data di perangkat ini", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Text("Catat closing lokal dan hitung penggunaan bahan Terjual/Out dari menu yang terjual.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("Cadangan data lokal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Data lokal dan closing bersama memiliki penyimpanan terpisah. Gunakan Closing bersama untuk data tim yang tersinkron.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Backup & restore lokal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("File backup v1 berisi kategori, bahan, menu, resep dan riwayat lokal. File ini tidak mencadangkan data server, akun atau antrean closing bersama.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { backupLauncher.launch("closing-count-lokal-${LocalDate.now()}.json") }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("local.backup.create")) {
                        Icon(Icons.Default.SaveAlt, null); Text("Buat backup lokal", Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("local.restore.select")) {
                        Icon(Icons.Default.Restore, null); Text("Periksa file backup", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (state.busy) AlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(state.activity) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Tunggu sampai proses selesai.")
        } }, confirmButton = {})
    state.review?.takeIf { !state.busy }?.let { review ->
        AlertDialog(onDismissRequest = viewModel::discard,
            title = { Text("Periksa backup lokal") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()).testTag("local.restore.review"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(review.filename, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${review.menus} menu / ${review.ingredients} bahan / ${review.closings} riwayat closing")
                Text(if (review.firstDate == null) "Belum ada riwayat closing." else "Tanggal ${review.firstDate} sampai ${review.lastDate}")
                Text("Kategori, bahan, menu, resep dan riwayat lokal saat ini akan diganti. Simpan backup lokal terlebih dahulu bila diperlukan.")
                Text("Data server dan antrean closing bersama tetap terpisah.", fontWeight = FontWeight.SemiBold)
                Text("Restore memakai salinan file yang telah diperiksa ini.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { Button(onClick = { viewModel.restore(review.id) }, modifier = Modifier.testTag("local.restore.confirm"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Ya, ganti data lokal") } },
            dismissButton = { TextButton(onClick = viewModel::discard, modifier = Modifier.testTag("local.restore.cancel")) { Text("Batal") } })
    }
}
