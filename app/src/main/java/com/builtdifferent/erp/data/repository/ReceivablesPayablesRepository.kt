package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.AllocationTargetType
import com.builtdifferent.erp.data.local.entity.InvoiceStatus
import kotlinx.coroutines.flow.first

data class PartyOutstanding(
    val partyId: Long,
    val partyName: String,
    val totalBilledPaise: Long,
    val totalAllocatedPaise: Long,
    val outstandingPaise: Long
)

/**
 * Receivables (money owed BY customers) and Payables (money owed TO
 * suppliers) are never separately maintained running totals — that would
 * be a second source of truth that could drift from the invoices and
 * payments underneath it. Instead, this repository always recomputes, on
 * demand, from the same two facts the rest of the app already trusts:
 * SUM(posted invoice grand totals) - SUM(payment allocations against
 * them), per party. More DB work than caching a balance column, in
 * exchange for a number that can never be wrong.
 */
class ReceivablesPayablesRepository(private val db: AppDatabase) {

    suspend fun getReceivablesByCustomer(financialYearId: Long?): List<PartyOutstanding> {
        val invoiceList = db.salesInvoiceDao().observeInvoices(financialYearId, InvoiceStatus.POSTED).first()
        val byParty = invoiceList.groupBy { it.partyId }

        return byParty.map { (partyId, invoicesForParty) ->
            val party = db.partyDao().getById(partyId)
            val totalBilled = invoicesForParty.sumOf { it.grandTotalPaise }
            val totalAllocated = invoicesForParty.sumOf {
                db.paymentAllocationDao().getTotalAllocated(AllocationTargetType.SALES_INVOICE, it.salesInvoiceId)
            }
            PartyOutstanding(
                partyId = partyId,
                partyName = party?.partyName ?: "Unknown",
                totalBilledPaise = totalBilled,
                totalAllocatedPaise = totalAllocated,
                outstandingPaise = totalBilled - totalAllocated
            )
        }.filter { it.outstandingPaise != 0L }
    }

    suspend fun getPayablesBySupplier(financialYearId: Long?): List<PartyOutstanding> {
        val invoiceList = db.purchaseInvoiceDao().observeInvoices(financialYearId, InvoiceStatus.POSTED).first()
        val byParty = invoiceList.groupBy { it.partyId }

        return byParty.map { (partyId, invoicesForParty) ->
            val party = db.partyDao().getById(partyId)
            val totalBilled = invoicesForParty.sumOf { it.grandTotalPaise }
            val totalAllocated = invoicesForParty.sumOf {
                db.paymentAllocationDao().getTotalAllocated(AllocationTargetType.PURCHASE_INVOICE, it.purchaseInvoiceId)
            }
            PartyOutstanding(
                partyId = partyId,
                partyName = party?.partyName ?: "Unknown",
                totalBilledPaise = totalBilled,
                totalAllocatedPaise = totalAllocated,
                outstandingPaise = totalBilled - totalAllocated
            )
        }.filter { it.outstandingPaise != 0L }
    }
}
