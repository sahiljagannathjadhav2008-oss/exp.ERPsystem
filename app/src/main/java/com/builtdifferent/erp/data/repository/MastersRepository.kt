package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.dao.GstRateDao
import com.builtdifferent.erp.data.local.dao.HsnSacDao
import com.builtdifferent.erp.data.local.dao.ProductCategoryDao
import com.builtdifferent.erp.data.local.dao.UnitDao
import com.builtdifferent.erp.data.local.entity.GstRateEntity
import com.builtdifferent.erp.data.local.entity.HsnSacEntity
import com.builtdifferent.erp.data.local.entity.ProductCategoryEntity
import com.builtdifferent.erp.data.local.entity.UnitEntity
import kotlinx.coroutines.flow.Flow

class UnitRepository(private val unitDao: UnitDao) {
    fun observeAll(): Flow<List<UnitEntity>> = unitDao.observeAll()
    suspend fun create(displayLabel: String, unitCode: String, allowDecimal: Boolean): Long =
        unitDao.insert(UnitEntity(unitCode = unitCode.uppercase(), displayLabel = displayLabel, allowDecimalQuantity = allowDecimal))
    suspend fun getById(id: Long) = unitDao.getById(id)
}

class ProductCategoryRepository(private val categoryDao: ProductCategoryDao) {
    fun observeAll(): Flow<List<ProductCategoryEntity>> = categoryDao.observeAll()
    suspend fun create(name: String, parentId: Long? = null): Long =
        categoryDao.insert(ProductCategoryEntity(name = name, parentCategoryId = parentId))
}

/**
 * Owns both HSN/SAC codes and their effective-dated GST rates, since the two
 * are always edited together from the UI's point of view ("add an HSN code
 * with its current GST rate") even though they are two tables — see
 * GstRateEntity for why they must remain two tables rather than one.
 */
class HsnSacRepository(
    private val hsnSacDao: HsnSacDao,
    private val gstRateDao: GstRateDao
) {
    fun observeAll(): Flow<List<HsnSacEntity>> = hsnSacDao.observeAll()
    fun search(query: String): Flow<List<HsnSacEntity>> = hsnSacDao.search(query)
    suspend fun getById(id: Long) = hsnSacDao.getById(id)

    suspend fun createHsnSacWithRate(
        code: String,
        type: com.builtdifferent.erp.data.local.entity.HsnSacType,
        description: String,
        totalRatePercent: Double,
        effectiveFromMillis: Long,
        notificationReference: String = ""
    ): Long {
        val hsnSacId = hsnSacDao.insert(
            HsnSacEntity(code = code, type = type, description = description)
        )
        val half = totalRatePercent / 2.0
        gstRateDao.addNewEffectiveRate(
            GstRateEntity(
                hsnSacId = hsnSacId,
                totalRatePercent = totalRatePercent,
                cgstRatePercent = half,
                sgstRatePercent = half,
                igstRatePercent = totalRatePercent,
                effectiveFromMillis = effectiveFromMillis,
                notificationReference = notificationReference
            )
        )
        return hsnSacId
    }

    /** Revises the rate for an existing HSN/SAC code, e.g. after a
     * government rate-change notification. The OLD rate is closed, not
     * deleted or overwritten — see GstRateDao.addNewEffectiveRate. */
    suspend fun reviseRate(
        hsnSacId: Long,
        newTotalRatePercent: Double,
        effectiveFromMillis: Long,
        notificationReference: String = ""
    ) {
        val half = newTotalRatePercent / 2.0
        gstRateDao.addNewEffectiveRate(
            GstRateEntity(
                hsnSacId = hsnSacId,
                totalRatePercent = newTotalRatePercent,
                cgstRatePercent = half,
                sgstRatePercent = half,
                igstRatePercent = newTotalRatePercent,
                effectiveFromMillis = effectiveFromMillis,
                notificationReference = notificationReference
            )
        )
    }

    suspend fun getCurrentRate(hsnSacId: Long) = gstRateDao.getCurrentRate(hsnSacId)
    suspend fun getRateEffectiveOn(hsnSacId: Long, asOfMillis: Long) = gstRateDao.getRateEffectiveOn(hsnSacId, asOfMillis)
    fun observeRateHistory(hsnSacId: Long) = gstRateDao.observeHistoryForHsnSac(hsnSacId)
}
