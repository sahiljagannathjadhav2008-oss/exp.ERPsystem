package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.DeliveryChallanEntity
import com.builtdifferent.erp.data.local.entity.DeliveryChallanItemEntity
import com.builtdifferent.erp.data.local.entity.DocumentType
import com.builtdifferent.erp.data.local.entity.SalesDocStatus
import com.builtdifferent.erp.data.local.entity.StockMovementType
import com.builtdifferent.erp.data.local.entity.StockRefType
import kotlinx.coroutines.flow.Flow

data class ChallanLineDraft(val productId: Long, val productName: String, val quantity: Double)

/**
 * BUSINESS RULE (documented per project convention — delivery challans):
 *
 * A delivery challan moves goods out the door WITHOUT recognising a sale —
 * common when goods ship ahead of billing, or for job-work/returnable
 * gate-pass movements. It therefore:
 *  - DOES record a stock movement (goods are physically gone — the
 *    inventory count must reflect reality), tagged DELIVERY_OUT so stock
 *    reports can distinguish it from an actual invoiced sale
 *  - Does NOT post any journal entry (no revenue has been earned or
 *    invoiced yet — posting one here would recognise income that doesn't
 *    exist)
 *
 * When the challan is later invoiced, markInvoiced links it to the
 * resulting invoice for traceability. Reconciling "don't double-decrement
 * stock when a challan-covered sale is finally invoiced" is a refinement
 * that belongs to whichever screen wires the challan-to-invoice flow
 * (not yet built) — this repository only guarantees the challan's own
 * movement is correct and traceable back to it.
 */
class DeliveryChallanRepository(private val db: AppDatabase, private val inventoryRepository: InventoryRepository) {
    private val challanDao = db.deliveryChallanDao()
    private val itemDao = db.deliveryChallanItemDao()

    fun observeAll(financialYearId: Long?): Flow<List<DeliveryChallanEntity>> = challanDao.observeAll(financialYearId)
    suspend fun getById(id: Long) = challanDao.getById(id)
    suspend fun getItems(id: Long) = itemDao.getForChallan(id)

    suspend fun create(
        financialYearId: Long,
        partyId: Long,
        warehouseId: Long,
        challanDateMillis: Long,
        sourceSalesOrderId: Long?,
        notes: String,
        lines: List<ChallanLineDraft>
    ): Long {
        if (lines.isEmpty()) throw IllegalArgumentException("A delivery challan must have at least one line")

        return db.withTransaction {
            val challanNumber = db.invoiceSequenceDao().nextNumber(DocumentType.DELIVERY_CHALLAN, financialYearId, "DC-")
            val challanId = challanDao.insert(
                DeliveryChallanEntity(
                    challanNumber = challanNumber,
                    challanDateMillis = challanDateMillis,
                    financialYearId = financialYearId,
                    partyId = partyId,
                    warehouseId = warehouseId,
                    sourceSalesOrderId = sourceSalesOrderId,
                    notes = notes
                )
            )
            itemDao.insertAll(
                lines.mapIndexed { index, line ->
                    DeliveryChallanItemEntity(
                        deliveryChallanId = challanId,
                        productId = line.productId,
                        productNameSnapshot = line.productName,
                        quantity = line.quantity,
                        sortOrder = index
                    )
                }
            )
            lines.forEach { line ->
                val unitCost = db.productDao().getById(line.productId)?.purchasePricePaise ?: 0L
                inventoryRepository.recordMovement(
                    productId = line.productId,
                    warehouseId = warehouseId,
                    type = StockMovementType.DELIVERY_OUT,
                    quantityDelta = -line.quantity,
                    valueDeltaPaise = -(unitCost.toDouble() * line.quantity).toLong(),
                    unitCostPaise = unitCost,
                    refType = StockRefType.DELIVERY_CHALLAN,
                    refId = challanId,
                    financialYearId = financialYearId,
                    movementDateMillis = challanDateMillis,
                    note = "Delivery Challan $challanNumber"
                )
            }
            challanId
        }
    }

    suspend fun markInvoiced(challanId: Long, invoiceId: Long) {
        challanDao.markInvoiced(challanId, SalesDocStatus.CONVERTED, invoiceId)
    }
}
