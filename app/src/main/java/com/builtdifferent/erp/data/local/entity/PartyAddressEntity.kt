package com.builtdifferent.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AddressType { BILLING, SHIPPING, OTHER }

/** A party's primary billing/shipping address lives inline on PartyEntity
 * for the common case; this table holds ADDITIONAL addresses (e.g. a
 * customer with three delivery sites), selectable per invoice. */
@Entity(
    tableName = "party_addresses",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["partyId"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["partyId"])]
)
data class PartyAddressEntity(
    @PrimaryKey(autoGenerate = true)
    val partyAddressId: Long = 0,
    val partyId: Long,
    val label: String = "",
    val type: AddressType = AddressType.SHIPPING,
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val stateCode: String = "",
    val pinCode: String = "",
    val isDefault: Boolean = false
)
