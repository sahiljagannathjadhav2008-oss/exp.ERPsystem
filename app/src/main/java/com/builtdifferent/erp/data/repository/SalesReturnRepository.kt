package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.CreditNoteEntity
import com.builtdifferent.erp.data.local.entity.DocumentType
import com.builtdifferent.erp.data.local.entity.GstDirection
import com.builtdifferent.erp.data.local.entity.GstTransactionEntity
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import com.builtdifferent.erp.data.local.entity.JournalSourceType
import com.builtdifferent.erp.data.local.entity.SalesInvoiceItemEntity
import com.builtdifferent.erp.data.local.entity.SalesReturnEntity
import com.builtdifferent.erp.data.local.entity.SalesReturnItemEntity
import com.builtdifferent.erp.data.local.entity.StockMovementType
import com.builtdifferent.erp.data.local.entity.StockRefType

data class SalesReturnLineInput(val originalInvoiceItemId: Long, val quantity: Double)

private data class ComputedReturnLine(
    val originalItem: SalesInvoiceItemEntity,
    val quantity: Double,
    val taxableValuePaise: Long,
    val cgstAmountPaise: Long,
    val sgstAmountPaise: Long,
    val igstAmountPaise: Long,
    val cessAmountPaise: Long,
    val lineTotalPaise: Long
)

/**
 * BUSINESS RULE (documented per project convention — returns):
 *
 * A sales return NEVER re-looks-up the current GST rate. It always
 * proportions the tax from the ORIGINAL SalesInvoiceItemEntity's already-
 * applied rates and amounts by the fraction of quantity being returned.
 * This is the only way a return can be correct if the GST rate has since
 * changed — it must reverse exactly what was actually charged on the
 * original sale, not what today's rate would produce.
 *
 * Posting a return atomically:
 *  1. Validates the requested return quantity doesn't exceed (original qty
 *     - already returned qty) per line, across multiple partial returns.
 *  2. Inserts SalesReturnEntity + SalesReturnItemEntity (inventory/reason
 *     record) and a CreditNoteEntity (the fiscal document) for the amount.
 *  3. Records a SALE_RETURN stock movement per line (stock comes back in).
 *  4. Posts a reversing journal entry: Dr Sales, Dr CGST/SGST/IGST Output,
 *     Cr Party — the exact opposite of the original sale's entry, scaled
 *     to the returned portion only.
 *  5. Logs a negative OUTWARD GstTransactionEntity row per line so GST
 *     reports net the return against the original outward supply.
 */
class SalesReturnRepository(
    private val db: AppDatabase,
    private val inventoryRepository: InventoryRepository,
    private val accountingRepository: AccountingRepository
) {
    private val salesReturnDao = db.salesReturnDao()
    private val salesReturnItemDao = db.salesReturnItemDao()
    private val creditNoteDao = db.creditNoteDao()

    suspend fun postReturn(
        originalInvoiceId: Long,
        financialYearId: Long,
        returnDateMillis: Long,
        reason: String,
        lines: List<SalesReturnLineInput>
    ): Long {
        val invoice = db.salesInvoiceDao().getById(originalInvoiceId)
            ?: throw IllegalArgumentException("Original invoice not found")
        val party = db.partyDao().getById(invoice.partyId)
            ?: throw IllegalStateException("Party for invoice not found")
        val partyLedgerAccountId = party.ledgerAccountId
            ?: throw IllegalStateException("Party has no ledger account")

        if (lines.isEmpty()) throw IllegalArgumentException("A return must have at least one line")

        return db.withTransaction {
            val originalItems = db.salesInvoiceItemDao().getForInvoice(originalInvoiceId)
                .associateBy { it.salesInvoiceItemId }

            val computedLines = lines.map { line ->
                val originalItem = originalItems[line.originalInvoiceItemId]
                    ?: throw IllegalArgumentException("Original invoice item ${line.originalInvoiceItemId} not found")
                val alreadyReturned = salesReturnDao.getReturnedQuantity(line.originalInvoiceItemId)
                val available = originalItem.quantity - alreadyReturned
                if (line.quantity > available + 0.0001) {
                    throw IllegalArgumentException(
                        "Cannot return ${line.quantity} of ${originalItem.productNameSnapshot}; only $available remaining"
                    )
                }
                val fraction = if (originalItem.quantity == 0.0) 0.0 else line.quantity / originalItem.quantity
                ComputedReturnLine(
                    originalItem = originalItem,
                    quantity = line.quantity,
                    taxableValuePaise = (originalItem.taxableValuePaise * fraction).toLong(),
                    cgstAmountPaise = (originalItem.cgstAmountPaise * fraction).toLong(),
                    sgstAmountPaise = (originalItem.sgstAmountPaise * fraction).toLong(),
                    igstAmountPaise = (originalItem.igstAmountPaise * fraction).toLong(),
                    cessAmountPaise = (originalItem.cessAmountPaise * fraction).toLong(),
                    lineTotalPaise = (originalItem.lineTotalPaise * fraction).toLong()
                )
            }

            val subTotal = computedLines.sumOf { it.taxableValuePaise }
            val totalTax = computedLines.sumOf { it.cgstAmountPaise + it.sgstAmountPaise + it.igstAmountPaise + it.cessAmountPaise }
            val grandTotal = computedLines.sumOf { it.lineTotalPaise }

            val returnNumber = db.invoiceSequenceDao().nextNumber(DocumentType.SALES_RETURN, financialYearId, "SR-")

            val returnId = salesReturnDao.insert(
                SalesReturnEntity(
                    returnNumber = returnNumber,
                    returnDateMillis = returnDateMillis,
                    financialYearId = financialYearId,
                    originalSalesInvoiceId = originalInvoiceId,
                    reason = reason,
                    subTotalPaise = subTotal,
                    totalTaxPaise = totalTax,
                    grandTotalPaise = grandTotal
                )
            )

            salesReturnItemDao.insertAll(
                computedLines.map { c ->
                    SalesReturnItemEntity(
                        salesReturnId = returnId,
                        originalSalesInvoiceItemId = c.originalItem.salesInvoiceItemId,
                        productId = c.originalItem.productId,
                        quantity = c.quantity,
                        unitPricePaise = c.originalItem.unitPricePaise,
                        taxableValuePaise = c.taxableValuePaise,
                        cgstAmountPaise = c.cgstAmountPaise,
                        sgstAmountPaise = c.sgstAmountPaise,
                        igstAmountPaise = c.igstAmountPaise,
                        cessAmountPaise = c.cessAmountPaise,
                        lineTotalPaise = c.lineTotalPaise
                    )
                }
            )

            val creditNoteNumber = db.invoiceSequenceDao().nextNumber(DocumentType.CREDIT_NOTE, financialYearId, "CN-")
            creditNoteDao.insert(
                CreditNoteEntity(
                    creditNoteNumber = creditNoteNumber,
                    creditNoteDateMillis = returnDateMillis,
                    financialYearId = financialYearId,
                    partyId = invoice.partyId,
                    salesReturnId = returnId,
                    amountPaise = grandTotal,
                    reason = reason
                )
            )

            computedLines.forEach { c ->
                val unitCost = db.productDao().getById(c.originalItem.productId)?.purchasePricePaise ?: 0L
                inventoryRepository.recordMovement(
                    productId = c.originalItem.productId,
                    warehouseId = invoice.warehouseId,
                    type = StockMovementType.SALE_RETURN,
                    quantityDelta = c.quantity,
                    valueDeltaPaise = (unitCost.toDouble() * c.quantity).toLong(),
                    unitCostPaise = unitCost,
                    refType = StockRefType.SALES_RETURN,
                    refId = returnId,
                    financialYearId = financialYearId,
                    movementDateMillis = returnDateMillis
                )
            }

            val ledgerAccountDao = db.ledgerAccountDao()
            val salesAccountId = ledgerAccountDao.findByName("Sales Account")!!.ledgerAccountId
            val cgstAccountId = ledgerAccountDao.findByName("CGST Output")!!.ledgerAccountId
            val sgstAccountId = ledgerAccountDao.findByName("SGST Output")!!.ledgerAccountId
            val igstAccountId = ledgerAccountDao.findByName("IGST Output")!!.ledgerAccountId

            val totalCgst = computedLines.sumOf { it.cgstAmountPaise }
            val totalSgst = computedLines.sumOf { it.sgstAmountPaise }
            val totalIgst = computedLines.sumOf { it.igstAmountPaise }

            val journalLines = buildList {
                add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = salesAccountId, debitPaise = subTotal))
                if (totalCgst > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cgstAccountId, debitPaise = totalCgst))
                if (totalSgst > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = sgstAccountId, debitPaise = totalSgst))
                if (totalIgst > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = igstAccountId, debitPaise = totalIgst))
                add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = partyLedgerAccountId, creditPaise = grandTotal))
            }
            accountingRepository.postJournalEntry(
                financialYearId = financialYearId,
                entryDateMillis = returnDateMillis,
                narration = "Sales Return $returnNumber / Credit Note $creditNoteNumber for ${party.partyName}",
                sourceType = JournalSourceType.SALES_RETURN,
                sourceId = returnId,
                lines = journalLines
            )

            computedLines.forEach { c ->
                db.gstTransactionDao().insert(
                    GstTransactionEntity(
                        financialYearId = financialYearId,
                        direction = GstDirection.OUTWARD,
                        transactionDateMillis = returnDateMillis,
                        partyId = invoice.partyId,
                        partyGstin = party.gstin,
                        hsnSacCode = c.originalItem.hsnSacCodeSnapshot,
                        taxableValuePaise = -c.taxableValuePaise,
                        cgstPaise = -c.cgstAmountPaise,
                        sgstPaise = -c.sgstAmountPaise,
                        igstPaise = -c.igstAmountPaise,
                        cessPaise = -c.cessAmountPaise,
                        sourceType = JournalSourceType.SALES_RETURN,
                        sourceId = returnId
                    )
                )
            }

            returnId
        }
    }
}
