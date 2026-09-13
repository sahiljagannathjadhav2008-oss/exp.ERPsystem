package com.builtdifferent.erp.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.PartyType
import com.builtdifferent.erp.data.local.entity.ProductEntity
import com.builtdifferent.erp.data.repository.CompanyRepository
import com.builtdifferent.erp.data.repository.FinancialYearRepository
import com.builtdifferent.erp.data.repository.HsnSacRepository
import com.builtdifferent.erp.data.repository.PartyRepository
import com.builtdifferent.erp.data.repository.PurchaseInvoiceLineDraft
import com.builtdifferent.erp.data.repository.PurchaseInvoiceRepository
import com.builtdifferent.erp.data.repository.WarehouseRepository
import com.builtdifferent.erp.domain.engine.TaxEngine
import com.builtdifferent.erp.domain.engine.TaxLineInput
import com.builtdifferent.erp.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PurchaseDraftLine(
    val product: ProductEntity,
    val quantity: Double,
    val unitCostPaise: Long,
    val discountPercent: Double,
    val cgstRatePercent: Double,
    val sgstRatePercent: Double,
    val igstRatePercent: Double,
    val cessRatePercent: Double,
    val hsnSacCode: String
)

data class NewPurchaseInvoiceUiState(
    val availableSuppliers: List<PartyEntity> = emptyList(),
    val selectedSupplier: PartyEntity? = null,
    val supplierInvoiceNumber: String = "",
    val lines: List<PurchaseDraftLine> = emptyList(),
    val isInterState: Boolean = false,
    val isPosting: Boolean = false,
    val postedInvoiceId: Long? = null,
    val errorMessage: String? = null
) {
    val lineResults get() = lines.map {
        TaxEngine.calculateLine(
            TaxLineInput(
                quantity = it.quantity,
                unitPricePaise = it.unitCostPaise,
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

class NewPurchaseInvoiceViewModel(
    private val companyRepository: CompanyRepository,
    private val financialYearRepository: FinancialYearRepository,
    private val partyRepository: PartyRepository,
    private val hsnSacRepository: HsnSacRepository,
    private val warehouseRepository: WarehouseRepository,
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewPurchaseInvoiceUiState())
    val uiState: StateFlow<NewPurchaseInvoiceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            partyRepository.observeParties(type = PartyType.SUPPLIER).collect { suppliers ->
                _uiState.value = _uiState.value.copy(availableSuppliers = suppliers)
            }
        }
    }

    fun selectSupplier(supplier: PartyEntity) {
        viewModelScope.launch {
            val company = companyRepository.getActiveCompany()
            val interState = company != null && company.stateCode.isNotBlank() &&
                supplier.billingStateCode.isNotBlank() && company.stateCode != supplier.billingStateCode
            _uiState.value = _uiState.value.copy(selectedSupplier = supplier, isInterState = interState)
        }
    }

    fun updateSupplierInvoiceNumber(value: String) {
        _uiState.value = _uiState.value.copy(supplierInvoiceNumber = value)
    }

    fun addProduct(product: ProductEntity) {
        viewModelScope.launch {
            val rate = hsnSacRepository.getRateEffectiveOn(product.hsnSacId, DateUtils.nowMillis())
            val hsnSac = hsnSacRepository.getById(product.hsnSacId)
            val line = PurchaseDraftLine(
                product = product,
                quantity = 1.0,
                unitCostPaise = product.purchasePricePaise,
                discountPercent = 0.0,
                cgstRatePercent = rate?.cgstRatePercent ?: 0.0,
                sgstRatePercent = rate?.sgstRatePercent ?: 0.0,
                igstRatePercent = rate?.igstRatePercent ?: 0.0,
                cessRatePercent = rate?.cessRatePercent ?: 0.0,
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

    fun updateLineCost(index: Int, costPaise: Long) {
        val lines = _uiState.value.lines.toMutableList()
        if (index !in lines.indices) return
        lines[index] = lines[index].copy(unitCostPaise = costPaise)
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
        val supplier = state.selectedSupplier
        if (supplier == null) {
            _uiState.value = state.copy(errorMessage = "Select a supplier"); return
        }
        if (state.supplierInvoiceNumber.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Enter the supplier's invoice number"); return
        }
        if (state.lines.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Add at least one product"); return
        }

        _uiState.value = state.copy(isPosting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val company = companyRepository.getActiveCompany() ?: throw IllegalStateException("No company configured")
                val financialYear = financialYearRepository.getCurrentFinancialYear(company.companyId)
                    ?: throw IllegalStateException("No active financial year")
                val warehouseId = warehouseRepository.getOrCreateDefaultWarehouseId()

                val drafts = state.lines.map {
                    PurchaseInvoiceLineDraft(
                        productId = it.product.productId,
                        productName = it.product.productName,
                        hsnSacCode = it.hsnSacCode,
                        quantity = it.quantity,
                        unitLabel = it.hsnSacCode,
                        unitPricePaise = it.unitCostPaise,
                        discountPercent = it.discountPercent,
                        cgstRatePercent = it.cgstRatePercent,
                        sgstRatePercent = it.sgstRatePercent,
                        igstRatePercent = it.igstRatePercent,
                        cessRatePercent = it.cessRatePercent
                    )
                }

                val invoiceId = purchaseInvoiceRepository.postInvoice(
                    company = company,
                    supplier = supplier,
                    warehouseId = warehouseId,
                    financialYearId = financialYear.financialYearId,
                    supplierInvoiceNumber = state.supplierInvoiceNumber,
                    invoiceDateMillis = DateUtils.nowMillis(),
                    isInterState = state.isInterState,
                    lines = drafts
                )
                _uiState.value = _uiState.value.copy(isPosting = false, postedInvoiceId = invoiceId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isPosting = false, errorMessage = e.message ?: "Failed to post purchase invoice")
            }
        }
    }
}
