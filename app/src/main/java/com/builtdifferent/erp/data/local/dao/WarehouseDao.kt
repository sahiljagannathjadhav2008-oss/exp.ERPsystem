package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.builtdifferent.erp.data.local.entity.WarehouseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WarehouseDao {

    @Insert
    suspend fun insert(warehouse: WarehouseEntity): Long

    @Update
    suspend fun update(warehouse: WarehouseEntity)

    @Query("SELECT * FROM warehouses WHERE isActive = 1 ORDER BY name ASC")
    fun observeAll(): Flow<List<WarehouseEntity>>

    @Query("SELECT * FROM warehouses WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): WarehouseEntity?

    @Query("SELECT COUNT(*) FROM warehouses")
    suspend fun count(): Int
}
