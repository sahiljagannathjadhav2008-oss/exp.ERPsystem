package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.DebitNoteEntity
import com.builtdifferent.erp.data.local.entity.DocumentType
import com.builtdifferent.erp.data.local.entity.GstDirection
import com.builtdifferent.erp.data.local.entity.GstTransactionEntity
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import com.builtdifferent.erp.data.local.entity.JournalSourceType
import com.builtdifferent.erp.data.local.entity.PurchaseInvoiceItemEntity
import com.builtdifferent.erp.data.local.entity.PurchaseReturnEntity
import com.builtdifferent.erp.data.local.entity.PurchaseReturnItemEntity
import com.builtdifferent.erp.data.local.entity.StockMovementType
import com.builtdifferent.erp.data.local.entity.StockRefType

data class PurchaseReturnLineInput(val originalInvoiceItemId: Long, val quantity: Double)

private data class ComputedPurchaseReturnLine(
    val originalItem: PurchaseInvoiceItemEntity,
    val quantity: Double,
    val taxableValuePaise: Long,
    val cgstAmountPaise: Long,
    val sgstAmountPaise: Long,
    val igstAmountPaise: Long,
    val cessAmountPaise: Long,
    val lineTotalPaise: Long
)

/**
 * Mirrors SalesReturnRepository for the purchase side: proportions tax from
 * the ORIGINAL PurchaseInvoiceItemEntity's applied rates (never re-looked
 * up), moves stock OUT (PURCHASE_RETURN), and posts the reversing entry —
 *   Dr Supplier                  grandTotal
 *     Cr Purchase Account        taxable
 *     Cr CGST/SGST/IGST Input
 * — exactly opposite of the original purchase posting, scaled to the
 * returned portion. Logs a negative INWARD GstTransactionEntity row per
 * line so GST reports net the return against the original inward supply.
 */
class PurchaseReturnRepository(
    private val db: AppDatabase,
    private val inventoryRepository: InventoryRepository,
    private val accountingRepository: AccountingRepository
) {
    private val purchaseReturnDao = db.purchaseReturnDao()
    private val purchaseReturnItemDao = db.purchaseReturnItemDao()
    private val debitNoteDao = db.debitNoteDao()

    suspend fun postReturn(
        originalInvoiceId: Long,
        financialYearId: Long,
        returnDateMillis: Long,
        reason: String,
        lines: List<PurchaseReturnLineInput>
    ): Long {
        val invoice = db.purchaseInvoiceDao().getById(originalInvoiceId)
            ?: throw IllegalArgumentException("Original purchase invoice not found")
        val supplier = db.partyDao().getById(invoice.partyId)
            ?: throw IllegalStateException("Supplier for invoice not found")
        val supplierLedgerAccountId = supplier.ledgerAccountId
            ?: throw IllegalStateException("Supplier has no ledger account")

        if (lines.isEmpty()) throw IllegalArgumentException("A return must have at least one line")

        return db.withTransaction {
            val originalItems = db.purchaseInvoiceItemDao().getForInvoice(originalInvoiceId)
                .associateBy { it.purchaseInvoiceItemId }

            val computedLines = lines.map { line ->
                val originalItem = originalItems[line.originalInvoiceItemId]
                    ?: throw IllegalArgumentException("Original purchase item ${line.originalInvoiceItemId} not found")
                val alreadyReturned = purchaseReturnDao.getReturnedQuantity(line.originalInvoiceItemId)
                val available = originalItem.quantity - alreadyReturned
                if (line.quantity > available + 0.0001) {
                    throw IllegalArgumentException(
                        "Cannot return ${line.quantity} of ${originalItem.productNameSnapshot}; only $available remaining"
                    )
                }
                val fraction = if (originalItem.quantity == 0.0) 0.0 else line.quantity / originalItem.quantity
                ComputedPurchaseReturnLine(
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

            val returnNumber = db.invoiceSequenceDao().nextNumber(DocumentType.PURCHASE_RETURN, financialYearId, "PR-")

            val returnId = purchaseReturnDao.insert(
                PurchaseReturnEntity(
                    returnNumber = returnNumber,
                    returnDateMillis = returnDateMillis,
                    financialYearId = financialYearId,
                    originalPurchaseInvoiceId = originalInvoiceId,
                    reason = reason,
                    subTotalPaise = subTotal,
                    totalTaxPaise = totalTax,
                    grandTotalPaise = grandTotal
                )
            )

            purchaseReturnItemDao.insertAll(
                computedLines.map { c ->
                    PurchaseReturnItemEntity(
                        purchaseReturnId = returnId,
                        originalPurchaseInvoiceItemId = c.originalItem.purchaseInvoiceItemId,
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

            val debitNoteNumber = db.invoiceSequenceDao().nextNumber(DocumentType.DEBIT_NOTE, financialYearId, "DN-")
            debitNoteDao.insert(
                DebitNoteEntity(
                    debitNoteNumber = debitNoteNumber,
                    debitNoteDateMillis = returnDateMillis,
                    financialYearId = financialYearId,
                    partyId = invoice.partyId,
                    purchaseReturnId = returnId,
                    amountPaise = grandTotal,
                    reason = reason
                )
            )

            computedLines.forEach { c ->
                inventoryRepository.recordMovement(
                    productId = c.originalItem.productId,
                    warehouseId = invoice.warehouseId,
                    type = StockMovementType.PURCHASE_RETURN,
                    quantityDelta = -c.quantity,
                    valueDeltaPaise = -c.taxableValuePaise,
                    unitCostPaise = c.originalItem.unitPricePaise,
                    refType = StockRefType.PURCHASE_RETURN,
                    refId = returnId,
                    financialYearId = financialYearId,
                    movementDateMillis = returnDateMillis
                )
            }

            val ledgerAccountDao = db.ledgerAccountDao()
            val purchaseAccountId = ledgerAccountDao.findByName("Purchase Account")!!.ledgerAccountId
            val cgstInputAccountId = ledgerAccountDao.findByName("CGST Input")!!.ledgerAccountId
            val sgstInputAccountId = ledgerAccountDao.findByName("SGST Input")!!.ledgerAccountId
            val igstInputAccountId = ledgerAccountDao.findByName("IGST Input")!!.ledgerAccountId

            val totalCgst = computedLines.sumOf { it.cgstAmountPaise }
            val totalSgst = computedLines.sumOf { it.sgstAmountPaise }
            val totalIgst = computedLines.sumOf { it.igstAmountPaise }

            val journalLines = buildList {
                add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = supplierLedgerAccountId, debitPaise = grandTotal))
                add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = purchaseAccountId, creditPaise = subTotal))
                if (totalCgst > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cgstInputAccountId, creditPaise = totalCgst))
                if (totalSgst > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = sgstInputAccountId, creditPaise = totalSgst))
                if (totalIgst > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = igstInputAccountId, creditPaise = totalIgst))
            }
            accountingRepository.postJournalEntry(
                financialYearId = financialYearId,
                entryDateMillis = returnDateMillis,
                narration = "Purchase Return $returnNumber / Debit Note $debitNoteNumber to ${supplier.partyName}",
                sourceType = JournalSourceType.PURCHASE_RETURN,
                sourceId = returnId,
                lines = journalLines
            )

            computedLines.forEach { c ->
                db.gstTransactionDao().insert(
                    GstTransactionEntity(
                        financialYearId = financialYearId,
                        direction = GstDirection.INWARD,
                        transactionDateMillis = returnDateMillis,
                        partyId = invoice.partyId,
                        partyGstin = supplier.gstin,
                        hsnSacCode = c.originalItem.hsnSacCodeSnapshot,
                        taxableValuePaise = -c.taxableValuePaise,
                        cgstPaise = -c.cgstAmountPaise,
                        sgstPaise = -c.sgstAmountPaise,
                        igstPaise = -c.igstAmountPaise,
                        cessPaise = -c.cessAmountPaise,
                        sourceType = JournalSourceType.PURCHASE_RETURN,
                        sourceId = returnId
                    )
                )
            }

            returnId
        }
    }
}
