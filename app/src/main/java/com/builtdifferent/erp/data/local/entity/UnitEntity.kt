package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Units of measure, e.g. PCS, KG, BOX, LTR. GST returns require a standard
 * UQC (Unit Quantity Code) per line item, so unitCode should map to GST's
 * UQC list (e.g. "NOS", "KGS", "LTR") while displayLabel can be a friendlier
 * localized label shown in the UI. */
@Entity(
    tableName = "units",
    indices = [Index(value = ["unitCode"], unique = true)]
)
data class UnitEntity(
    @PrimaryKey(autoGenerate = true)
    val unitId: Long = 0,
    val unitCode: String,
    val displayLabel: String,
    val allowDecimalQuantity: Boolean = true,
    val isActive: Boolean = true
)
