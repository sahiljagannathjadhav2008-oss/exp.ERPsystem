package com.builtdifferent.erp.ui.screens.party

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.PartyType
import com.builtdifferent.erp.data.repository.PartyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PartyListUiState(
    val parties: List<PartyEntity> = emptyList(),
    val searchQuery: String = "",
    val filterType: PartyType? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class PartyListViewModel(private val partyRepository: PartyRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filterType = MutableStateFlow<PartyType?>(null)
    private val filters = combine(searchQuery, filterType) { q, t -> q to t }

    val uiState: StateFlow<PartyListUiState> = filters
        .flatMapLatest { (query, type) ->
            partyRepository.observeParties(type, query).combine(filters) { parties, (q, t) ->
                PartyListUiState(parties = parties, searchQuery = q, filterType = t)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PartyListUiState())

    fun onSearchChanged(query: String) { searchQuery.value = query }
    fun onFilterChanged(type: PartyType?) { filterType.value = type }
}

data class PartyEditUiState(
    val party: PartyEntity = PartyEntity(partyName = "", type = PartyType.CUSTOMER),
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val errorMessage: String? = null
)

class PartyEditViewModel(private val partyRepository: PartyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PartyEditUiState())
    val uiState: StateFlow<PartyEditUiState> = _uiState.asStateFlow()

    fun loadParty(partyId: Long) {
        if (partyId <= 0) return
        viewModelScope.launch {
            partyRepository.getById(partyId)?.let { party ->
                _uiState.value = _uiState.value.copy(party = party)
            }
        }
    }

    fun updateParty(transform: (PartyEntity) -> PartyEntity) {
        _uiState.value = _uiState.value.copy(party = transform(_uiState.value.party), savedSuccessfully = false)
    }

    fun save() {
        val party = _uiState.value.party
        if (party.partyName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Party name is required")
            return
        }
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            if (party.partyId == 0L) {
                val id = partyRepository.createParty(party)
                val saved = partyRepository.getById(id) ?: party
                _uiState.value = _uiState.value.copy(party = saved, isSaving = false, savedSuccessfully = true)
            } else {
                partyRepository.updateParty(party)
                _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
            }
        }
    }
}
