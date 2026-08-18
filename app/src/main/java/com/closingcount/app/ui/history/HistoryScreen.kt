package com.closingcount.app.ui.history

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closingcount.app.data.local.ClosingHistoryRow
import com.closingcount.app.data.local.ClosingIngredientResultEntity
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val historyDateFormatter = DateTimeFormatter.ofPattern(
    "EEEE, d MMMM yyyy",
    Locale.forLanguageTag("id-ID"),
)

@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    onEditClosing: (LocalDate) -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    if (detail == null) {
        HistoryList(contentPadding, history, viewModel::open)
    } else {
        BackHandler(onBack = viewModel::closeDetail)
        HistoryDetail(
            contentPadding = contentPadding,
            state = checkNotNull(detail),
            onBack = viewModel::closeDetail,
            onEdit = {
                val date = LocalDate.parse(checkNotNull(detail).summary.date)
                viewModel.closeDetail()
                onEditClosing(date)
            },
        )
    }
}

@Composable
private fun HistoryList(
    contentPadding: PaddingValues,
    history: List<ClosingHistoryRow>,
    onOpen: (ClosingHistoryRow) -> Unit,
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Belum ada riwayat closing. Closing yang disimpan akan muncul di sini.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(history, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(item) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            LocalDate.parse(item.date).format(historyDateFormatter)
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${item.totalMenusSold} menu terjual • ${item.ingredientCount} bahan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Diperbarui ${formatUpdatedAt(item.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.RestaurantMenu, contentDescription = "Buka detail closing")
                }
            }
        }
    }
}

@Composable
private fun HistoryDetail(
    contentPadding: PaddingValues,
    state: HistoryDetailState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.semantics { contentDescription = "Edit closing" },
                onClick = onEdit,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("Edit closing") },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke riwayat")
                    }
                    Column {
                        Text("Detail Closing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            LocalDate.parse(state.summary.date).format(historyDateFormatter)
                                .replaceFirstChar { it.uppercase() },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                HistorySummaryCard(state.summary)
            }
            item {
                Text("Menu Terjual", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val soldEntries = state.entries.filter { it.quantity > 0 }
            if (soldEntries.isEmpty()) {
                item { Text("Tidak ada menu terjual pada closing ini.") }
            } else {
                val menuGroups = soldEntries
                    .groupBy { it.menuCategoryId to it.menuCategoryName }
                    .values
                    .toList()
                items(menuGroups, key = { "${it.first().menuCategoryId}:${it.first().menuCategoryName}" }) { entries ->
                    MenuHistoryCard(entries)
                }
            }
            item {
                Text("Hasil Terjual / Out", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (state.results.isEmpty()) {
                item { Text("Tidak ada hasil bahan pada closing ini.") }
            } else {
                val resultGroups = state.results.groupBy { it.ingredientCategoryId }.values.toList()
                items(resultGroups, key = { it.first().ingredientCategoryId }) { results ->
                    IngredientHistoryCard(results)
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(summary: ClosingHistoryRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryValue(summary.totalMenusSold.toString(), "Terjual")
            SummaryValue(summary.soldMenuTypes.toString(), "Jenis menu")
            SummaryValue(summary.ingredientCount.toString(), "Bahan")
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MenuHistoryCard(entries: List<ClosingMenuEntryEntity>) {
    val first = entries.first()
    var expanded by remember(first.menuCategoryId) { androidx.compose.runtime.mutableStateOf(true) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(first.menuCategoryName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Tutup kategori" else "Buka kategori",
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                entries.sortedWith(compareBy(ClosingMenuEntryEntity::menuSortOrder, ClosingMenuEntryEntity::menuName))
                    .forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(entry.menuName, Modifier.weight(1f))
                            Text(entry.quantity.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
            }
        }
    }
}

@Composable
private fun IngredientHistoryCard(results: List<ClosingIngredientResultEntity>) {
    val first = results.first()
    var expanded by remember(first.ingredientCategoryId) { androidx.compose.runtime.mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(first.ingredientCategoryName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Tutup kategori" else "Buka kategori",
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                results.forEachIndexed { index, result ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(result.ingredientName, Modifier.weight(1f))
                        Text(result.total.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatUpdatedAt(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")))
