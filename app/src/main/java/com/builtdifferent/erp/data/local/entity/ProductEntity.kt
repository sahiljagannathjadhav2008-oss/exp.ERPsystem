package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProductType { GOODS, SERVICE }

/**
 * A product's CURRENT selling/purchase price and reorder level live here for
 * convenience (fast reads on list/search screens), but neither is used
 * directly by invoice creation — see ProductPriceEntity for price history
 * and GstRateEntity (via hsnSacId) for tax. Invoice lines snapshot the price
 * and tax actually applied, so changing this row later never rewrites past
 * invoices.
 */
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = ProductCategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["unitId"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = HsnSacEntity::class,
            parentColumns = ["hsnSacId"],
            childColumns = ["hsnSacId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sku"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["unitId"]),
        Index(value = ["hsnSacId"]),
        Index(value = ["productName"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val productId: Long = 0,

    val productName: String,
    val sku: String,
    val type: ProductType = ProductType.GOODS,

    val categoryId: Long? = null,
    val unitId: Long,
    val hsnSacId: Long,

    /** Current selling price EXCLUSIVE of GST, in paise. */
    val sellingPricePaise: Long = 0,
    /** Current purchase/cost price EXCLUSIVE of GST, in paise. */
    val purchasePricePaise: Long = 0,

    /** Whether sellingPricePaise above is treated as tax-inclusive when
     * shown/entered in the UI; storage is always tax-exclusive internally so
     * the tax engine has one consistent basis. */
    val priceIsTaxInclusiveInUi: Boolean = false,

    val openingStockQty: Double = 0.0,
    val openingStockValuePaise: Long = 0,
    val reorderLevelQty: Double = 0.0,

    val trackInventory: Boolean = true,

    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
