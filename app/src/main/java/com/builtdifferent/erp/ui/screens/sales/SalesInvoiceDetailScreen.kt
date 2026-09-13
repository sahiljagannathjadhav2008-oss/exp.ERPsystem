package com.builtdifferent.erp.ui.screens.sales

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.builtdifferent.erp.data.local.entity.PaymentMode
import com.builtdifferent.erp.util.DateUtils
import com.builtdifferent.erp.util.Money
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesInvoiceDetailScreen(
    viewModel: SalesInvoiceDetailViewModel,
    paymentViewModel: RecordPaymentViewModel,
    invoiceId: Long,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val paymentState by paymentViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPaymentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(invoiceId) { viewModel.load(invoiceId) }
    LaunchedEffect(state.invoice) {
        state.invoice?.let { paymentViewModel.load(it.salesInvoiceId, it.partyId, it.grandTotalPaise) }
    }
    LaunchedEffect(paymentState.saved) {
        if (paymentState.saved) {
            showPaymentDialog = false
            state.invoice?.let { paymentViewModel.load(it.salesInvoiceId, it.partyId, it.grandTotalPaise) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.invoice?.invoiceNumber ?: "Invoice") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { viewModel.generatePdf(context.cacheDir) }) {
                        Text(if (state.isGeneratingPdf) "Generating..." else "Share PDF")
                    }
                }
            )
        }
    ) { padding ->
        val invoice = state.invoice
        if (invoice == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(DateUtils.formatDate(invoice.invoiceDateMillis), style = MaterialTheme.typography.bodyMedium)
            Text("Status: ${invoice.status}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(state.items) { item ->
                    ListItem(
                        headlineContent = { Text(item.productNameSnapshot) },
                        supportingContent = { Text("Qty ${item.quantity} × ${Money(item.unitPricePaise).formatIndian()}") },
                        trailingContent = { Text(Money(item.lineTotalPaise).formatIndian()) }
                    )
                    HorizontalDivider()
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Grand Total: ${Money(invoice.grandTotalPaise).formatIndian()}", style = MaterialTheme.typography.titleMedium)
            Text(invoice.amountInWords, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Outstanding: ${Money(paymentState.outstandingPaise).formatIndian()}",
                style = MaterialTheme.typography.titleMedium,
                color = if (paymentState.outstandingPaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            if (paymentState.outstandingPaise > 0) {
                Button(onClick = { showPaymentDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Record Payment")
                }
            }
        }
    }

    LaunchedEffect(state.pdfFile) {
        state.pdfFile?.let { file ->
            val uri = FileProvider.getUriForFile(context, "com.builtdifferent.erp.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Invoice"))
            viewModel.onPdfShared()
        }
    }

    if (showPaymentDialog) {
        RecordPaymentDialog(
            state = paymentState,
            onAmountChange = { text ->
                val amount = text.toBigDecimalOrNull() ?: BigDecimal.ZERO
                paymentViewModel.updateAmount(Money.fromRupees(amount).paise)
            },
            onModeChange = paymentViewModel::updateMode,
            onReferenceChange = paymentViewModel::updateReference,
            onSave = paymentViewModel::save,
            onDismiss = { showPaymentDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentDialog(
    state: RecordPaymentUiState,
    onAmountChange: (String) -> Unit,
    onModeChange: (PaymentMode) -> Unit,
    onReferenceChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember(state.outstandingPaise) {
        mutableStateOf(Money(state.amountPaise).toRupees().toPlainString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Outstanding: ${Money(state.outstandingPaise).formatIndian()}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; onAmountChange(it) },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(PaymentMode.CASH, PaymentMode.BANK_TRANSFER, PaymentMode.UPI, PaymentMode.CHEQUE)
                    options.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                        ) { Text(mode.name.take(4)) }
                    }
                }
                if (state.mode != PaymentMode.CASH) {
                    OutlinedTextField(
                        value = state.referenceNumber,
                        onValueChange = onReferenceChange,
                        label = { Text("Reference / UTR / Cheque No.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) { Text(if (state.isSaving) "Saving..." else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    if (this.isBlank()) null else BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
