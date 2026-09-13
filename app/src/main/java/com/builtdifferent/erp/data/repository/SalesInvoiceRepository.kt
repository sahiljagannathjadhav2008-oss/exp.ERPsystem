package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.CompanyEntity
import com.builtdifferent.erp.data.local.entity.DocumentType
import com.builtdifferent.erp.data.local.entity.GstDirection
import com.builtdifferent.erp.data.local.entity.GstInvoiceCategory
import com.builtdifferent.erp.data.local.entity.GstTransactionEntity
import com.builtdifferent.erp.data.local.entity.InvoiceStatus
import com.builtdifferent.erp.data.local.entity.JournalEntryLineEntity
import com.builtdifferent.erp.data.local.entity.JournalSourceType
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.SalesInvoiceEntity
import com.builtdifferent.erp.data.local.entity.SalesInvoiceItemEntity
import com.builtdifferent.erp.data.local.entity.StockMovementType
import com.builtdifferent.erp.data.local.entity.StockRefType
import com.builtdifferent.erp.domain.engine.TaxEngine
import com.builtdifferent.erp.domain.engine.TaxLineInput
import com.builtdifferent.erp.domain.model.CompanySnapshot
import com.builtdifferent.erp.domain.model.PartySnapshot
import com.builtdifferent.erp.util.AmountInWords
import com.builtdifferent.erp.util.Money
import kotlinx.coroutines.flow.Flow

data class SalesInvoiceLineDraft(
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
 * Owns the entire lifecycle of a sales invoice. Posting an invoice
 * atomically:
 *  1. Reserves the next invoice number (InvoiceSequenceDao)
 *  2. Computes every line's tax via TaxEngine and totals
 *  3. Inserts the invoice + item rows, with company/party snapshots
 *  4. Records a SALE stock movement per line (InventoryRepository)
 *  5. Posts a balanced journal entry (AccountingRepository):
 *       Dr Party                     grandTotal
 *         Cr Sales Account           totalTaxable
 *         Cr CGST/SGST/IGST Output
 *         Cr/Dr Round Off
 *  6. Writes one GstTransactionEntity row per line (OUTWARD) for GST
 *     reports
 *
 * All in ONE Room transaction so a crash mid-posting can never leave stock
 * decremented without the matching accounting entry, or vice versa.
 */
class SalesInvoiceRepository(
    private val db: AppDatabase,
    private val inventoryRepository: InventoryRepository,
    private val accountingRepository: AccountingRepository
) {
    private val invoiceDao = db.salesInvoiceDao()
    private val itemDao = db.salesInvoiceItemDao()
    private val ledgerAccountDao = db.ledgerAccountDao()

    fun observeInvoices(financialYearId: Long?, status: InvoiceStatus?): Flow<List<SalesInvoiceEntity>> =
        invoiceDao.observeInvoices(financialYearId, status)

    suspend fun getInvoice(id: Long): SalesInvoiceEntity? = invoiceDao.getById(id)
    suspend fun getItems(invoiceId: Long): List<SalesInvoiceItemEntity> = itemDao.getForInvoice(invoiceId)

    suspend fun postInvoice(
        company: CompanyEntity,
        party: PartyEntity,
        warehouseId: Long,
        financialYearId: Long,
        invoiceDateMillis: Long,
        isInterState: Boolean,
        placeOfSupplyStateCode: String,
        lines: List<SalesInvoiceLineDraft>
    ): Long {
        if (lines.isEmpty()) throw IllegalArgumentException("An invoice must have at least one line item")

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
            val invoiceNumber = db.invoiceSequenceDao().nextNumber(
                DocumentType.SALES_INVOICE, financialYearId, company.invoicePrefix
            )
            val gstCategory = if (party.isUnregistered) GstInvoiceCategory.B2C else GstInvoiceCategory.B2B

            val invoiceId = invoiceDao.insert(
                SalesInvoiceEntity(
                    invoiceNumber = invoiceNumber,
                    invoiceDateMillis = invoiceDateMillis,
                    financialYearId = financialYearId,
                    partyId = party.partyId,
                    warehouseId = warehouseId,
                    gstCategory = gstCategory,
                    isInterState = isInterState,
                    placeOfSupplyStateCode = placeOfSupplyStateCode,
                    companySnapshotJson = CompanySnapshot.toJson(company),
                    partySnapshotJson = PartySnapshot.toJson(party),
                    subTotalPaise = totals.subTotalPaise,
                    totalDiscountPaise = totals.totalDiscountPaise,
                    totalTaxableValuePaise = totals.totalTaxableValuePaise,
                    totalCgstPaise = totals.totalCgstPaise,
                    totalSgstPaise = totals.totalSgstPaise,
                    totalIgstPaise = totals.totalIgstPaise,
                    totalCessPaise = totals.totalCessPaise,
                    roundOffPaise = totals.roundOffPaise,
                    grandTotalPaise = totals.grandTotalPaise,
                    amountInWords = AmountInWords.convert(Money(totals.grandTotalPaise)),
                    status = InvoiceStatus.POSTED
                )
            )

            val itemEntities = lineResults.mapIndexed { index, (line, result) ->
                SalesInvoiceItemEntity(
                    salesInvoiceId = invoiceId,
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
                val unitCost = db.productDao().getById(line.productId)?.purchasePricePaise ?: 0L
                inventoryRepository.recordMovement(
                    productId = line.productId,
                    warehouseId = warehouseId,
                    type = StockMovementType.SALE,
                    quantityDelta = -line.quantity,
                    valueDeltaPaise = -(unitCost.toDouble() * line.quantity).toLong(),
                    unitCostPaise = unitCost,
                    refType = StockRefType.SALES_INVOICE,
                    refId = invoiceId,
                    financialYearId = financialYearId,
                    movementDateMillis = invoiceDateMillis
                )
            }

            val salesAccountId = ledgerAccountDao.findByName("Sales Account")!!.ledgerAccountId
            val cgstAccountId = ledgerAccountDao.findByName("CGST Output")!!.ledgerAccountId
            val sgstAccountId = ledgerAccountDao.findByName("SGST Output")!!.ledgerAccountId
            val igstAccountId = ledgerAccountDao.findByName("IGST Output")!!.ledgerAccountId
            val roundOffAccountId = ledgerAccountDao.findByName("Round Off")!!.ledgerAccountId
            val partyLedgerAccountId = party.ledgerAccountId
                ?: throw IllegalStateException("Party ${party.partyId} has no ledger account")

            val journalLines = buildList {
                add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = partyLedgerAccountId, debitPaise = totals.grandTotalPaise))
                add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = salesAccountId, creditPaise = totals.totalTaxableValuePaise))
                if (totals.totalCgstPaise > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = cgstAccountId, creditPaise = totals.totalCgstPaise))
                if (totals.totalSgstPaise > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = sgstAccountId, creditPaise = totals.totalSgstPaise))
                if (totals.totalIgstPaise > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = igstAccountId, creditPaise = totals.totalIgstPaise))
                if (totals.roundOffPaise > 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = roundOffAccountId, creditPaise = totals.roundOffPaise))
                if (totals.roundOffPaise < 0) add(JournalEntryLineEntity(journalEntryId = 0, ledgerAccountId = roundOffAccountId, debitPaise = -totals.roundOffPaise))
            }
            accountingRepository.postJournalEntry(
                financialYearId = financialYearId,
                entryDateMillis = invoiceDateMillis,
                narration = "Sales Invoice $invoiceNumber to ${party.partyName}",
                sourceType = JournalSourceType.SALES_INVOICE,
                sourceId = invoiceId,
                lines = journalLines
            )

            lineResults.forEach { (line, result) ->
                db.gstTransactionDao().insert(
                    GstTransactionEntity(
                        financialYearId = financialYearId,
                        direction = GstDirection.OUTWARD,
                        transactionDateMillis = invoiceDateMillis,
                        partyId = party.partyId,
                        partyGstin = party.gstin,
                        hsnSacCode = line.hsnSacCode,
                        taxableValuePaise = result.taxableValuePaise,
                        cgstPaise = result.cgstAmountPaise,
                        sgstPaise = result.sgstAmountPaise,
                        igstPaise = result.igstAmountPaise,
                        cessPaise = result.cessAmountPaise,
                        sourceType = JournalSourceType.SALES_INVOICE,
                        sourceId = invoiceId
                    )
                )
            }

            invoiceId
        }
    }
}
