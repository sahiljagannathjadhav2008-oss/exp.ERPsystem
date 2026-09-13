package com.builtdifferent.erp.ui.screens.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.erp.data.local.entity.HsnSacEntity
import com.builtdifferent.erp.data.local.entity.ProductCategoryEntity
import com.builtdifferent.erp.data.local.entity.ProductEntity
import com.builtdifferent.erp.data.local.entity.UnitEntity
import com.builtdifferent.erp.data.repository.HsnSacRepository
import com.builtdifferent.erp.data.repository.ProductCategoryRepository
import com.builtdifferent.erp.data.repository.ProductRepository
import com.builtdifferent.erp.data.repository.UnitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductListUiState(
    val products: List<ProductEntity> = emptyList(),
    val searchQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProductListViewModel(private val productRepository: ProductRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ProductListUiState> = searchQuery
        .flatMapLatest { query ->
            productRepository.search(query).combine(searchQuery) { products, q ->
                ProductListUiState(products = products, searchQuery = q)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductListUiState())

    fun onSearchChanged(query: String) { searchQuery.value = query }
}

data class ProductEditUiState(
    val product: ProductEntity = ProductEntity(productName = "", sku = "", unitId = 0, hsnSacId = 0),
    val units: List<UnitEntity> = emptyList(),
    val categories: List<ProductCategoryEntity> = emptyList(),
    val hsnSacCodes: List<HsnSacEntity> = emptyList(),
    val currentGstRatePercent: Double? = null,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val errorMessage: String? = null
)

class ProductEditViewModel(
    private val productRepository: ProductRepository,
    private val unitRepository: UnitRepository,
    private val categoryRepository: ProductCategoryRepository,
    private val hsnSacRepository: HsnSacRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductEditUiState())
    val uiState: StateFlow<ProductEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            unitRepository.observeAll().collect { units ->
                _uiState.value = _uiState.value.copy(units = units)
            }
        }
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
            }
        }
        viewModelScope.launch {
            hsnSacRepository.observeAll().collect { codes ->
                _uiState.value = _uiState.value.copy(hsnSacCodes = codes)
            }
        }
    }

    fun loadProduct(productId: Long) {
        if (productId <= 0) return
        viewModelScope.launch {
            productRepository.getById(productId)?.let { product ->
                _uiState.value = _uiState.value.copy(product = product)
                refreshCurrentRate(product.hsnSacId)
            }
        }
    }

    fun updateProduct(transform: (ProductEntity) -> ProductEntity) {
        val updated = transform(_uiState.value.product)
        _uiState.value = _uiState.value.copy(product = updated, savedSuccessfully = false)
        if (updated.hsnSacId > 0) refreshCurrentRate(updated.hsnSacId)
    }

    private fun refreshCurrentRate(hsnSacId: Long) {
        viewModelScope.launch {
            val rate = hsnSacRepository.getCurrentRate(hsnSacId)
            _uiState.value = _uiState.value.copy(currentGstRatePercent = rate?.totalRatePercent)
        }
    }

    fun save() {
        val product = _uiState.value.product
        when {
            product.productName.isBlank() -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Product name is required"); return
            }
            product.sku.isBlank() -> {
                _uiState.value = _uiState.value.copy(errorMessage = "SKU is required"); return
            }
            product.unitId <= 0 -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Select a unit"); return
            }
            product.hsnSacId <= 0 -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Select an HSN/SAC code"); return
            }
        }
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            if (product.productId == 0L) {
                val id = productRepository.createProduct(product)
                val saved = productRepository.getById(id) ?: product
                _uiState.value = _uiState.value.copy(product = saved, isSaving = false, savedSuccessfully = true)
            } else {
                productRepository.updateProduct(product, priceChanged = true)
                _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
            }
        }
    }
}
