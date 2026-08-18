package com.closingcount.app.data.local

data class ClosingHistoryRow(
    val id: Long,
    val date: String,
    val updatedAt: Long,
    val totalMenusSold: Int,
    val soldMenuTypes: Int,
    val ingredientCount: Int,
)
