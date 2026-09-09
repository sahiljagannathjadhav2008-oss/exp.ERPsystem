package com.builtdifferent.erp.ui.screens.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.entity.SalesInvoiceEntity
import com.builtdifferent.erp.data.local.entity.SalesInvoiceItemEntity
import com.builtdifferent.erp.data.repository.SalesInvoiceRepository
import com.builtdifferent.erp.domain.engine.InvoicePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SalesInvoiceDetailUiState(
    val invoice: SalesInvoiceEntity? = null,
    val items: List<SalesInvoiceItemEntity> = emptyList(),
    val isGeneratingPdf: Boolean = false,
    val pdfFile: File? = null,
    val errorMessage: String? = null
)

class SalesInvoiceDetailViewModel(
    private val salesInvoiceRepository: SalesInvoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesInvoiceDetailUiState())
    val uiState: StateFlow<SalesInvoiceDetailUiState> = _uiState.asStateFlow()

    fun load(invoiceId: Long) {
        viewModelScope.launch {
            val invoice = salesInvoiceRepository.getInvoice(invoiceId)
            val items = salesInvoiceRepository.getItems(invoiceId)
            _uiState.value = _uiState.value.copy(invoice = invoice, items = items)
        }
    }

    fun generatePdf(cacheDir: File) {
        val invoice = _uiState.value.invoice ?: return
        val items = _uiState.value.items
        _uiState.value = _uiState.value.copy(isGeneratingPdf = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.Default) {
                    InvoicePdfGenerator.generate(invoice, items, File(cacheDir, "invoices"))
                }
                _uiState.value = _uiState.value.copy(isGeneratingPdf = false, pdfFile = file)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isGeneratingPdf = false, errorMessage = e.message ?: "Failed to generate PDF")
            }
        }
    }

    fun onPdfShared() {
        _uiState.value = _uiState.value.copy(pdfFile = null)
    }
}
