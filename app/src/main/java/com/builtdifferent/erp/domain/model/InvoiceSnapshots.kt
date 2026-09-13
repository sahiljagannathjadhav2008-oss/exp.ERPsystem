package com.builtdifferent.erp.domain.model

import com.builtdifferent.erp.data.local.entity.CompanyEntity
import com.builtdifferent.erp.data.local.entity.PartyEntity
import org.json.JSONObject

/**
 * Serializes just the fields that appear on a printed invoice, at the
 * moment of posting, into the invoice row's companySnapshotJson /
 * partySnapshotJson columns (see SalesInvoiceEntity doc comment). Uses
 * org.json (bundled with Android) rather than pulling in a JSON library
 * dependency, since the shape here is small and fixed.
 */
object CompanySnapshot {
    fun toJson(company: CompanyEntity): String = JSONObject().apply {
        put("legalName", company.legalName)
        put("tradeName", company.tradeName ?: "")
        put("addressLine1", company.addressLine1)
        put("addressLine2", company.addressLine2)
        put("city", company.city)
        put("state", company.state)
        put("stateCode", company.stateCode)
        put("pinCode", company.pinCode)
        put("gstin", company.gstin ?: "")
        put("pan", company.pan ?: "")
        put("phone", company.phone)
        put("email", company.email)
        put("bankAccountName", company.bankAccountName)
        put("bankAccountNumber", company.bankAccountNumber)
        put("bankIfsc", company.bankIfsc)
        put("bankName", company.bankName)
        put("bankBranch", company.bankBranch)
        put("termsAndConditions", company.termsAndConditions)
        put("authorizedSignatory", company.authorizedSignatory)
        put("logoPath", company.logoPath ?: "")
    }.toString()
}

object PartySnapshot {
    fun toJson(party: PartyEntity): String = JSONObject().apply {
        put("partyName", party.partyName)
        put("gstin", party.gstin ?: "")
        put("phone", party.phone)
        put("email", party.email)
        put("billingAddressLine1", party.billingAddressLine1)
        put("billingAddressLine2", party.billingAddressLine2)
        put("billingCity", party.billingCity)
        put("billingState", party.billingState)
        put("billingStateCode", party.billingStateCode)
        put("billingPinCode", party.billingPinCode)
        put("shippingSameAsBilling", party.shippingSameAsBilling)
        put("shippingAddressLine1", party.shippingAddressLine1)
        put("shippingCity", party.shippingCity)
        put("shippingState", party.shippingState)
        put("shippingStateCode", party.shippingStateCode)
        put("shippingPinCode", party.shippingPinCode)
    }.toString()
}
