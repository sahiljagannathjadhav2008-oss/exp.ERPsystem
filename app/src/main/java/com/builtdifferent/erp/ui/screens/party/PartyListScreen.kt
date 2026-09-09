package com.builtdifferent.erp.ui.screens.party

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
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.PartyType
import com.builtdifferent.erp.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyListScreen(
    viewModel: PartyListViewModel,
    onAddParty: () -> Unit,
    onOpenParty: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parties") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddParty) { Icon(Icons.Filled.Add, contentDescription = "Add Party") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchChanged,
                label = { Text("Search by name, phone, GSTIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 16.dp)) {
                val options = listOf<Pair<String, PartyType?>>(
                    "All" to null, "Customers" to PartyType.CUSTOMER, "Suppliers" to PartyType.SUPPLIER
                )
                options.forEachIndexed { index, (label, type) ->
                    SegmentedButton(
                        selected = state.filterType == type,
                        onClick = { viewModel.onFilterChanged(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.parties.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No parties yet. Tap + to add your first customer or supplier.")
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.parties, key = { it.partyId }) { party ->
                        PartyRow(party = party, onClick = { onOpenParty(party.partyId) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PartyRow(party: PartyEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(party.partyName) },
        supportingContent = {
            Text(
                buildString {
                    append(party.type.name.lowercase().replaceFirstChar { it.uppercase() })
                    if (party.phone.isNotBlank()) append(" • ${party.phone}")
                    if (!party.gstin.isNullOrBlank()) append(" • ${party.gstin}")
                }
            )
        },
        trailingContent = {
            if (party.openingBalancePaise != 0L) {
                Text(Money(party.openingBalancePaise).formatIndian())
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
