package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class InvoiceStatus { DRAFT, POSTED, CANCELLED }

/** B2B = registered party, GSTIN captured; B2C = unregistered/consumer.
 * Determines GST return classification and which fields are mandatory. */
enum class GstInvoiceCategory { B2B, B2C }

/**
 * Sales invoice header. This — together with SalesInvoiceItemEntity — is the
 * permanent legal record of the sale: nothing on it changes retroactively
 * when company info, party info, product price, or GST rate masters change
 * later (Section 6/8). Everything needed to reprint the exact original PDF
 * is captured here or on the line items, either directly or via
 * companySnapshotJson/partySnapshotJson.
 *
 * Posting an invoice (status DRAFT -> POSTED) is the action that:
 *  1. Creates StockMovementEntity rows (SALE, one per line item) and
 *     decrements StockEntity — see InventoryRepository.
 *  2. Creates a balanced JournalEntryEntity (Dr Party/Cash, Cr Sales,
 *     Cr CGST/SGST/IGST Output) — see AccountingRepository.
 *  3. Locks the invoice number (see InvoiceSequenceEntity) and the row
 *     itself against further field edits (only cancellation via a
 *     SalesReturn/CreditNote is allowed afterwards).
 * A DRAFT invoice has none of these effects yet and can still be freely
 * edited or deleted.
 */
@Entity(
    tableName = "sales_invoices",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["partyId"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = FinancialYearEntity::class,
            parentColumns = ["financialYearId"],
            childColumns = ["financialYearId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WarehouseEntity::class,
            parentColumns = ["warehouseId"],
            childColumns = ["warehouseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["invoiceNumber"], unique = true),
        Index(value = ["partyId"]),
        Index(value = ["financialYearId"]),
        Index(value = ["invoiceDateMillis"]),
        Index(value = ["status"])
    ]
)
data class SalesInvoiceEntity(
    @PrimaryKey(autoGenerate = true)
    val salesInvoiceId: Long = 0,

    val invoiceNumber: String,
    val invoiceDateMillis: Long,
    val financialYearId: Long,

    val partyId: Long,
    val warehouseId: Long,

    val gstCategory: GstInvoiceCategory,
    /** True when partyStateCode != companyStateCode at the time of posting
     * — determines whether CGST+SGST or IGST columns are populated on the
     * line items. Snapshotted so a later change to either party's or the
     * company's state never reclassifies a historical invoice. */
    val isInterState: Boolean,

    val placeOfSupplyStateCode: String,

    /** Full JSON snapshot of company details (name/address/GSTIN/bank/logo
     * path) at the moment of posting, used to render the PDF unchanged
     * forever regardless of later company edits. */
    val companySnapshotJson: String,
    /** Full JSON snapshot of party billing+shipping details at posting
     * time, for the same reason. */
    val partySnapshotJson: String,

    val subTotalPaise: Long,
    val totalDiscountPaise: Long = 0,
    val totalTaxableValuePaise: Long,
    val totalCgstPaise: Long = 0,
    val totalSgstPaise: Long = 0,
    val totalIgstPaise: Long = 0,
    val totalCessPaise: Long = 0,
    val roundOffPaise: Long = 0,
    val grandTotalPaise: Long,

    val amountInWords: String,

    val status: InvoiceStatus = InvoiceStatus.DRAFT,

    val notes: String = "",
    val termsAndConditions: String = "",

    /** Path to the generated invoice PDF, populated once rendered. */
    val pdfFilePath: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sales_invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = SalesInvoiceEntity::class,
            parentColumns = ["salesInvoiceId"],
            childColumns = ["salesInvoiceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["productId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["salesInvoiceId"]), Index(value = ["productId"])]
)
data class SalesInvoiceItemEntity(
    @PrimaryKey(autoGenerate = true)
    val salesInvoiceItemId: Long = 0,
    val salesInvoiceId: Long,
    val productId: Long,

    /** Product name/HSN snapshotted so a later rename doesn't alter a
     * historical invoice line's printed description. */
    val productNameSnapshot: String,
    val hsnSacCodeSnapshot: String,

    val quantity: Double,
    val unitLabelSnapshot: String,

    /** Unit price EXCLUSIVE of GST, in paise, as actually charged. */
    val unitPricePaise: Long,
    val discountPercent: Double = 0.0,
    val discountAmountPaise: Long = 0,

    val taxableValuePaise: Long,

    /** Rates and amounts actually applied — the GstRateEntity snapshot
     * described on that class. Exactly one of (cgst+sgst) or igst is
     * non-zero, matching SalesInvoiceEntity.isInterState. */
    val appliedCgstRatePercent: Double = 0.0,
    val appliedSgstRatePercent: Double = 0.0,
    val appliedIgstRatePercent: Double = 0.0,
    val appliedCessRatePercent: Double = 0.0,
    val cgstAmountPaise: Long = 0,
    val sgstAmountPaise: Long = 0,
    val igstAmountPaise: Long = 0,
    val cessAmountPaise: Long = 0,

    val lineTotalPaise: Long,

    val sortOrder: Int = 0
)
