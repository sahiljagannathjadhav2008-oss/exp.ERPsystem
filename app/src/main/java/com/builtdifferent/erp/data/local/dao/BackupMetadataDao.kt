package com.builtdifferent.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.builtdifferent.erp.data.local.entity.BackupMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupMetadataDao {
    @Insert
    suspend fun insert(backup: BackupMetadataEntity): Long

    @Query("SELECT * FROM backup_metadata ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BackupMetadataEntity>>

    @Query("UPDATE backup_metadata SET restoredAt = :restoredAt WHERE backupMetadataId = :id")
    suspend fun markRestored(id: Long, restoredAt: Long)
}
