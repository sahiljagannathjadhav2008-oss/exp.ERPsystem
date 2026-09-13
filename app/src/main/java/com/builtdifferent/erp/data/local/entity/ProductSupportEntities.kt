package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A product may have multiple barcodes (case pack vs single unit, or a
 * re-issued barcode). Scanning looks up this table, not ProductEntity. */
@Entity(
    tableName = "product_barcodes",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["productId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["barcode"], unique = true), Index(value = ["productId"])]
)
data class ProductBarcodeEntity(
    @PrimaryKey(autoGenerate = true)
    val productBarcodeId: Long = 0,
    val productId: Long,
    val barcode: String,
    val packSize: Double = 1.0
)

enum class PriceType { SELLING, PURCHASE }

/**
 * Price history log. ProductEntity.sellingPricePaise/purchasePricePaise
 * always reflect the CURRENT price for fast list reads, but every change is
 * appended here first (repository writes both in one transaction). This
 * lets a "price as of date" report exist and matches the same
 * snapshot-on-transaction philosophy used for GST rates.
 */
@Entity(
    tableName = "product_prices",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["productId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId", "effectiveFromMillis"])]
)
data class ProductPriceEntity(
    @PrimaryKey(autoGenerate = true)
    val productPriceId: Long = 0,
    val productId: Long,
    val type: PriceType,
    val pricePaise: Long,
    val effectiveFromMillis: Long = System.currentTimeMillis(),
    val note: String = ""
)
