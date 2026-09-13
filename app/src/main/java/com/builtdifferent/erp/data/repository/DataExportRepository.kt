package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.PartyEntity
import com.builtdifferent.erp.data.local.entity.PartyType
import com.builtdifferent.erp.util.CsvUtil
import com.builtdifferent.erp.util.Money
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Export writes exactly what's on screen elsewhere in the app — the same
 * PartyEntity/ProductEntity fields other repositories already expose,
 * reformatted as CSV, never a second copy of business logic that could
 * compute a different number. Import goes through the SAME
 * PartyRepository.createParty used by the Add Party screen, so an
 * imported party gets its ledger account created exactly the same way a
 * manually-entered one does — there is no bypass path that could create a
 * party without one.
 */
class DataExportRepository(private val db: AppDatabase, private val partyRepository: PartyRepository) {

    suspend fun exportPartiesToCsv(file: File) {
        val parties = db.partyDao().observeParties(null, "").first()
        CsvUtil.writeCsv(
            file = file,
            header = listOf("Name", "Type", "Phone", "Email", "GSTIN", "State Code", "Opening Balance"),
            rows = parties.map { p ->
                listOf(
                    p.partyName, p.type.name, p.phone, p.email, p.gstin ?: "",
                    p.billingStateCode, Money(p.openingBalancePaise).toRupees().toPlainString()
                )
            }
        )
    }

    suspend fun exportProductsToCsv(file: File) {
        val products = db.productDao().search("").first()
        CsvUtil.writeCsv(
            file = file,
            header = listOf("Name", "SKU", "Selling Price", "Purchase Price", "Reorder Level"),
            rows = products.map { p ->
                listOf(
                    p.productName, p.sku,
                    Money(p.sellingPricePaise).toRupees().toPlainString(),
                    Money(p.purchasePricePaise).toRupees().toPlainString(),
                    p.reorderLevelQty.toString()
                )
            }
        )
    }

    /** Imports parties from a CSV with the same column layout
     * exportPartiesToCsv produces. Returns (successCount, errors) rather
     * than throwing on the first bad row, since a real-world import file
     * is likely to have a handful of malformed rows mixed into otherwise
     * good data, and the user should get everything valid rather than
     * nothing. */
    suspend fun importPartiesFromCsv(file: File): Pair<Int, List<String>> {
        val (header, rows) = CsvUtil.readCsv(file)
        val nameIdx = header.indexOf("Name")
        val typeIdx = header.indexOf("Type")
        val phoneIdx = header.indexOf("Phone")
        val gstinIdx = header.indexOf("GSTIN")
        val stateCodeIdx = header.indexOf("State Code")

        if (nameIdx < 0) return 0 to listOf("CSV is missing a required 'Name' column")

        var successCount = 0
        val errors = mutableListOf<String>()
        rows.forEachIndexed { index, row ->
            try {
                val name = row.getOrNull(nameIdx)?.trim().orEmpty()
                if (name.isBlank()) throw IllegalArgumentException("Name is required")
                val type = row.getOrNull(typeIdx)?.trim()?.let {
                    try { PartyType.valueOf(it) } catch (e: IllegalArgumentException) { PartyType.CUSTOMER }
                } ?: PartyType.CUSTOMER

                partyRepository.createParty(
                    PartyEntity(
                        partyName = name,
                        type = type,
                        phone = row.getOrNull(phoneIdx)?.trim().orEmpty(),
                        gstin = row.getOrNull(gstinIdx)?.trim()?.ifBlank { null },
                        billingStateCode = row.getOrNull(stateCodeIdx)?.trim().orEmpty(),
                        isUnregistered = row.getOrNull(gstinIdx)?.trim().isNullOrBlank()
                    )
                )
                successCount++
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: ${e.message}")
            }
        }
        return successCount to errors
    }
}
