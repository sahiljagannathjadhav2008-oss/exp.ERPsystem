package com.builtdifferent.erp.ui.screens.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.builtdifferent.erp.data.local.entity.SalesInvoiceEntity
import com.builtdifferent.erp.util.DateUtils
import com.builtdifferent.erp.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesInvoiceListScreen(
    viewModel: SalesInvoiceListViewModel,
    onNewInvoice: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales Invoices") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewInvoice) { Icon(Icons.Filled.Add, contentDescription = "New Invoice") }
        }
    ) { padding ->
        if (state.invoices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No invoices yet. Tap + to create your first sale.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.invoices, key = { it.salesInvoiceId }) { invoice ->
                    InvoiceRow(invoice = invoice, onClick = { onOpenInvoice(invoice.salesInvoiceId) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InvoiceRow(invoice: SalesInvoiceEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(invoice.invoiceNumber) },
        supportingContent = { Text(DateUtils.formatDate(invoice.invoiceDateMillis) + " • " + invoice.status.name) },
        trailingContent = { Text(Money(invoice.grandTotalPaise).formatIndian()) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
