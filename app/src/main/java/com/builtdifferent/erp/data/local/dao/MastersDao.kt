package com.builtdifferent.erp.data.local.dao

import androidx.room.*
import com.builtdifferent.erp.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitDao {
    @Insert
    suspend fun insert(unit: UnitEntity): Long

    @Update
    suspend fun update(unit: UnitEntity)

    @Query("SELECT * FROM units WHERE isActive = 1 ORDER BY displayLabel ASC")
    fun observeAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE unitId = :unitId")
    suspend fun getById(unitId: Long): UnitEntity?

    @Query("SELECT COUNT(*) FROM units")
    suspend fun count(): Int
}

@Dao
interface ProductCategoryDao {
    @Insert
    suspend fun insert(category: ProductCategoryEntity): Long

    @Update
    suspend fun update(category: ProductCategoryEntity)

    @Query("SELECT * FROM product_categories WHERE isActive = 1 ORDER BY name ASC")
    fun observeAll(): Flow<List<ProductCategoryEntity>>
}

@Dao
interface HsnSacDao {
    @Insert
    suspend fun insert(hsnSac: HsnSacEntity): Long

    @Update
    suspend fun update(hsnSac: HsnSacEntity)

    @Query("SELECT * FROM hsn_sac_codes WHERE isActive = 1 ORDER BY code ASC")
    fun observeAll(): Flow<List<HsnSacEntity>>

    @Query("SELECT * FROM hsn_sac_codes WHERE hsnSacId = :id")
    suspend fun getById(id: Long): HsnSacEntity?

    @Query("SELECT * FROM hsn_sac_codes WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): HsnSacEntity?

    @Query(
        """SELECT * FROM hsn_sac_codes
           WHERE isActive = 1 AND (code LIKE :search || '%' OR description LIKE '%' || :search || '%')
           ORDER BY code ASC"""
    )
    fun search(search: String): Flow<List<HsnSacEntity>>
}

@Dao
interface GstRateDao {

    @Insert
    suspend fun insert(rate: GstRateEntity): Long

    @Query("UPDATE gst_rates SET effectiveToMillis = :effectiveToMillis WHERE gstRateId = :gstRateId")
    suspend fun closeRate(gstRateId: Long, effectiveToMillis: Long)

    @Query("SELECT * FROM gst_rates WHERE hsnSacId = :hsnSacId AND effectiveToMillis IS NULL LIMIT 1")
    suspend fun getCurrentRate(hsnSacId: Long): GstRateEntity?

    @Query(
        """SELECT * FROM gst_rates WHERE hsnSacId = :hsnSacId
           AND effectiveFromMillis <= :asOfMillis
           AND (effectiveToMillis IS NULL OR effectiveToMillis >= :asOfMillis)
           ORDER BY effectiveFromMillis DESC LIMIT 1"""
    )
    suspend fun getRateEffectiveOn(hsnSacId: Long, asOfMillis: Long): GstRateEntity?

    @Query("SELECT * FROM gst_rates WHERE hsnSacId = :hsnSacId ORDER BY effectiveFromMillis DESC")
    fun observeHistoryForHsnSac(hsnSacId: Long): Flow<List<GstRateEntity>>

    /**
     * Inserts a new effective-dated rate while closing off whatever rate
     * was previously open-ended for the same HSN/SAC, atomically. This is
     * the ONLY way a GST rate should ever be changed in this app — there is
     * deliberately no "update rate" DAO method, because updating in place
     * would corrupt the historical snapshot guarantee described on
     * GstRateEntity.
     */
    @Transaction
    suspend fun addNewEffectiveRate(newRate: GstRateEntity) {
        val current = getCurrentRate(newRate.hsnSacId)
        if (current != null) {
            closeRate(current.gstRateId, newRate.effectiveFromMillis - 1)
        }
        insert(newRate)
    }
}
