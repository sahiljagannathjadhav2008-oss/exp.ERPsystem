package com.builtdifferent.erp.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.builtdifferent.erp.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenParties: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenSalesInvoices: () -> Unit,
    onOpenNewPurchaseInvoice: () -> Unit,
    onOpenCompanySettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.company?.tradeName?.takeIf { it.isNotBlank() } ?: state.company?.legalName ?: "Offline ERP") },
                actions = {
                    TextButton(onClick = onOpenCompanySettings) { Text("Company") }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Text("Overview", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(dashboardCards(state)) { card ->
                DashboardCard(card = card, onClick = {
                    when (card.title) {
                        "Parties" -> onOpenParties()
                        "Products" -> onOpenProducts()
                        "Sales Invoices" -> onOpenSalesInvoices()
                        "New Purchase" -> onOpenNewPurchaseInvoice()
                    }
                })
            }
        }
    }
}

private data class DashboardCardData(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val isWarning: Boolean = false
)

private fun dashboardCards(state: DashboardUiState): List<DashboardCardData> = listOf(
    DashboardCardData("Parties", state.partyCount.toString(), Icons.Filled.People),
    DashboardCardData("Products", state.productCount.toString(), Icons.Filled.Inventory2),
    DashboardCardData("Sales Invoices", state.invoiceCount.toString(), Icons.Filled.Receipt),
    DashboardCardData("New Purchase", "+", Icons.Filled.Receipt),
    DashboardCardData("Stock Value", Money(state.totalStockValuePaise).formatIndian(), Icons.Filled.Inventory2),
    DashboardCardData("Low Stock Items", state.lowStockCount.toString(), Icons.Filled.Warning, isWarning = state.lowStockCount > 0)
)

@Composable
private fun DashboardCard(card: DashboardCardData, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                imageVector = card.icon,
                contentDescription = null,
                tint = if (card.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(card.value, style = MaterialTheme.typography.headlineMedium)
            Text(card.title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
