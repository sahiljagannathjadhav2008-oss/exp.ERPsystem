package com.builtdifferent.erp.ui.screens.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.builtdifferent.erp.data.local.entity.ProductEntity
import com.builtdifferent.erp.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductListViewModel,
    onAddProduct: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Products") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProduct) { Icon(Icons.Filled.Add, contentDescription = "Add Product") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchChanged,
                label = { Text("Search by name or SKU") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            if (state.products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No products yet. Tap + to add your first product.")
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.products, key = { it.productId }) { product ->
                        ProductRow(product = product, onClick = { onOpenProduct(product.productId) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: ProductEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(product.productName) },
        supportingContent = { Text("SKU: ${product.sku}") },
        trailingContent = { Text(Money(product.sellingPricePaise).formatIndian()) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
