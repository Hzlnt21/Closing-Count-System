package com.closingcount.app.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.local.ClosingHistoryRow
import com.closingcount.app.data.local.ClosingIngredientResultEntity
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import com.closingcount.app.data.transfer.ClosingExcelExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryDetailState(
    val summary: ClosingHistoryRow,
    val entries: List<ClosingMenuEntryEntity> = emptyList(),
    val results: List<ClosingIngredientResultEntity> = emptyList(),
    val isLoading: Boolean = true,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClosingCountApplication
    private val dao = app.database.closingDao()

    val history = dao.observeHistoryRows().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val mutableDetail = MutableStateFlow<HistoryDetailState?>(null)
    val detail: StateFlow<HistoryDetailState?> = mutableDetail.asStateFlow()

    fun open(summary: ClosingHistoryRow) {
        mutableDetail.value = HistoryDetailState(summary = summary)
        viewModelScope.launch {
            val entries = dao.getMenuEntries(summary.id)
            val results = dao.getIngredientResults(summary.id)
            if (mutableDetail.value?.summary?.id == summary.id) {
                mutableDetail.value = HistoryDetailState(
                    summary = summary,
                    entries = entries,
                    results = results,
                    isLoading = false,
                )
            }
        }
    }

    fun closeDetail() {
        mutableDetail.value = null
    }

    suspend fun exportCurrentDetail(uri: Uri): String? {
        val state = mutableDetail.value
            ?: return "Detail closing belum siap untuk diekspor."
        if (state.isLoading) return "Tunggu sampai detail closing selesai dimuat."
        return runCatching {
            val bytes = ClosingExcelExporter.create(
                date = state.summary.date,
                entries = state.entries,
                results = state.results,
            )
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("File tujuan tidak dapat dibuka.")
        }.exceptionOrNull()?.let { "Export Excel gagal. Coba pilih lokasi lain." }
    }
}
