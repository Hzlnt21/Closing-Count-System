package com.closingcount.app.ui.closing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ClosingTab(val label: String) {
    Input("Input Menu"),
    Result("Hasil Bahan"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosingScreen(
    contentPadding: PaddingValues,
    requestedDate: LocalDate? = null,
    onRequestedDateHandled: () -> Unit = {},
    viewModel: ClosingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDiscardForDatePicker by remember { mutableStateOf(false) }
    var pendingRequestedDate by remember { mutableStateOf<LocalDate?>(null) }
    val saveLabel = if (state.closingExists) "Simpan perubahan" else "Simpan closing"

    LaunchedEffect(requestedDate, state.hasUnsavedChanges) {
        requestedDate?.let { date ->
            when {
                date == state.date -> onRequestedDateHandled()
                state.hasUnsavedChanges -> pendingRequestedDate = date
                else -> {
                    viewModel.selectDate(date)
                    onRequestedDateHandled()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding).fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.semantics { contentDescription = saveLabel },
                onClick = {
                    scope.launch {
                        val error = viewModel.save()
                        snackbar.showSnackbar(error ?: "Closing berhasil disimpan.")
                    }
                },
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text(saveLabel) },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            ClosingHeader(
                date = state.date,
                exists = state.closingExists,
                saved = state.isSaved,
                onChooseDate = {
                    if (state.hasUnsavedChanges) {
                        confirmDiscardForDatePicker = true
                    } else {
                        showDatePicker = true
                    }
                },
            )
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                ClosingTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) },
                    )
                }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                selectedTab == ClosingTab.Input.ordinal -> ClosingMenuInput(
                    groups = state.menuGroups,
                    quantities = state.quantities,
                    onQuantityChange = viewModel::updateQuantity,
                )
                else -> ClosingResults(state.resultGroups)
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.selectDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (confirmDiscardForDatePicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscardForDatePicker = false },
            title = { Text("Perubahan belum disimpan") },
            text = {
                Text("Jumlah menu yang baru diubah akan hilang jika kamu memilih tanggal lain.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDiscardForDatePicker = false
                        showDatePicker = true
                    },
                ) { Text("Pilih tanggal lain") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardForDatePicker = false }) { Text("Kembali") }
            },
        )
    }

    pendingRequestedDate?.let { date ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                pendingRequestedDate = null
                onRequestedDateHandled()
            },
            title = { Text("Perubahan belum disimpan") },
            text = {
                Text("Simpan perubahan closing saat ini sebelum membuka closing dari riwayat, atau lanjutkan tanpa menyimpan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRequestedDate = null
                        viewModel.selectDate(date)
                        onRequestedDateHandled()
                    },
                ) { Text("Lanjut tanpa simpan") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRequestedDate = null
                        onRequestedDateHandled()
                    },
                ) { Text("Kembali") }
            },
        )
    }
}

@Composable
private fun ClosingHeader(
    date: LocalDate,
    exists: Boolean,
    saved: Boolean,
    onChooseDate: () -> Unit,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onChooseDate, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Text(
                date.format(formatter).replaceFirstChar { it.uppercase() },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = when {
                exists && saved -> "Tersimpan"
                exists -> "Diubah"
                else -> "Baru"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when {
                exists && saved -> MaterialTheme.colorScheme.primary
                exists -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ClosingMenuInput(
    groups: List<ClosingMenuGroup>,
    quantities: Map<Long, Int>,
    onQuantityChange: (Long, Int) -> Unit,
) {
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    if (groups.isEmpty()) {
        ClosingEmptyState("Belum ada menu aktif. Tambahkan kategori, menu, dan resep di Data Menu terlebih dahulu.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Masukkan jumlah setiap menu yang terjual. Menu yang tidak terjual tetap bernilai 0.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(groups, key = { it.id }) { group ->
            val isExpanded = expanded[group.id] ?: true
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded[group.id] = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${group.menus.size} menu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Tutup kategori" else "Buka kategori",
                    )
                }
                AnimatedVisibility(isExpanded) {
                    Column {
                        group.menus.forEachIndexed { index, menu ->
                            if (index > 0) HorizontalDivider()
                            ClosingMenuRow(
                                menu = menu,
                                quantity = quantities[menu.id] ?: 0,
                                onQuantityChange = { onQuantityChange(menu.id, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClosingMenuRow(
    menu: ClosingMenu,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(menu.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${menu.ingredients.size} bahan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onQuantityChange(quantity - 1) },
            enabled = quantity > 0,
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Kurangi ${menu.name}")
        }
        OutlinedTextField(
            value = quantity.toString(),
            onValueChange = { value ->
                if (value.isEmpty()) onQuantityChange(0)
                else value.toIntOrNull()?.let(onQuantityChange)
            },
            modifier = Modifier.width(88.dp),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        IconButton(onClick = { onQuantityChange(quantity + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "Tambah ${menu.name}")
        }
    }
}

@Composable
private fun ClosingResults(groups: List<ClosingResultGroup>) {
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    if (groups.isEmpty()) {
        ClosingEmptyState("Belum ada hasil. Masukkan jumlah menu terjual pada tab Input Menu.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Total Terjual / Out",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        items(groups, key = { it.id }) { group ->
            val isExpanded = expanded[group.id] ?: true
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded[group.id] = !isExpanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Tutup hasil" else "Buka hasil",
                    )
                }
                AnimatedVisibility(isExpanded) {
                    Column {
                        group.results.forEachIndexed { index, result ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(result.ingredient.name, modifier = Modifier.weight(1f))
                                Text(
                                    result.total.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClosingEmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
