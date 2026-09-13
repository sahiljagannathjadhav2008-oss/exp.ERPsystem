package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PartyType { CUSTOMER, SUPPLIER, BOTH }

/**
 * Customers and suppliers share the same table (PartyType.BOTH covers a
 * party that is both, e.g. a job-work vendor you also sell to) because they
 * share almost every field and both need a running ledger account — see
 * LedgerAccountEntity, one of which is auto-created per party.
 *
 * Parties are never hard-deleted once they have any transaction against
 * them (Section 6: "do not delete historical accounting data just because
 * master data changes") — isActive = false is used instead ("soft delete").
 */
@Entity(
    tableName = "parties",
    indices = [
        Index(value = ["gstin"]),
        Index(value = ["phone"]),
        Index(value = ["partyName"]),
        Index(value = ["type"])
    ]
)
data class PartyEntity(
    @PrimaryKey(autoGenerate = true)
    val partyId: Long = 0,

    val partyName: String,
    val type: PartyType,

    val phone: String = "",
    val email: String = "",

    val gstin: String? = null,
    val pan: String? = null,

    /** True when the party has no GSTIN and is treated as an unregistered /
     * consumer party for GST purposes (affects invoice type, e.g. B2C). */
    val isUnregistered: Boolean = true,

    val billingAddressLine1: String = "",
    val billingAddressLine2: String = "",
    val billingCity: String = "",
    val billingState: String = "",
    val billingStateCode: String = "",
    val billingPinCode: String = "",

    val shippingSameAsBilling: Boolean = true,
    val shippingAddressLine1: String = "",
    val shippingAddressLine2: String = "",
    val shippingCity: String = "",
    val shippingState: String = "",
    val shippingStateCode: String = "",
    val shippingPinCode: String = "",

    /** Opening balance as of the party's creation, in paise. Positive means
     * the party owes the business (receivable); negative means the business
     * owes the party (payable). Stored separately from computed running
     * balance so it survives even if transaction history is later purged
     * for very old years. */
    val openingBalancePaise: Long = 0,
    val openingBalanceAsOfMillis: Long = System.currentTimeMillis(),

    /** Optional credit limit in paise; 0 = no limit enforced. */
    val creditLimitPaise: Long = 0,
    val creditPeriodDays: Int = 0,

    /** FK to the auto-created ledger account for this party (see
     * LedgerAccountEntity). Nullable only during the single insert
     * transaction that creates both rows. */
    val ledgerAccountId: Long? = null,

    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
