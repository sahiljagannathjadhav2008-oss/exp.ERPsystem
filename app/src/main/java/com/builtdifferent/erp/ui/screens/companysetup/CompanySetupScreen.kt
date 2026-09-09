package com.builtdifferent.erp.ui.screens.companysetup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySetupScreen(
    viewModel: CompanySetupViewModel,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onSaved()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Company Setup") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Business Details", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.company.legalName,
                onValueChange = { v -> viewModel.updateCompany { it.copy(legalName = v) } },
                label = { Text("Legal Business Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.company.tradeName ?: "",
                onValueChange = { v -> viewModel.updateCompany { it.copy(tradeName = v) } },
                label = { Text("Trade Name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.company.gstin ?: "",
                onValueChange = { v -> viewModel.updateCompany { it.copy(gstin = v.uppercase()) } },
                label = { Text("GSTIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.company.pan ?: "",
                onValueChange = { v -> viewModel.updateCompany { it.copy(pan = v.uppercase()) } },
                label = { Text("PAN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Address", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.company.addressLine1,
                onValueChange = { v -> viewModel.updateCompany { it.copy(addressLine1 = v) } },
                label = { Text("Address Line 1") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.company.addressLine2,
                onValueChange = { v -> viewModel.updateCompany { it.copy(addressLine2 = v) } },
                label = { Text("Address Line 2") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.company.city,
                    onValueChange = { v -> viewModel.updateCompany { it.copy(city = v) } },
                    label = { Text("City") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.company.pinCode,
                    onValueChange = { v -> viewModel.updateCompany { it.copy(pinCode = v) } },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.company.state,
                    onValueChange = { v -> viewModel.updateCompany { it.copy(state = v) } },
                    label = { Text("State") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.company.stateCode,
                    onValueChange = { v -> viewModel.updateCompany { it.copy(stateCode = v) } },
                    label = { Text("State Code") },
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Contact", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.company.phone,
                onValueChange = { v -> viewModel.updateCompany { it.copy(phone = v) } },
                label = { Text("Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.company.email,
                onValueChange = { v -> viewModel.updateCompany { it.copy(email = v) } },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Invoice Numbering", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.company.invoicePrefix,
                onValueChange = { v -> viewModel.updateCompany { it.copy(invoicePrefix = v) } },
                label = { Text("Invoice Prefix") },
                modifier = Modifier.fillMaxWidth()
            )

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { focusManager.clearFocus(); viewModel.save() },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "Saving..." else "Save & Continue")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
