package com.builtdifferent.erp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.CompanyEntity
import com.builtdifferent.erp.data.repository.CompanyRepository
import com.builtdifferent.erp.data.repository.FinancialYearRepository
import com.builtdifferent.erp.data.repository.PartyRepository
import com.builtdifferent.erp.data.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val company: CompanyEntity? = null,
    val hasCompany: Boolean = false,
    val partyCount: Int = 0,
    val productCount: Int = 0,
    val totalStockValuePaise: Long = 0,
    val lowStockCount: Int = 0,
    val invoiceCount: Int = 0
)

/**
 * Every number on this screen is a live read from Room. Sales/purchase
 * totals beyond invoice count, and receivables/payables, are intentionally
 * still absent here (rather than shown as fake zeros) because the payments
 * and reporting modules that would make them meaningful haven't landed yet
 * in this pass; they get added to this same state class as those modules
 * are built, never faked in the meantime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val companyRepository: CompanyRepository,
    private val financialYearRepository: FinancialYearRepository,
    partyRepository: PartyRepository,
    productRepository: ProductRepository,
    database: AppDatabase
) : ViewModel() {

    private val currentFinancialYearId = MutableStateFlow<Long?>(null)

    private val baseState = combine(
        companyRepository.observeActiveCompany(),
        partyRepository.observeActivePartyCount(),
        productRepository.observeActiveProductCount(),
        database.stockDao().observeTotalStockValuePaise(),
        database.stockDao().observeLowStockCount()
    ) { company, partyCount, productCount, stockValue, lowStock ->
        DashboardUiState(
            company = company,
            hasCompany = company != null,
            partyCount = partyCount,
            productCount = productCount,
            totalStockValuePaise = stockValue,
            lowStockCount = lowStock
        )
    }

    private val invoiceCountFlow = currentFinancialYearId.flatMapLatest { fyId ->
        if (fyId == null) flowOf(0) else database.salesInvoiceDao().observePostedCount(fyId)
    }

    val uiState: StateFlow<DashboardUiState> = combine(baseState, invoiceCountFlow) { base, invoiceCount ->
        base.copy(invoiceCount = invoiceCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        viewModelScope.launch {
            companyRepository.observeActiveCompany().collect { company ->
                currentFinancialYearId.value = company?.let {
                    financialYearRepository.getCurrentFinancialYear(it.companyId)?.financialYearId
                }
            }
        }
    }
}
