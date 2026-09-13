package com.builtdifferent.erp.data.repository

import androidx.room.withTransaction
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.PriceType
import com.builtdifferent.erp.data.local.entity.ProductEntity
import com.builtdifferent.erp.data.local.entity.ProductPriceEntity
import com.builtdifferent.erp.data.local.entity.StockMovementEntity
import com.builtdifferent.erp.data.local.entity.StockMovementType
import com.builtdifferent.erp.data.local.entity.StockRefType
import com.builtdifferent.erp.data.local.entity.WarehouseEntity
import com.builtdifferent.erp.util.DateUtils
import kotlinx.coroutines.flow.Flow

class WarehouseRepository(private val db: AppDatabase) {
    private val warehouseDao = db.warehouseDao()

    fun observeAll() = warehouseDao.observeAll()

    /** Returns the default warehouse, creating a "Main Warehouse" the very
     * first time any product with opening stock is created, so the app
     * never requires the user to set up a warehouse before using it. */
    suspend fun getOrCreateDefaultWarehouseId(): Long {
        warehouseDao.getDefault()?.let { return it.warehouseId }
        return warehouseDao.insert(WarehouseEntity(name = "Main Warehouse", isDefault = true))
    }
}

/**
 * Creating a product atomically creates its initial price-history rows
 * (ProductPriceEntity) and, when trackInventory is on and an opening
 * quantity was given, the opening StockEntity balance plus the
 * corresponding OPENING StockMovementEntity row — so a brand-new product
 * with opening stock behaves identically, from the very first read, to one
 * whose stock arrived via a later purchase invoice (Section 6's "no
 * shortcuts that produce inconsistent data" requirement).
 */
class ProductRepository(
    private val db: AppDatabase,
    private val warehouseRepository: WarehouseRepository,
    private val currentFinancialYearId: suspend () -> Long?
) {
    private val productDao = db.productDao()

    fun search(query: String): Flow<List<ProductEntity>> = productDao.search(query)
    fun observeActiveProductCount(): Flow<Int> = productDao.observeActiveProductCount()
    suspend fun getById(id: Long): ProductEntity? = productDao.getById(id)

    suspend fun createProduct(product: ProductEntity): Long = db.withTransaction {
        val productId = productDao.insert(product)

        db.productPriceDao().insert(
            ProductPriceEntity(productId = productId, type = PriceType.SELLING, pricePaise = product.sellingPricePaise)
        )
        db.productPriceDao().insert(
            ProductPriceEntity(productId = productId, type = PriceType.PURCHASE, pricePaise = product.purchasePricePaise)
        )

        if (product.trackInventory && product.openingStockQty != 0.0) {
            val warehouseId = warehouseRepository.getOrCreateDefaultWarehouseId()
            val fyId = currentFinancialYearId() ?: 0L
            db.stockDao().upsertAddQuantity(
                productId = productId,
                warehouseId = warehouseId,
                quantityDelta = product.openingStockQty,
                valueDeltaPaise = product.openingStockValuePaise,
                updatedAt = DateUtils.nowMillis()
            )
            db.stockMovementDao().insert(
                StockMovementEntity(
                    productId = productId,
                    warehouseId = warehouseId,
                    type = StockMovementType.OPENING,
                    quantity = product.openingStockQty,
                    unitCostPaise = if (product.openingStockQty != 0.0)
                        (product.openingStockValuePaise / product.openingStockQty).toLong() else 0L,
                    refType = StockRefType.PRODUCT_OPENING,
                    refId = productId,
                    financialYearId = fyId,
                    movementDateMillis = DateUtils.nowMillis()
                )
            )
        }
        productId
    }

    suspend fun updateProduct(product: ProductEntity, priceChanged: Boolean) {
        productDao.update(product.copy(updatedAt = DateUtils.nowMillis()))
        if (priceChanged) {
            db.productPriceDao().insert(
                ProductPriceEntity(productId = product.productId, type = PriceType.SELLING, pricePaise = product.sellingPricePaise)
            )
            db.productPriceDao().insert(
                ProductPriceEntity(productId = product.productId, type = PriceType.PURCHASE, pricePaise = product.purchasePricePaise)
            )
        }
    }

    suspend fun deactivateProduct(productId: Long) = productDao.softDelete(productId)
}
