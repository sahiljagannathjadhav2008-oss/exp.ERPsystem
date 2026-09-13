package com.builtdifferent.erp.ui.screens.companysetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.entity.CompanyEntity
import com.builtdifferent.erp.data.repository.CompanyRepository
import com.builtdifferent.erp.data.repository.FinancialYearRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompanySetupUiState(
    val company: CompanyEntity = CompanyEntity(legalName = ""),
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val errorMessage: String? = null
)

class CompanySetupViewModel(
    private val companyRepository: CompanyRepository,
    private val financialYearRepository: FinancialYearRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanySetupUiState())
    val uiState: StateFlow<CompanySetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            companyRepository.getActiveCompany()?.let { existing ->
                _uiState.value = _uiState.value.copy(company = existing)
            }
        }
    }

    fun updateCompany(transform: (CompanyEntity) -> CompanyEntity) {
        _uiState.value = _uiState.value.copy(company = transform(_uiState.value.company), savedSuccessfully = false)
    }

    fun save() {
        val company = _uiState.value.company
        if (company.legalName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Business name is required")
            return
        }
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val wasNew = company.companyId == 0L
            val companyId = companyRepository.saveCompany(company)
            if (wasNew) {
                financialYearRepository.createAndActivateFinancialYearContainingToday(
                    companyId = companyId,
                    startMonthDay = company.financialYearStartMonthDay
                )
            }
            val saved = companyRepository.getActiveCompany() ?: company.copy(companyId = companyId)
            _uiState.value = _uiState.value.copy(company = saved, isSaving = false, savedSuccessfully = true)
        }
    }
}
