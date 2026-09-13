package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.CompanyEntity
import com.builtdifferent.erp.data.local.entity.GstDirection
import com.builtdifferent.erp.data.local.entity.GstTransactionEntity
import com.builtdifferent.erp.data.local.entity.InvoiceStatus
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import com.builtdifferent.erp.data.local.entity.JournalSourceType
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.PurchaseInvoiceEntity
import com.builtdifferent.erp.data.local.entity.PurchaseInvoiceItemEntity
import com.builtdifferent.erp.data.local.entity.StockMovementType
import com.builtdifferent.erp.data.local.entity.StockRefType
import com.builtdifferent.erp.domain.engine.TaxEngine
import com.builtdifferent.erp.domain.engine.TaxLineInput
import com.builtdifferent.erp.domain.model.CompanySnapshot
import com.builtdifferent.erp.domain.model.PartySnapshot
import kotlinx.coroutines.flow.Flow

data class PurchaseInvoiceLineDraft(
    val productId: Long,
    val productName: String,
    val hsnSacCode: String,
    val quantity: Double,
    val unitLabel: String,
    val unitPricePaise: Long,
    val discountPercent: Double,
    val cgstRatePercent: Double,
    val sgstRatePercent: Double,
    val igstRatePercent: Double,
    val cessRatePercent: Double
)

/**
 * Mirrors SalesInvoiceRepository for the purchase side: stock moves IN
 * (PURCHASE movement) instead of out, and the journal entry direction is
 * reversed —
 *   Dr Purchase Account        totalTaxable
 *   Dr CGST/SGST/IGST Input
 *     Cr Supplier              grandTotal
 * — because input GST is a recoverable asset, not a liability (see
 * AccountGroup.ASSET on the Input accounts in DatabaseSeeder). Logs one
 * INWARD GstTransactionEntity per line for GSTR-2-style reports.
 *
 * The supplier's own invoice number is stored as-is (never renumbered) —
 * see PurchaseInvoiceEntity's composite unique index with partyId, since
 * supplier numbering is not ours to control.
 */
class PurchaseInvoiceRepository(
    private val db: AppDatabase,
    private val inventoryRepository: InventoryRepository,
    private val accountingRepository: AccountingRepository
) {
    private val invoiceDao = db.purchaseInvoiceDao()
    private val itemDao = db.purchaseInvoiceItemDao()
    private val ledgerAccountDao = db.ledgerAccountDao()

    fun observeInvoices(financialYearId: Long?, status: InvoiceStatus?): Flow<List<PurchaseInvoiceEntity>> =
        invoiceDao.observeInvoices(financialYearId, status)

    suspend fun getInvoice(id: Long): PurchaseInvoiceEntity? = invoiceDao.getById(id)
    suspend fun getItems(invoiceId: Long): List<PurchaseInvoiceItemEntity> = itemDao.getForInvoice(invoiceId)

    suspend fun postInvoice(
        company: CompanyEntity,
        supplier: PartyEntity,
        warehouseId: Long,
        financialYearId: Long,
        supplierInvoiceNumber: String,
        invoiceDateMillis: Long,
        isInterState: Boolean,
        lines: List<PurchaseInvoiceLineDraft>
    ): Long {
        if (lines.isEmpty()) throw IllegalArgumentException("A purchase invoice must have at least one line item")
        if (supplierInvoiceNumber.isBlank()) throw IllegalArgumentException("Supplier's invoice number is required")

        val lineResults = lines.map { line ->
            line to TaxEngine.calculateLine(
                TaxLineInput(
                    quantity = line.quantity,
                    unitPricePaise = line.unitPricePaise,
                    discountPercent = line.discountPercent,
                    cgstRatePercent = line.cgstRatePercent,
                    sgstRatePercent = line.sgstRatePercent,
                    igstRatePercent = line.igstRatePercent,
                    cessRatePercent = line.cessRatePercent,
                    isInterState = isInterState
                )
            )
        }
        val totals = TaxEngine.calculateInvoiceTotals(lineResults.map { it.second })

        return db.withTransaction {
            val invoiceId = invoiceDao.insert(
                PurchaseInvoiceEntity(
                    invoiceNumber = supplierInvoiceNumber,
                    invoiceDateMillis = invoiceDateMillis,
                    financialYearId = financialYearId,
                    partyId = supplier.partyId,
                    warehouseId = warehouseId,
                    isInterState = isInterState,
                    companySnapshotJson = CompanySnapshot.toJson(company),
                    partySnapshotJson = PartySnapshot.toJson(supplier),
                    subTotalPaise = totals.subTotalPaise,
                    totalDiscountPaise = totals.totalDiscountPaise,
                    totalTaxableValuePaise = totals.totalTaxableValuePaise,
                    totalCgstPaise = totals.totalCgstPaise,
                    totalSgstPaise = totals.totalSgstPaise,
                    totalIgstPaise = totals.totalIgstPaise,
                    totalCessPaise = totals.totalCessPaise,
                    roundOffPaise = totals.roundOffPaise,
                    grandTotalPaise = totals.grandTotalPaise,
                    status = InvoiceStatus.POSTED
                )
            )

            val itemEntities = lineResults.mapIndexed { index, (line, result) ->
                PurchaseInvoiceItemEntity(
                    purchaseInvoiceId = invoiceId,
                    productId = line.productId,
                    productNameSnapshot = line.productName,
                    hsnSacCodeSnapshot = line.hsnSacCode,
                    quantity = line.quantity,
                    unitLabelSnapshot = line.unitLabel,
                    unitPricePaise = line.unitPricePaise,
                    discountPercent = line.discountPercent,
                    discountAmountPaise = result.discountAmountPaise,
                    taxableValuePaise = result.taxableValuePaise,
                    appliedCgstRatePercent = line.cgstRatePercent,
                    appliedSgstRatePercent = line.sgstRatePercent,
                    appliedIgstRatePercent = line.igstRatePercent,
                    appliedCessRatePercent = line.cessRatePercent,
                    cgstAmountPaise = result.cgstAmountPaise,
                    sgstAmountPaise = result.sgstAmountPaise,
                    igstAmountPaise = result.igstAmountPaise,
                    cessAmountPaise = result.cessAmountPaise,
                    lineTotalPaise = result.lineTotalPaise,
                    sortOrder = index
                )
            }
            itemDao.insertAll(itemEntities)

            lineResults.forEach { (line, result) ->
                inventoryRepository.recordMovement(
                    productId = line.productId,
                    warehouseId = warehouseId,
                    type = StockMovementType.PURCHASE,
                    quantityDelta = line.quantity,
                    valueDeltaPaise = result.taxableValuePaise,
                    unitCostPaise = line.unitPricePaise,
                    refType = StockRefType.PURCHASE_INVOICE,
                    refId = invoiceId,
                    financialYearId = financialYearId,
                    movementDateMillis = invoiceDateMillis
                )
                db.productDao().updatePurchasePrice(line.productId, line.unitPricePaise, invoiceDateMillis)
            }

            val purchaseAccountId = ledgerAccountDao.findByName("Purchase Account")!!.ledgerAccountId
            val cgstInputAccountId = ledgerAccountDao.findByName("CGST Input")!!.ledgerAccountId
            val sgstInputAccountId = ledgerAccountDao.findByName("SGST Input")!!.ledgerAccountId
            val igstInputAccountId = ledgerAccountDao.findByName("IGST Input")!!.ledgerAccountId
            val roundOffAccountId = ledgerAccountDao.findByName("Round Off")!!.ledgerAccountId
            val supplierLedgerAccountId = supplier.ledgerAccountId
                ?: throw IllegalStateException("Supplier ${supplier.partyId} has no ledger account")

            val journalLines = buildList {
                add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = purchaseAccountId, debitPaise = totals.totalTaxableValuePaise))
                if (totals.totalCgstPaise > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cgstInputAccountId, debitPaise = totals.totalCgstPaise))
                if (totals.totalSgstPaise > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = sgstInputAccountId, debitPaise = totals.totalSgstPaise))
                if (totals.totalIgstPaise > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = igstInputAccountId, debitPaise = totals.totalIgstPaise))
                if (totals.roundOffPaise > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = roundOffAccountId, debitPaise = totals.roundOffPaise))
                if (totals.roundOffPaise < 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = roundOffAccountId, creditPaise = -totals.roundOffPaise))
                add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = supplierLedgerAccountId, creditPaise = totals.grandTotalPaise))
            }
            accountingRepository.postJournalEntry(
                financialYearId = financialYearId,
                entryDateMillis = invoiceDateMillis,
                narration = "Purchase Invoice $supplierInvoiceNumber from ${supplier.partyName}",
                sourceType = JournalSourceType.PURCHASE_INVOICE,
                sourceId = invoiceId,
                lines = journalLines
            )

            lineResults.forEach { (line, result) ->
                db.gstTransactionDao().insert(
                    GstTransactionEntity(
                        financialYearId = financialYearId,
                        direction = GstDirection.INWARD,
                        transactionDateMillis = invoiceDateMillis,
                        partyId = supplier.partyId,
                        partyGstin = supplier.gstin,
                        hsnSacCode = line.hsnSacCode,
                        taxableValuePaise = result.taxableValuePaise,
                        cgstPaise = result.cgstAmountPaise,
                        sgstPaise = result.sgstAmountPaise,
                        igstPaise = result.igstAmountPaise,
                        cessPaise = result.cessAmountPaise,
                        sourceType = JournalSourceType.PURCHASE_INVOICE,
                        sourceId = invoiceId
                    )
                )
            }

            invoiceId
        }
    }
}
