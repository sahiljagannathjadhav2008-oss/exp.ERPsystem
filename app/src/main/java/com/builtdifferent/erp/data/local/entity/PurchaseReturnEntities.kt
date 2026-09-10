package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_returns",
    foreignKeys = [
        ForeignKey(PurchaseInvoiceEntity::class, ["purchaseInvoiceId"], ["originalPurchaseInvoiceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["returnNumber"], unique = true), Index(value = ["originalPurchaseInvoiceId"])]
)
data class PurchaseReturnEntity(
    @PrimaryKey(autoGenerate = true) val purchaseReturnId: Long = 0,
    val returnNumber: String,
    val returnDateMillis: Long,
    val financialYearId: Long,
    val originalPurchaseInvoiceId: Long,
    val reason: String = "",
    val subTotalPaise: Long,
    val totalTaxPaise: Long,
    val grandTotalPaise: Long,
    val debitNoteId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_return_items",
    foreignKeys = [
        ForeignKey(PurchaseReturnEntity::class, ["purchaseReturnId"], ["purchaseReturnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PurchaseInvoiceItemEntity::class, ["purchaseInvoiceItemId"], ["originalPurchaseInvoiceItemId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["purchaseReturnId"]), Index(value = ["originalPurchaseInvoiceItemId"])]
)
data class PurchaseReturnItemEntity(
    @PrimaryKey(autoGenerate = true) val purchaseReturnItemId: Long = 0,
    val purchaseReturnId: Long,
    val originalPurchaseInvoiceItemId: Long,
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

/** Fiscal document reducing the supplier's payable, mirroring CreditNote's
 * role on the sales side. */
@Entity(
    tableName = "debit_notes",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["debitNoteNumber"], unique = true), Index(value = ["partyId"]), Index(value = ["financialYearId"])]
)
data class DebitNoteEntity(
    @PrimaryKey(autoGenerate = true) val debitNoteId: Long = 0,
    val debitNoteNumber: String,
    val debitNoteDateMillis: Long,
    val financialYearId: Long,
    val partyId: Long,
    val purchaseReturnId: Long? = null,
    val amountPaise: Long,
    val reason: String = "",
    val journalEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
