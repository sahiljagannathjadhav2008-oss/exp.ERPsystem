package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class HsnSacType { HSN, SAC }

/** Master list of HSN (goods) / SAC (services) codes used by products. The
 * GST rate is intentionally NOT a column here — rates change over time by
 * government notification, so they live in GstRateEntity as effective-dated
 * rows keyed to an hsnSacId. See GstRateEntity for the full rationale. */
@Entity(
    tableName = "hsn_sac_codes",
    indices = [Index(value = ["code"], unique = true)]
)
data class HsnSacEntity(
    @PrimaryKey(autoGenerate = true)
    val hsnSacId: Long = 0,
    val code: String,
    val type: HsnSacType,
    val description: String = "",
    val isActive: Boolean = true
)
