package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BUSINESS RULE (documented per project convention — GST engine):
 *
 * GST rates are government-notified and change over time (e.g. a rate
 * revision effective from a notification date). This app therefore NEVER
 * stores "the GST rate" as a single mutable field on a product or HSN code.
 * Instead:
 *
 *  1. Each HsnSacEntity can have MANY GstRateEntity rows, each with an
 *     [effectiveFromMillis] and an optional [effectiveToMillis] (null =
 *     still in force). Only one row per hsnSacId may have a null
 *     effectiveToMillis at a time — enforced by the repository transaction
 *     that "closes" the previous rate row when a new one is inserted.
 *
 *  2. At the moment a sales/purchase invoice line is created, the engine
 *     looks up the rate that was effective ON THE INVOICE DATE and copies
 *     the resolved cgstRate/sgstRate/igstRate/cessRate AND the computed tax
 *     amounts directly onto the invoice line item row
 *     (see SalesInvoiceItemEntity.appliedCgstRate etc). The invoice line is
 *     therefore a permanent snapshot.
 *
 *  3. Consequently, changing or adding a GstRateEntity row in the future
 *     (e.g. a rate hike next month) can NEVER alter the tax shown on a
 *     historical invoice — this is what Section 6/Acceptance-Test-5
 *     ("change future GST rate, verify old invoices remain unchanged")
 *     requires, and it is enforced structurally rather than by convention.
 *
 * Split of total rate into CGST+SGST (intra-state) vs IGST (inter-state) is
 * decided at invoice-creation time by comparing the company's state code to
 * the party's state code — both rates are stored here so the engine does
 * not need to guess: for a standard slab, cgstRate == sgstRate == totalRate/2
 * and igstRate == totalRate, and only the applicable pair is applied.
 */
@Entity(
    tableName = "gst_rates",
    foreignKeys = [
        ForeignKey(
            entity = HsnSacEntity::class,
            parentColumns = ["hsnSacId"],
            childColumns = ["hsnSacId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["hsnSacId"]),
        Index(value = ["hsnSacId", "effectiveFromMillis"]),
        Index(value = ["hsnSacId", "effectiveToMillis"])
    ]
)
data class GstRateEntity(
    @PrimaryKey(autoGenerate = true)
    val gstRateId: Long = 0,

    val hsnSacId: Long,

    /** Total slab rate, e.g. 18.0 for 18%. Kept for display; the actual
     * split fields below are what the tax engine uses. */
    val totalRatePercent: Double,

    val cgstRatePercent: Double,
    val sgstRatePercent: Double,
    val igstRatePercent: Double,
    val cessRatePercent: Double = 0.0,

    val effectiveFromMillis: Long,
    /** Null means "currently in force". Set by the repository when a newer
     * rate row supersedes this one. */
    val effectiveToMillis: Long? = null,

    val notificationReference: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
