package com.builtdifferent.erp.ui.screens.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.entity.SalesInvoiceEntity
import com.builtdifferent.erp.data.repository.CompanyRepository
import com.builtdifferent.erp.data.repository.FinancialYearRepository
import com.builtdifferent.erp.data.repository.SalesInvoiceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SalesInvoiceListUiState(val invoices: List<SalesInvoiceEntity> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class SalesInvoiceListViewModel(
    private val salesInvoiceRepository: SalesInvoiceRepository,
    private val companyRepository: CompanyRepository,
    private val financialYearRepository: FinancialYearRepository
) : ViewModel() {

    private val financialYearId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<SalesInvoiceListUiState> = financialYearId
        .flatMapLatest { fyId -> salesInvoiceRepository.observeInvoices(fyId, null) }
        .map { invoices -> SalesInvoiceListUiState(invoices = invoices) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SalesInvoiceListUiState())

    init {
        viewModelScope.launch {
            val company = companyRepository.getActiveCompany()
            if (company != null) {
                financialYearId.value = financialYearRepository.getCurrentFinancialYear(company.companyId)?.financialYearId
            }
        }
    }
}
