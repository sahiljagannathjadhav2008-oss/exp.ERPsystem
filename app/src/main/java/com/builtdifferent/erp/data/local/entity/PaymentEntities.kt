package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentMode { CASH, BANK_TRANSFER, CHEQUE, UPI, CARD, OTHER }

/** A single payment received from a customer may be a partial payment
 * against one invoice, a lump sum against several, or an on-account payment
 * against none yet (allocated later) — PaymentAllocationEntity is what maps
 * "how much of this payment applies to which invoice", so "outstanding
 * amount" for any invoice is always SUM(invoice total) - SUM(allocations),
 * never a separately maintained field that can drift. */
@Entity(
    tableName = "payments_received",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["partyId"]), Index(value = ["financialYearId"]), Index(value = ["paymentDateMillis"])]
)
data class PaymentReceivedEntity(
    @PrimaryKey(autoGenerate = true) val paymentReceivedId: Long = 0,
    val receiptNumber: String,
    val paymentDateMillis: Long,
    val financialYearId: Long,
    val partyId: Long,
    val mode: PaymentMode,
    val amountPaise: Long,
    val bankAccountId: Long? = null,
    val referenceNumber: String = "",
    val note: String = "",
    val journalEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments_made",
    foreignKeys = [
        ForeignKey(PartyEntity::class, ["partyId"], ["partyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(FinancialYearEntity::class, ["financialYearId"], ["financialYearId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["partyId"]), Index(value = ["financialYearId"]), Index(value = ["paymentDateMillis"])]
)
data class PaymentMadeEntity(
    @PrimaryKey(autoGenerate = true) val paymentMadeId: Long = 0,
    val paymentNumber: String,
    val paymentDateMillis: Long,
    val financialYearId: Long,
    val partyId: Long,
    val mode: PaymentMode,
    val amountPaise: Long,
    val bankAccountId: Long? = null,
    val referenceNumber: String = "",
    val note: String = "",
    val journalEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AllocationTargetType { SALES_INVOICE, PURCHASE_INVOICE }

@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(PaymentReceivedEntity::class, ["paymentReceivedId"], ["paymentReceivedId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PaymentMadeEntity::class, ["paymentMadeId"], ["paymentMadeId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["paymentReceivedId"]),
        Index(value = ["paymentMadeId"]),
        Index(value = ["targetType", "targetId"])
    ]
)
data class PaymentAllocationEntity(
    @PrimaryKey(autoGenerate = true) val paymentAllocationId: Long = 0,
    /** Exactly one of paymentReceivedId / paymentMadeId is non-null. */
    val paymentReceivedId: Long? = null,
    val paymentMadeId: Long? = null,
    val targetType: AllocationTargetType,
    val targetId: Long,
    val allocatedAmountPaise: Long
)
