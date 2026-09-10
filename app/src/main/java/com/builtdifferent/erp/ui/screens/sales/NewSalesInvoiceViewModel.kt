package com.builtdifferent.erp.ui.screens.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.ProductEntity
import com.builtdifferent.erp.data.repository.CompanyRepository
import com.builtdifferent.erp.data.repository.FinancialYearRepository
import com.builtdifferent.erp.data.repository.HsnSacRepository
import com.builtdifferent.erp.data.repository.PartyRepository
import com.builtdifferent.erp.data.repository.ProductRepository
import com.builtdifferent.erp.data.repository.SalesInvoiceLineDraft
import com.builtdifferent.erp.data.repository.SalesInvoiceRepository
import com.builtdifferent.erp.data.repository.WarehouseRepository
import com.builtdifferent.erp.domain.engine.TaxEngine
import com.builtdifferent.erp.domain.engine.TaxLineInput
import com.builtdifferent.erp.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A line as it exists in the UI while the invoice is being built — before
 * posting, quantities/discounts can change freely; the resolved GST rate is
 * cached on the line so totals can be recomputed live without hitting the
 * database on every keystroke. */
data class DraftLine(
    val product: ProductEntity,
    val quantity: Double,
    val discountPercent: Double,
    val cgstRatePercent: Double,
    val sgstRatePercent: Double,
    val igstRatePercent: Double,
    val cessRatePercent: Double,
    val unitLabel: String,
    val hsnSacCode: String
)

data class NewSalesInvoiceUiState(
    val availableParties: List<PartyEntity> = emptyList(),
    val selectedParty: PartyEntity? = null,
    val lines: List<DraftLine> = emptyList(),
    val isInterState: Boolean = false,
    val isPosting: Boolean = false,
    val postedInvoiceId: Long? = null,
    val errorMessage: String? = null
) {
    val lineResults get() = lines.map {
        TaxEngine.calculateLine(
            TaxLineInput(
                quantity = it.quantity,
                unitPricePaise = it.product.sellingPricePaise,
                discountPercent = it.discountPercent,
                cgstRatePercent = it.cgstRatePercent,
                sgstRatePercent = it.sgstRatePercent,
                igstRatePercent = it.igstRatePercent,
                cessRatePercent = it.cessRatePercent,
                isInterState = isInterState
            )
        )
    }
    val totals get() = TaxEngine.calculateInvoiceTotals(lineResults)
}

class NewSalesInvoiceViewModel(
    private val companyRepository: CompanyRepository,
    private val financialYearRepository: FinancialYearRepository,
    private val partyRepository: PartyRepository,
    private val productRepository: ProductRepository,
    private val hsnSacRepository: HsnSacRepository,
    private val warehouseRepository: WarehouseRepository,
    private val salesInvoiceRepository: SalesInvoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewSalesInvoiceUiState())
    val uiState: StateFlow<NewSalesInvoiceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            partyRepository.observeParties(type = null).collect { parties ->
                _uiState.value = _uiState.value.copy(availableParties = parties)
            }
        }
    }

    fun selectParty(party: PartyEntity) {
        viewModelScope.launch {
            val company = companyRepository.getActiveCompany()
            val interState = company != null && company.stateCode.isNotBlank() &&
                party.billingStateCode.isNotBlank() && company.stateCode != party.billingStateCode
            _uiState.value = _uiState.value.copy(selectedParty = party, isInterState = interState)
        }
    }

    /** Adds a product as a new line, resolving its current GST rate for
     * today's date — the same lookup postInvoice's caller must always use,
     * so what the user sees while building the invoice matches exactly
     * what gets snapshotted on posting. */
    fun addProduct(product: ProductEntity) {
        viewModelScope.launch {
            val rate = hsnSacRepository.getRateEffectiveOn(product.hsnSacId, DateUtils.nowMillis())
            val hsnSac = hsnSacRepository.getById(product.hsnSacId)
            val line = DraftLine(
                product = product,
                quantity = 1.0,
                discountPercent = 0.0,
                cgstRatePercent = rate?.cgstRatePercent ?: 0.0,
                sgstRatePercent = rate?.sgstRatePercent ?: 0.0,
                igstRatePercent = rate?.igstRatePercent ?: 0.0,
                cessRatePercent = rate?.cessRatePercent ?: 0.0,
                unitLabel = hsnSac?.code ?: "",
                hsnSacCode = hsnSac?.code ?: ""
            )
            _uiState.value = _uiState.value.copy(lines = _uiState.value.lines + line)
        }
    }

    fun updateLineQuantity(index: Int, quantity: Double) {
        val lines = _uiState.value.lines.toMutableList()
        if (index !in lines.indices) return
        lines[index] = lines[index].copy(quantity = quantity)
        _uiState.value = _uiState.value.copy(lines = lines)
    }

    fun updateLineDiscount(index: Int, discountPercent: Double) {
        val lines = _uiState.value.lines.toMutableList()
        if (index !in lines.indices) return
        lines[index] = lines[index].copy(discountPercent = discountPercent)
        _uiState.value = _uiState.value.copy(lines = lines)
    }

    fun removeLine(index: Int) {
        val lines = _uiState.value.lines.toMutableList()
        if (index !in lines.indices) return
        lines.removeAt(index)
        _uiState.value = _uiState.value.copy(lines = lines)
    }

    fun postInvoice() {
        val state = _uiState.value
        val party = state.selectedParty
        if (party == null) {
            _uiState.value = state.copy(errorMessage = "Select a customer"); return
        }
        if (state.lines.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Add at least one product"); return
        }

        _uiState.value = state.copy(isPosting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val company = companyRepository.getActiveCompany()
                    ?: throw IllegalStateException("No company configured")
                val financialYear = financialYearRepository.getCurrentFinancialYear(company.companyId)
                    ?: throw IllegalStateException("No active financial year")
                val warehouseId = warehouseRepository.getOrCreateDefaultWarehouseId()

                val drafts = state.lines.map {
                    SalesInvoiceLineDraft(
                        productId = it.product.productId,
                        productName = it.product.productName,
                        hsnSacCode = it.hsnSacCode,
                        quantity = it.quantity,
                        unitLabel = it.unitLabel,
                        unitPricePaise = it.product.sellingPricePaise,
                        discountPercent = it.discountPercent,
                        cgstRatePercent = it.cgstRatePercent,
                        sgstRatePercent = it.sgstRatePercent,
                        igstRatePercent = it.igstRatePercent,
                        cessRatePercent = it.cessRatePercent
                    )
                }

                val invoiceId = salesInvoiceRepository.postInvoice(
                    company = company,
                    party = party,
                    warehouseId = warehouseId,
                    financialYearId = financialYear.financialYearId,
                    invoiceDateMillis = DateUtils.nowMillis(),
                    isInterState = state.isInterState,
                    placeOfSupplyStateCode = party.billingStateCode.ifBlank { company.stateCode },
                    lines = drafts
                )
                _uiState.value = _uiState.value.copy(isPosting = false, postedInvoiceId = invoiceId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isPosting = false, errorMessage = e.message ?: "Failed to post invoice")
            }
        }
    }
}
