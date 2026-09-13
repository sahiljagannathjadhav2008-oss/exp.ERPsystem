package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.DocumentType
import com.builtdifferent.erp.data.local.entity.PurchaseOrderEntity
import com.builtdifferent.erp.data.local.entity.PurchaseOrderItemEntity
import com.builtdifferent.erp.data.local.entity.SalesDocStatus
import kotlinx.coroutines.flow.Flow

/** Purchase orders are, like sales quotations/orders, not accounting or
 * stock documents — they're a commitment to a supplier, fulfilled later by
 * a real PurchaseInvoiceEntity that does the actual posting. */
class PurchaseOrderRepository(private val db: AppDatabase) {
    private val orderDao = db.purchaseOrderDao()
    private val itemDao = db.purchaseOrderItemDao()

    fun observeAll(financialYearId: Long?): Flow<List<PurchaseOrderEntity>> = orderDao.observeAll(financialYearId)
    suspend fun getById(id: Long) = orderDao.getById(id)
    suspend fun getItems(id: Long) = itemDao.getForOrder(id)

    suspend fun create(
        financialYearId: Long,
        partyId: Long,
        orderDateMillis: Long,
        notes: String,
        lines: List<PreDocLineDraft>
    ): Long {
        if (lines.isEmpty()) throw IllegalArgumentException("A purchase order must have at least one line")
        val subTotal = lines.sumOf { (it.unitPricePaise * it.quantity).toLong() }
        val grandTotal = subTotal + lines.sumOf { it.estimatedTaxPaise }

        return db.withTransaction {
            val orderNumber = db.invoiceSequenceDao().nextNumber(DocumentType.PURCHASE_ORDER, financialYearId, "PO-")
            val orderId = orderDao.insert(
                PurchaseOrderEntity(
                    orderNumber = orderNumber,
                    orderDateMillis = orderDateMillis,
                    financialYearId = financialYearId,
                    partyId = partyId,
                    subTotalPaise = subTotal,
                    grandTotalPaise = grandTotal,
                    notes = notes
                )
            )
            itemDao.insertAll(
                lines.mapIndexed { index, line ->
                    PurchaseOrderItemEntity(
                        purchaseOrderId = orderId,
                        productId = line.productId,
                        productNameSnapshot = line.productName,
                        quantity = line.quantity,
                        unitPricePaise = line.unitPricePaise,
                        taxableValuePaise = (line.unitPricePaise * line.quantity).toLong(),
                        estimatedTaxPaise = line.estimatedTaxPaise,
                        lineTotalPaise = (line.unitPricePaise * line.quantity).toLong() + line.estimatedTaxPaise,
                        sortOrder = index
                    )
                }
            )
            orderId
        }
    }

    suspend fun updateStatus(id: Long, status: SalesDocStatus) = orderDao.updateStatus(id, status)
}
