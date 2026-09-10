package com.builtdifferent.erp.ui.screens.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.ProductEntity
import com.builtdifferent.erp.ui.screens.product.ProductListViewModel
import com.builtdifferent.erp.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSalesInvoiceScreen(
    viewModel: NewSalesInvoiceViewModel,
    productListViewModel: ProductListViewModel,
    onPosted: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val productState by productListViewModel.uiState.collectAsState()

    var showPartyPicker by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.postedInvoiceId) {
        state.postedInvoiceId?.let(onPosted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Sales Invoice") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedCard(onClick = { showPartyPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Customer", style = MaterialTheme.typography.labelLarge)
                    Text(state.selectedParty?.partyName ?: "Tap to select a customer", style = MaterialTheme.typography.titleMedium)
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Items", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showProductPicker = true }) { Text("Add Product") }
            }

            if (state.lines.isEmpty()) {
                Text("No items added yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.lines.forEachIndexed { index, line ->
                    val result = state.lineResults[index]
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(line.product.productName, style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { viewModel.removeLine(index) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = line.quantity.toString(),
                                    onValueChange = { v -> viewModel.updateLineQuantity(index, v.toDoubleOrNull() ?: 0.0) },
                                    label = { Text("Qty") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = if (line.discountPercent == 0.0) "" else line.discountPercent.toString(),
                                    onValueChange = { v -> viewModel.updateLineDiscount(index, v.toDoubleOrNull() ?: 0.0) },
                                    label = { Text("Disc %") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                "Rate ${Money(line.product.sellingPricePaise).formatIndian()} • " +
                                    "GST ${line.cgstRatePercent + line.sgstRatePercent + line.igstRatePercent}% • " +
                                    "Line Total ${Money(result.lineTotalPaise).formatIndian()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            val totals = state.totals
            SummaryRow("Subtotal", Money(totals.subTotalPaise).formatIndian())
            if (totals.totalDiscountPaise > 0) SummaryRow("Discount", "- " + Money(totals.totalDiscountPaise).formatIndian())
            SummaryRow("Taxable Value", Money(totals.totalTaxableValuePaise).formatIndian())
            if (totals.totalCgstPaise > 0) SummaryRow("CGST", Money(totals.totalCgstPaise).formatIndian())
            if (totals.totalSgstPaise > 0) SummaryRow("SGST", Money(totals.totalSgstPaise).formatIndian())
            if (totals.totalIgstPaise > 0) SummaryRow("IGST", Money(totals.totalIgstPaise).formatIndian())
            if (totals.roundOffPaise != 0L) SummaryRow("Round Off", Money(totals.roundOffPaise).formatIndian())
            SummaryRow("Grand Total", Money(totals.grandTotalPaise).formatIndian(), emphasize = true)

            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = viewModel::postInvoice,
                enabled = !state.isPosting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isPosting) "Posting..." else "Post Invoice")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPartyPicker) {
        PartyPickerDialog(
            parties = state.availableParties,
            onDismiss = { showPartyPicker = false },
            onSelect = { viewModel.selectParty(it); showPartyPicker = false }
        )
    }
    if (showProductPicker) {
        ProductPickerDialog(
            products = productState.products,
            onQueryChange = productListViewModel::onSearchChanged,
            onDismiss = { showProductPicker = false },
            onSelect = { viewModel.addProduct(it); showProductPicker = false }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PartyPickerDialog(parties: List<PartyEntity>, onDismiss: () -> Unit, onSelect: (PartyEntity) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Select Customer") },
        text = {
            LazyColumn {
                items(parties) { party ->
                    ListItem(
                        headlineContent = { Text(party.partyName) },
                        supportingContent = { Text(party.phone) },
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(party) }
                    )
                }
            }
        }
    )
}

@Composable
private fun ProductPickerDialog(
    products: List<ProductEntity>,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (ProductEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Select Product") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onQueryChange(it) },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn {
                    items(products) { product ->
                        ListItem(
                            headlineContent = { Text(product.productName) },
                            supportingContent = { Text(Money(product.sellingPricePaise).formatIndian()) },
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(product) }
                        )
                    }
                }
            }
        }
    )
}
