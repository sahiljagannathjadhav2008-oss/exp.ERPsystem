package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A sales return always references the original invoice it returns against
 * (originalSalesInvoiceId) and, on posting, mirrors that invoice's tax
 * treatment exactly (same CGST/SGST/IGST split, computed from the ORIGINAL
 * invoice's applied rates — never re-looked-up from current GST masters).
 * Posting creates: a SALE_RETURN stock movement (stock restored), a
 * balanced reversing JournalEntry, and reduces the party's receivable.
 */
@Entity(
    tableName = "sales_returns",
    foreignKeys = [
        ForeignKey(SalesInvoiceEntity::class, ["salesInvoiceId"], ["originalSalesInvoiceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["returnNumber"], unique = true), Index(value = ["originalSalesInvoiceId"])]
)
data class SalesReturnEntity(
    @PrimaryKey(autoGenerate = true) val salesReturnId: Long = 0,
    val returnNumber: String,
    val returnDateMillis: Long,
    val financialYearId: Long,
    val originalSalesInvoiceId: Long,
    val reason: String = "",
    val subTotalPaise: Long,
    val totalTaxPaise: Long,
    val grandTotalPaise: Long,
    val creditNoteId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sales_return_items",
    foreignKeys = [
        ForeignKey(SalesReturnEntity::class, ["salesReturnId"], ["salesReturnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SalesInvoiceItemEntity::class, ["salesInvoiceItemId"], ["originalSalesInvoiceItemId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["salesReturnId"]), Index(value = ["originalSalesInvoiceItemId"])]
)
data class SalesReturnItemEntity(
    @PrimaryKey(autoGenerate = true) val salesReturnItemId: Long = 0,
    val salesReturnId: Long,
    val originalSalesInvoiceItemId: Long,
    val productId: Long,
    val quantity: Double,
    val unitPricePaise: Long,
    val taxableValuePaise: Long,
    val cgstAmountPaise: Long = 0,
    val sgstAmountPaise: Long = 0,
    val igstAmountPaise: Long = 0,
    val cessAmountPaise: Long = 0,
    val lineTotalPaise: Long
)

/** A credit note is the financial document issued for a sales return (or a
 * standalone price adjustment) — it is what actually reduces the party's
 * receivable in GST returns terminology; SalesReturnEntity is the
 * inventory/reason record, CreditNoteEntity is the fiscal document. */
@Entity(
    tableName = "credit_notes",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["creditNoteNumber"], unique = true), Index(value = ["partyId"]), Index(value = ["financialYearId"])]
)
data class CreditNoteEntity(
    @PrimaryKey(autoGenerate = true) val creditNoteId: Long = 0,
    val creditNoteNumber: String,
    val creditNoteDateMillis: Long,
    val financialYearId: Long,
    val partyId: Long,
    val salesReturnId: Long? = null,
    val amountPaise: Long,
    val reason: String = "",
    val journalEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
