package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.DocumentType
import com.builtdifferent.erp.data.local.entity.SalesDocStatus
import com.builtdifferent.erp.data.local.entity.SalesOrderEntity
import com.builtdifferent.erp.data.local.entity.SalesPreDocItemEntity
import com.builtdifferent.erp.data.local.entity.SalesQuotationEntity
import kotlinx.coroutines.flow.Flow

data class PreDocLineDraft(
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val discountPercent: Double,
    val estimatedTaxPaise: Long
)

/**
 * Quotations and orders are NOT accounting documents — no journal entry,
 * no stock movement — they're commitments/estimates. Their totals include
 * an estimatedTaxPaise per line precisely because it is an estimate: the
 * real applied rate and amount are only ever fixed when the eventual sales
 * invoice is posted (see SalesInvoiceRepository), which is the only place
 * a GST snapshot is taken.
 */
class SalesQuotationRepository(private val db: AppDatabase) {
    private val quotationDao = db.salesQuotationDao()
    private val itemDao = db.salesPreDocItemDao()

    fun observeAll(financialYearId: Long?): Flow<List<SalesQuotationEntity>> = quotationDao.observeAll(financialYearId)
    suspend fun getById(id: Long) = quotationDao.getById(id)
    suspend fun getItems(id: Long) = itemDao.getForQuotation(id)

    suspend fun create(
        financialYearId: Long,
        partyId: Long,
        quotationDateMillis: Long,
        validUntilMillis: Long?,
        notes: String,
        lines: List<PreDocLineDraft>
    ): Long {
        if (lines.isEmpty()) throw IllegalArgumentException("A quotation must have at least one line")
        val subTotal = lines.sumOf { (it.unitPricePaise * it.quantity).toLong() }
        val grandTotal = subTotal + lines.sumOf { it.estimatedTaxPaise }

        return db.withTransaction {
            val quotationNumber = db.invoiceSequenceDao().nextNumber(DocumentType.SALES_QUOTATION, financialYearId, "QTN-")
            val quotationId = quotationDao.insert(
                SalesQuotationEntity(
                    quotationNumber = quotationNumber,
                    quotationDateMillis = quotationDateMillis,
                    validUntilMillis = validUntilMillis,
                    financialYearId = financialYearId,
                    partyId = partyId,
                    subTotalPaise = subTotal,
                    grandTotalPaise = grandTotal,
                    notes = notes
                )
            )
            itemDao.insertAll(
                lines.mapIndexed { index, line ->
                    SalesPreDocItemEntity(
                        salesQuotationId = quotationId,
                        productId = line.productId,
                        productNameSnapshot = line.productName,
                        quantity = line.quantity,
                        unitPricePaise = line.unitPricePaise,
                        discountPercent = line.discountPercent,
                        taxableValuePaise = (line.unitPricePaise * line.quantity).toLong(),
                        estimatedTaxPaise = line.estimatedTaxPaise,
                        lineTotalPaise = (line.unitPricePaise * line.quantity).toLong() + line.estimatedTaxPaise,
                        sortOrder = index
                    )
                }
            )
            quotationId
        }
    }

    /** Converts a quotation into a sales order, carrying the same lines
     * forward and marking the quotation CONVERTED. */
    suspend fun convertToOrder(quotationId: Long): Long = db.withTransaction {
        val quotation = quotationDao.getById(quotationId) ?: throw IllegalArgumentException("Quotation not found")
        val items = itemDao.getForQuotation(quotationId)

        val orderNumber = db.invoiceSequenceDao().nextNumber(DocumentType.SALES_ORDER, quotation.financialYearId, "SO-")
        val orderId = db.salesOrderDao().insert(
            SalesOrderEntity(
                orderNumber = orderNumber,
                orderDateMillis = quotation.quotationDateMillis,
                financialYearId = quotation.financialYearId,
                partyId = quotation.partyId,
                sourceQuotationId = quotationId,
                subTotalPaise = quotation.subTotalPaise,
                grandTotalPaise = quotation.grandTotalPaise,
                notes = quotation.notes
            )
        )
        itemDao.insertAll(
            items.map { it.copy(salesPreDocItemId = 0, salesQuotationId = null, salesOrderId = orderId) }
        )
        quotationDao.markConverted(quotationId, SalesDocStatus.CONVERTED, orderId)
        orderId
    }
}

class SalesOrderRepository(private val db: AppDatabase) {
    private val orderDao = db.salesOrderDao()
    private val itemDao = db.salesPreDocItemDao()

    fun observeAll(financialYearId: Long?): Flow<List<SalesOrderEntity>> = orderDao.observeAll(financialYearId)
    suspend fun getById(id: Long) = orderDao.getById(id)
    suspend fun getItems(id: Long) = itemDao.getForOrder(id)

    suspend fun updateStatus(id: Long, status: SalesDocStatus) = orderDao.updateStatus(id, status)
}
