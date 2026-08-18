package com.closingcount.app.ui.menus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closingcount.app.data.local.MenuCategoryEntity
import com.closingcount.app.data.local.MenuEntity
import com.closingcount.app.ui.ingredients.IngredientCategoryGroup
import kotlinx.coroutines.launch

private enum class MenuTab(val label: String) {
    Menus("Menu"),
    Categories("Kategori"),
}

@Composable
fun MenusScreen(
    contentPadding: PaddingValues,
    viewModel: MenusViewModel = viewModel(),
) {
    val groups by viewModel.menuGroups.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val ingredientGroups by viewModel.ingredientGroups.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var menuToEdit by remember { mutableStateOf<MenuEntity?>(null) }
    var selectedIngredientIds by remember { mutableStateOf(emptySet<Long>()) }
    var categoryToEdit by remember { mutableStateOf<MenuCategoryEntity?>(null) }
    var showMenuEditor by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    val addLabel = if (selectedTab == MenuTab.Menus.ordinal) "Tambah menu" else "Tambah kategori"

    Scaffold(
        modifier = Modifier.padding(contentPadding).fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.semantics { contentDescription = addLabel },
                onClick = {
                    if (selectedTab == MenuTab.Menus.ordinal) {
                        if (categories.none { it.isActive }) {
                            scope.launch { snackbar.showSnackbar("Tambahkan kategori menu aktif terlebih dahulu.") }
                        } else {
                            menuToEdit = null
                            selectedIngredientIds = emptySet()
                            showMenuEditor = true
                        }
                    } else {
                        categoryToEdit = null
                        showCategoryEditor = true
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(addLabel) },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                MenuTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) },
                    )
                }
            }

            if (selectedTab == MenuTab.Menus.ordinal) {
                MenuList(
                    groups = groups,
                    onEdit = { menu ->
                        scope.launch {
                            selectedIngredientIds = viewModel.getIngredientIds(menu.id)
                            menuToEdit = menu
                            showMenuEditor = true
                        }
                    },
                    onActiveChange = { menu, active ->
                        scope.launch {
                            viewModel.setMenuActive(menu, active)?.let { snackbar.showSnackbar(it) }
                        }
                    },
                )
            } else {
                MenuCategoryList(
                    categories = categories,
                    groups = groups,
                    onEdit = {
                        categoryToEdit = it
                        showCategoryEditor = true
                    },
                    onActiveChange = { category, active ->
                        scope.launch {
                            viewModel.setCategoryActive(category, active)?.let { snackbar.showSnackbar(it) }
                        }
                    },
                )
            }
        }
    }

    if (showMenuEditor) {
        MenuEditorDialog(
            menu = menuToEdit,
            categories = categories,
            ingredientGroups = ingredientGroups,
            initialIngredientIds = selectedIngredientIds,
            onDismiss = { showMenuEditor = false },
            onSave = { name, categoryId, ingredientIds, active ->
                scope.launch {
                    val error = viewModel.saveMenu(
                        existing = menuToEdit,
                        name = name,
                        categoryId = categoryId,
                        ingredientIds = ingredientIds,
                        isActive = active,
                    )
                    if (error == null) showMenuEditor = false else snackbar.showSnackbar(error)
                }
            },
        )
    }

    if (showCategoryEditor) {
        MenuCategoryEditorDialog(
            category = categoryToEdit,
            onDismiss = { showCategoryEditor = false },
            onSave = { name, active ->
                scope.launch {
                    val error = viewModel.saveCategory(categoryToEdit, name, active)
                    if (error == null) showCategoryEditor = false else snackbar.showSnackbar(error)
                }
            },
        )
    }
}

@Composable
private fun MenuList(
    groups: List<MenuCategoryGroup>,
    onEdit: (MenuEntity) -> Unit,
    onActiveChange: (MenuEntity, Boolean) -> Unit,
) {
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    if (groups.isEmpty()) {
        EmptyMenuState("Belum ada kategori menu. Buka tab Kategori untuk menambahkannya.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.category.id }) { group ->
            val isExpanded = expanded[group.category.id] ?: true
            Card(
                modifier = Modifier.fillMaxWidth().alpha(if (group.category.isActive) 1f else 0.7f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        expanded[group.category.id] = !isExpanded
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(group.category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${group.menus.size} menu" + if (group.category.isActive) "" else " • Kategori nonaktif",
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
                    if (group.menus.isEmpty()) {
                        Text(
                            "Belum ada menu dalam kategori ini.",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column {
                            group.menus.forEachIndexed { index, summary ->
                                if (index > 0) HorizontalDivider()
                                MenuRowItem(summary, { onEdit(summary.menu) }) {
                                    onActiveChange(summary.menu, it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRowItem(
    summary: MenuSummary,
    onEdit: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    val menu = summary.menu
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 8.dp)) {
            Text(menu.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${summary.ingredientCount} bahan" + if (menu.isActive) "" else " • Nonaktif",
                style = MaterialTheme.typography.bodySmall,
                color = if (menu.isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit ${menu.name}")
        }
        Switch(
            modifier = Modifier.semantics { contentDescription = "Status menu ${menu.name}" },
            checked = menu.isActive,
            onCheckedChange = onActiveChange,
        )
    }
}

@Composable
private fun MenuCategoryList(
    categories: List<MenuCategoryEntity>,
    groups: List<MenuCategoryGroup>,
    onEdit: (MenuCategoryEntity) -> Unit,
    onActiveChange: (MenuCategoryEntity, Boolean) -> Unit,
) {
    val counts = remember(groups) { groups.associate { it.category.id to it.menus.size } }
    if (categories.isEmpty()) {
        EmptyMenuState("Belum ada kategori menu.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            Card(Modifier.fillMaxWidth().alpha(if (category.isActive) 1f else 0.7f), shape = RoundedCornerShape(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).clickable { onEdit(category) }.padding(vertical = 6.dp)) {
                        Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${counts[category.id] ?: 0} menu" + if (category.isActive) "" else " • Nonaktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(category) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${category.name}")
                    }
                    Switch(
                        modifier = Modifier.semantics { contentDescription = "Status kategori ${category.name}" },
                        checked = category.isActive,
                        onCheckedChange = { onActiveChange(category, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMenuState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuEditorDialog(
    menu: MenuEntity?,
    categories: List<MenuCategoryEntity>,
    ingredientGroups: List<IngredientCategoryGroup>,
    initialIngredientIds: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (String, Long?, Set<Long>, Boolean) -> Unit,
) {
    var name by remember(menu?.id) { mutableStateOf(menu?.name.orEmpty()) }
    var categoryId by remember(menu?.id, categories) {
        mutableStateOf(menu?.categoryId ?: categories.firstOrNull { it.isActive }?.id)
    }
    var active by remember(menu?.id) { mutableStateOf(menu?.isActive ?: true) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val selectedIds = remember(menu?.id, initialIngredientIds) {
        mutableStateMapOf<Long, Boolean>().apply {
            initialIngredientIds.forEach { put(it, true) }
        }
    }
    val selected = selectedIds.filterValues { it }.keys
    val visibleGroups = ingredientGroups.mapNotNull { group ->
        val visibleIngredients = group.ingredients.filter { it.isActive || it.id in selected }
        if ((group.category.isActive || visibleIngredients.any { it.id in selected }) && visibleIngredients.isNotEmpty()) {
            group.copy(ingredients = visibleIngredients)
        } else null
    }
    val availableCategories = categories.filter { it.isActive || it.id == categoryId }

    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = { Text(if (menu == null) "Tambah menu" else "Edit menu") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = { onSave(name, categoryId, selected.toSet(), active) },
                                enabled = name.isNotBlank() && categoryId != null && selected.isNotEmpty(),
                            ) { Text("Simpan") }
                        },
                    )
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nama menu") },
                            singleLine = true,
                        )
                    }
                    item {
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { categoryMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    categories.firstOrNull { it.id == categoryId }?.name ?: "Pilih kategori menu",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.ExpandMore, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false },
                            ) {
                                availableCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name + if (category.isActive) "" else " (Nonaktif)") },
                                        onClick = {
                                            categoryId = category.id
                                            categoryMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Menu aktif")
                                Text(
                                    "Menu nonaktif tidak muncul pada closing baru.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                modifier = Modifier.semantics { contentDescription = "Status menu" },
                                checked = active,
                                onCheckedChange = { active = it },
                            )
                        }
                    }
                    item {
                        Column {
                            Text("Bahan yang digunakan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${selected.size} bahan dipilih • setiap bahan dihitung 1 per menu terjual",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (visibleGroups.isEmpty()) {
                        item { Text("Belum ada bahan aktif. Tambahkan bahan terlebih dahulu.") }
                    } else {
                        items(visibleGroups, key = { it.category.id }) { group ->
                            IngredientSelectionCard(group, selectedIds)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientSelectionCard(
    group: IngredientCategoryGroup,
    selectedIds: MutableMap<Long, Boolean>,
) {
    var expanded by rememberSaveable(group.category.id) { mutableStateOf(true) }
    val selectedCount = group.ingredients.count { selectedIds[it.id] == true }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(group.category.name, fontWeight = FontWeight.Bold)
                Text(
                    "$selectedCount dari ${group.ingredients.size} dipilih",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Tutup kategori" else "Buka kategori",
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                group.ingredients.forEach { ingredient ->
                    val checked = selectedIds[ingredient.id] == true
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedIds[ingredient.id] = !checked
                        }.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selectedIds[ingredient.id] = it },
                        )
                        Text(
                            ingredient.name + if (ingredient.isActive) "" else " (Nonaktif)",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCategoryEditorDialog(
    category: MenuCategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var active by remember(category?.id) { mutableStateOf(category?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Tambah kategori menu" else "Edit kategori menu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama kategori menu") },
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Kategori aktif")
                        Text(
                            "Kategori nonaktif tidak dipakai untuk data baru.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        modifier = Modifier.semantics { contentDescription = "Status kategori menu" },
                        checked = active,
                        onCheckedChange = { active = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, active) }, enabled = name.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
