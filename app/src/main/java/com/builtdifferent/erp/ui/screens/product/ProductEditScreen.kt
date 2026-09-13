package com.builtdifferent.erp.ui.screens.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.builtdifferent.erp.data.local.entity.HsnSacEntity
import com.builtdifferent.erp.data.local.entity.ProductCategoryEntity
import com.builtdifferent.erp.data.local.entity.UnitEntity
import com.builtdifferent.erp.util.Money
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    viewModel: ProductEditViewModel,
    productId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(productId) { viewModel.loadProduct(productId) }
    LaunchedEffect(state.savedSuccessfully) { if (state.savedSuccessfully) onSaved() }

    var sellingPriceText by remember(state.product.productId) {
        mutableStateOf(if (state.product.sellingPricePaise == 0L) "" else Money(state.product.sellingPricePaise).toRupees().toPlainString())
    }
    var purchasePriceText by remember(state.product.productId) {
        mutableStateOf(if (state.product.purchasePricePaise == 0L) "" else Money(state.product.purchasePricePaise).toRupees().toPlainString())
    }
    var openingQtyText by remember(state.product.productId) {
        mutableStateOf(if (state.product.openingStockQty == 0.0) "" else state.product.openingStockQty.toString())
    }
    var openingValueText by remember(state.product.productId) {
        mutableStateOf(if (state.product.openingStockValuePaise == 0L) "" else Money(state.product.openingStockValuePaise).toRupees().toPlainString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == 0L) "Add Product" else "Edit Product") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.product.productName,
                onValueChange = { v -> viewModel.updateProduct { it.copy(productName = v) } },
                label = { Text("Product Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.product.sku,
                onValueChange = { v -> viewModel.updateProduct { it.copy(sku = v) } },
                label = { Text("SKU *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            DropdownField(
                label = "Unit *",
                options = state.units,
                selectedId = state.product.unitId,
                idOf = { it.unitId },
                labelOf = { it.displayLabel },
                onSelect = { unit -> viewModel.updateProduct { it.copy(unitId = unit.unitId) } }
            )

            DropdownField(
                label = "Category",
                options = state.categories,
                selectedId = state.product.categoryId ?: 0L,
                idOf = { it.categoryId },
                labelOf = { it.name },
                onSelect = { category -> viewModel.updateProduct { it.copy(categoryId = category.categoryId) } }
            )

            DropdownField(
                label = "HSN/SAC Code *",
                options = state.hsnSacCodes,
                selectedId = state.product.hsnSacId,
                idOf = { it.hsnSacId },
                labelOf = { "${it.code} — ${it.description}".trimEnd(' ', '—') },
                onSelect = { code -> viewModel.updateProduct { it.copy(hsnSacId = code.hsnSacId) } }
            )
            state.currentGstRatePercent?.let {
                Text("Current GST rate for this HSN/SAC: $it%", style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sellingPriceText,
                    onValueChange = { v ->
                        sellingPriceText = v
                        val amount = v.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        viewModel.updateProduct { it.copy(sellingPricePaise = Money.fromRupees(amount).paise) }
                    },
                    label = { Text("Selling Price (excl. GST)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = purchasePriceText,
                    onValueChange = { v ->
                        purchasePriceText = v
                        val amount = v.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        viewModel.updateProduct { it.copy(purchasePricePaise = Money.fromRupees(amount).paise) }
                    },
                    label = { Text("Purchase Price (excl. GST)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.product.trackInventory,
                    onCheckedChange = { v -> viewModel.updateProduct { it.copy(trackInventory = v) } }
                )
                Text("Track inventory for this product")
            }

            if (state.product.trackInventory && productId == 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = openingQtyText,
                        onValueChange = { v ->
                            openingQtyText = v
                            viewModel.updateProduct { it.copy(openingStockQty = v.toDoubleOrNull() ?: 0.0) }
                        },
                        label = { Text("Opening Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = openingValueText,
                        onValueChange = { v ->
                            openingValueText = v
                            val amount = v.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            viewModel.updateProduct { it.copy(openingStockValuePaise = Money.fromRupees(amount).paise) }
                        },
                        label = { Text("Opening Stock Value (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = state.product.reorderLevelQty.let { if (it == 0.0) "" else it.toString() },
                onValueChange = { v -> viewModel.updateProduct { it.copy(reorderLevelQty = v.toDoubleOrNull() ?: 0.0) } },
                label = { Text("Reorder Level Qty") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { focusManager.clearFocus(); viewModel.save() },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "Saving..." else "Save Product")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    options: List<T>,
    selectedId: Long,
    idOf: (T) -> Long,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.find { idOf(it) == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(labelOf) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // MenuAnchorType.PrimaryNotEditable: this text field is
            // read-only (selection happens via the dropdown, not typing),
            // which is the modern replacement for the deprecated no-arg
            // menuAnchor() overload.
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("None available yet") }, onClick = { expanded = false }, enabled = false)
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    if (this.isBlank()) null else BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
