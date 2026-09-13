package com.builtdifferent.erp.ui.screens.party

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
import com.builtdifferent.erp.data.local.entity.PartyType
import com.builtdifferent.erp.util.Money
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyEditScreen(
    viewModel: PartyEditViewModel,
    partyId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(partyId) { viewModel.loadParty(partyId) }
    LaunchedEffect(state.savedSuccessfully) { if (state.savedSuccessfully) onSaved() }

    var openingBalanceText by remember(state.party.partyId) {
        mutableStateOf(if (state.party.openingBalancePaise == 0L) "" else Money(state.party.openingBalancePaise).toRupees().toPlainString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (partyId == 0L) "Add Party" else "Edit Party") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(PartyType.CUSTOMER, PartyType.SUPPLIER, PartyType.BOTH)
                options.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.party.type == type,
                        onClick = { viewModel.updateParty { it.copy(type = type) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) { Text(type.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                }
            }

            OutlinedTextField(
                value = state.party.partyName,
                onValueChange = { v -> viewModel.updateParty { it.copy(partyName = v) } },
                label = { Text("Party Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.party.phone,
                onValueChange = { v -> viewModel.updateParty { it.copy(phone = v) } },
                label = { Text("Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.party.email,
                onValueChange = { v -> viewModel.updateParty { it.copy(email = v) } },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.party.isUnregistered,
                    onCheckedChange = { v -> viewModel.updateParty { it.copy(isUnregistered = v) } }
                )
                Text("Unregistered / consumer (no GSTIN)")
            }
            if (!state.party.isUnregistered) {
                OutlinedTextField(
                    value = state.party.gstin ?: "",
                    onValueChange = { v -> viewModel.updateParty { it.copy(gstin = v.uppercase()) } },
                    label = { Text("GSTIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Text("Billing Address", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.party.billingAddressLine1,
                onValueChange = { v -> viewModel.updateParty { it.copy(billingAddressLine1 = v) } },
                label = { Text("Address Line 1") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.party.billingCity,
                    onValueChange = { v -> viewModel.updateParty { it.copy(billingCity = v) } },
                    label = { Text("City") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.party.billingStateCode,
                    onValueChange = { v -> viewModel.updateParty { it.copy(billingStateCode = v) } },
                    label = { Text("State Code") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = openingBalanceText,
                onValueChange = { v ->
                    openingBalanceText = v
                    val amount = v.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    viewModel.updateParty { it.copy(openingBalancePaise = Money.fromRupees(amount).paise) }
                },
                label = { Text("Opening Balance (₹, +ve = they owe you)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { focusManager.clearFocus(); viewModel.save() },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "Saving..." else "Save Party")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    if (this.isBlank()) null else BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
