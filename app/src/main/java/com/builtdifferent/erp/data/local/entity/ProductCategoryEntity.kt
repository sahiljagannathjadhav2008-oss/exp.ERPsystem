package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_categories")
data class ProductCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val categoryId: Long = 0,
    val name: String,
    val parentCategoryId: Long? = null,
    val isActive: Boolean = true
)
