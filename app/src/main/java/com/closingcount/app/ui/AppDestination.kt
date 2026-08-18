package com.closingcount.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val label: String,
    val title: String,
    val icon: ImageVector,
    val description: String,
) {
    Home(
        label = "Beranda",
        title = "Closing Count",
        icon = Icons.Default.Home,
        description = "Ringkasan dan akses cepat closing harian.",
    ),
    Closing(
        label = "Closing",
        title = "Closing Harian",
        icon = Icons.Default.Checklist,
        description = "Form jumlah menu terjual akan tersedia pada v0.0.4.",
    ),
    History(
        label = "Riwayat",
        title = "Riwayat Closing",
        icon = Icons.Default.History,
        description = "Riwayat closing akan tersedia pada v0.0.5.",
    ),
    Menus(
        label = "Menu",
        title = "Data Menu",
        icon = Icons.Default.RestaurantMenu,
        description = "Pengelolaan menu dan resep akan tersedia pada v0.0.3.",
    ),
    Ingredients(
        label = "Bahan",
        title = "Data Bahan",
        icon = Icons.Default.Category,
        description = "Kelola kategori dan bahan yang digunakan oleh menu.",
    ),
}
