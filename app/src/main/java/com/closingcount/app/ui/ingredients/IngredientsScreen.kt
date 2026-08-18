package com.closingcount.app.ui.ingredients

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closingcount.app.data.local.IngredientCategoryEntity
import com.closingcount.app.data.local.IngredientEntity
import kotlinx.coroutines.launch

private enum class IngredientTab(val label: String) {
    Ingredients("Bahan"),
    Categories("Kategori"),
}

@Composable
fun IngredientsScreen(
    contentPadding: PaddingValues,
    viewModel: IngredientsViewModel = viewModel(),
) {
    val categoryGroups by viewModel.categoryGroups.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var ingredientToEdit by remember { mutableStateOf<IngredientEntity?>(null) }
    var categoryToEdit by remember { mutableStateOf<IngredientCategoryEntity?>(null) }
    var showIngredientDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    val addButtonLabel = if (selectedTabIndex == IngredientTab.Ingredients.ordinal) {
        "Tambah bahan"
    } else {
        "Tambah kategori"
    }

    Scaffold(
        modifier = Modifier
            .padding(contentPadding)
            .fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.semantics {
                    contentDescription = addButtonLabel
                },
                onClick = {
                    if (selectedTabIndex == IngredientTab.Ingredients.ordinal) {
                        ingredientToEdit = null
                        showIngredientDialog = true
                    } else {
                        categoryToEdit = null
                        showCategoryDialog = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(addButtonLabel)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                IngredientTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedTabIndex,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (selectedTabIndex) {
                IngredientTab.Ingredients.ordinal -> IngredientList(
                    groups = categoryGroups,
                    onEdit = { ingredient ->
                        ingredientToEdit = ingredient
                        showIngredientDialog = true
                    },
                    onActiveChange = { ingredient, active ->
                        coroutineScope.launch {
                            viewModel.setIngredientActive(ingredient, active)?.let {
                                snackbarHostState.showSnackbar(it)
                            }
                        }
                    },
                )

                else -> CategoryList(
                    categories = categories,
                    groups = categoryGroups,
                    onEdit = { category ->
                        categoryToEdit = category
                        showCategoryDialog = true
                    },
                    onActiveChange = { category, active ->
                        coroutineScope.launch {
                            viewModel.setCategoryActive(category, active)?.let {
                                snackbarHostState.showSnackbar(it)
                            }
                        }
                    },
                )
            }
        }
    }

    if (showIngredientDialog) {
        IngredientEditorDialog(
            ingredient = ingredientToEdit,
            categories = categories,
            onDismiss = { showIngredientDialog = false },
            onSave = { name, categoryId, active ->
                coroutineScope.launch {
                    val error = viewModel.saveIngredient(
                        existing = ingredientToEdit,
                        name = name,
                        categoryId = categoryId,
                        isActive = active,
                    )
                    if (error == null) {
                        showIngredientDialog = false
                    } else {
                        snackbarHostState.showSnackbar(error)
                    }
                }
            },
        )
    }

    if (showCategoryDialog) {
        CategoryEditorDialog(
            category = categoryToEdit,
            onDismiss = { showCategoryDialog = false },
            onSave = { name, active ->
                coroutineScope.launch {
                    val error = viewModel.saveCategory(
                        existing = categoryToEdit,
                        name = name,
                        isActive = active,
                    )
                    if (error == null) {
                        showCategoryDialog = false
                    } else {
                        snackbarHostState.showSnackbar(error)
                    }
                }
            },
        )
    }
}

@Composable
private fun IngredientList(
    groups: List<IngredientCategoryGroup>,
    onEdit: (IngredientEntity) -> Unit,
    onActiveChange: (IngredientEntity, Boolean) -> Unit,
) {
    val expandedCategories = remember { mutableStateMapOf<Long, Boolean>() }

    if (groups.isEmpty()) {
        EmptyState("Belum ada bahan. Tambahkan kategori dan bahan terlebih dahulu.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.category.id }) { group ->
            val expanded = expandedCategories[group.category.id] ?: true
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (group.category.isActive) 1f else 0.7f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedCategories[group.category.id] = !expanded
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.category.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${group.ingredients.size} bahan" +
                                if (group.category.isActive) "" else " • Kategori nonaktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Tutup kategori" else "Buka kategori",
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column {
                        group.ingredients.forEachIndexed { index, ingredient ->
                            if (index > 0) HorizontalDivider()
                            IngredientRowItem(
                                ingredient = ingredient,
                                onEdit = { onEdit(ingredient) },
                                onActiveChange = { onActiveChange(ingredient, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientRowItem(
    ingredient: IngredientEntity,
    onEdit: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (ingredient.isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!ingredient.isActive) {
                Text(
                    text = "Nonaktif",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit ${ingredient.name}",
            )
        }
        Switch(
            modifier = Modifier.semantics {
                contentDescription = "Status bahan ${ingredient.name}"
            },
            checked = ingredient.isActive,
            onCheckedChange = onActiveChange,
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<IngredientCategoryEntity>,
    groups: List<IngredientCategoryGroup>,
    onEdit: (IngredientCategoryEntity) -> Unit,
    onActiveChange: (IngredientCategoryEntity, Boolean) -> Unit,
) {
    val ingredientCounts = remember(groups) {
        groups.associate { it.category.id to it.ingredients.size }
    }

    if (categories.isEmpty()) {
        EmptyState("Belum ada kategori bahan.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (category.isActive) 1f else 0.7f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEdit(category) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${ingredientCounts[category.id] ?: 0} bahan" +
                                if (category.isActive) "" else " • Nonaktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(category) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit ${category.name}",
                        )
                    }
                    Switch(
                        modifier = Modifier.semantics {
                            contentDescription = "Status kategori ${category.name}"
                        },
                        checked = category.isActive,
                        onCheckedChange = { onActiveChange(category, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientEditorDialog(
    ingredient: IngredientEntity?,
    categories: List<IngredientCategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, categoryId: Long?, isActive: Boolean) -> Unit,
) {
    var name by remember(ingredient?.id) { mutableStateOf(ingredient?.name.orEmpty()) }
    var selectedCategoryId by remember(ingredient?.id, categories) {
        mutableStateOf(ingredient?.categoryId ?: categories.firstOrNull { it.isActive }?.id)
    }
    var isActive by remember(ingredient?.id) { mutableStateOf(ingredient?.isActive ?: true) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (ingredient == null) "Tambah bahan" else "Edit bahan")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama bahan") },
                    singleLine = true,
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { categoryMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = selectedCategory?.name ?: "Pilih kategori",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        category.name + if (category.isActive) "" else " (Nonaktif)",
                                    )
                                },
                                onClick = {
                                    selectedCategoryId = category.id
                                    categoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bahan aktif")
                        Text(
                            "Bahan nonaktif tidak dipakai pada menu baru.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        modifier = Modifier.semantics {
                            contentDescription = "Status bahan"
                        },
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedCategoryId, isActive) },
                enabled = name.isNotBlank() && selectedCategoryId != null,
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
    )
}

@Composable
private fun CategoryEditorDialog(
    category: IngredientCategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, isActive: Boolean) -> Unit,
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var isActive by remember(category?.id) { mutableStateOf(category?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (category == null) "Tambah kategori" else "Edit kategori")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama kategori") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kategori aktif")
                        Text(
                            "Kategori nonaktif tidak dipakai untuk data baru.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        modifier = Modifier.semantics {
                            contentDescription = "Status kategori"
                        },
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, isActive) },
                enabled = name.isNotBlank(),
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
    )
}
