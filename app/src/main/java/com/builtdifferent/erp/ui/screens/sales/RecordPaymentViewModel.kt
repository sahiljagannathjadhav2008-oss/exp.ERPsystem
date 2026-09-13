package com.builtdifferent.erp.ui.screens.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.entity.AllocationTargetType
import com.builtdifferent.erp.data.local.entity.PaymentMode
import com.builtdifferent.erp.data.repository.CompanyRepository
import com.builtdifferent.erp.data.repository.FinancialYearRepository
import com.builtdifferent.erp.data.repository.PartyRepository
import com.builtdifferent.erp.data.repository.PaymentAllocationInput
import com.builtdifferent.erp.data.repository.PaymentRepository
import com.builtdifferent.erp.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordPaymentUiState(
    val outstandingPaise: Long = 0,
    val amountPaise: Long = 0,
    val mode: PaymentMode = PaymentMode.CASH,
    val referenceNumber: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
)

/** Handles the common case — a single payment fully or partially allocated
 * to the one invoice it was opened from. Recording an on-account payment
 * split across several invoices is supported by PaymentRepository itself
 * (it takes a list of allocations) but doesn't yet have a dedicated
 * multi-invoice picker screen. */
class RecordPaymentViewModel(
    private val paymentRepository: PaymentRepository,
    private val companyRepository: CompanyRepository,
    private val financialYearRepository: FinancialYearRepository,
    private val partyRepository: PartyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordPaymentUiState())
    val uiState: StateFlow<RecordPaymentUiState> = _uiState.asStateFlow()

    private var invoiceId: Long = 0
    private var partyId: Long = 0

    fun load(invoiceId: Long, partyId: Long, grandTotalPaise: Long) {
        this.invoiceId = invoiceId
        this.partyId = partyId
        viewModelScope.launch {
            val outstanding = paymentRepository.getOutstanding(AllocationTargetType.SALES_INVOICE, invoiceId, grandTotalPaise)
            _uiState.value = _uiState.value.copy(outstandingPaise = outstanding, amountPaise = outstanding)
        }
    }

    fun updateAmount(paise: Long) {
        _uiState.value = _uiState.value.copy(amountPaise = paise, saved = false)
    }

    fun updateMode(mode: PaymentMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun updateReference(reference: String) {
        _uiState.value = _uiState.value.copy(referenceNumber = reference)
    }

    fun save() {
        val state = _uiState.value
        if (state.amountPaise <= 0) {
            _uiState.value = state.copy(errorMessage = "Enter an amount greater than zero"); return
        }
        if (state.amountPaise > state.outstandingPaise) {
            _uiState.value = state.copy(errorMessage = "Amount cannot exceed the outstanding balance"); return
        }
        _uiState.value = state.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val company = companyRepository.getActiveCompany() ?: throw IllegalStateException("No company configured")
                val financialYear = financialYearRepository.getCurrentFinancialYear(company.companyId)
                    ?: throw IllegalStateException("No active financial year")
                val party = partyRepository.getById(partyId) ?: throw IllegalStateException("Party not found")
                val ledgerAccountId = party.ledgerAccountId ?: throw IllegalStateException("Party has no ledger account")

                paymentRepository.recordPaymentReceived(
                    partyId = partyId,
                    partyLedgerAccountId = ledgerAccountId,
                    financialYearId = financialYear.financialYearId,
                    receiptNumber = "RCPT-${DateUtils.nowMillis()}",
                    paymentDateMillis = DateUtils.nowMillis(),
                    amountPaise = state.amountPaise,
                    mode = state.mode,
                    bankAccountId = null,
                    referenceNumber = state.referenceNumber,
                    note = "",
                    allocations = listOf(
                        PaymentAllocationInput(AllocationTargetType.SALES_INVOICE, invoiceId, state.amountPaise)
                    )
                )
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = e.message ?: "Failed to record payment")
            }
        }
    }
}
