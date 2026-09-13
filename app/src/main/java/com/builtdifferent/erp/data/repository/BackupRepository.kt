package com.builtdifferent.erp.data.repository

import android.content.Context
import android.net.Uri
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.entity.BackupMetadataEntity
import com.builtdifferent.erp.data.local.entity.BackupType
import java.io.File
import java.security.MessageDigest

data class BackupResult(val checksumSha256: String, val fileSizeBytes: Long)

/**
 * BUSINESS RULE (documented per project convention — backup/restore):
 *
 * Backup copies the raw SQLite database file (not a re-serialized export)
 * so restore is byte-exact — every table, index, and constraint comes back
 * exactly as it was, with no re-import logic that could silently drop or
 * misinterpret a row. This must run with the database CLOSED: the caller
 * is responsible for closing the Room database before calling restoreFrom,
 * and restarting the app process afterward to reopen it against the
 * restored file. Attempting to copy the file while Room holds it open
 * risks an inconsistent snapshot if a write is mid-flight, which is why
 * this repository does not try to work around that itself — silently
 * "helping" here is more dangerous than requiring an explicit close/
 * reopen step from the caller.
 *
 * Every backup's SHA-256 checksum is computed and stored in
 * BackupMetadataEntity at backup time and re-verified at restore time —
 * restore refuses to proceed if the checksum doesn't match, catching a
 * corrupted or truncated backup file before it overwrites live data.
 */
class BackupRepository(private val context: Context, private val db: AppDatabase) {

    private fun databaseFile(): File = context.getDatabasePath(AppDatabase.DATABASE_NAME)

    fun backupTo(destination: Uri): BackupResult {
        val dbFile = databaseFile()
        val digest = MessageDigest.getInstance("SHA-256")

        context.contentResolver.openOutputStream(destination)?.use { output ->
            dbFile.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead >= 0) {
                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
        } ?: throw IllegalStateException("Could not open destination for writing")

        val checksum = digest.digest().joinToString("") { "%02x".format(it) }
        return BackupResult(checksumSha256 = checksum, fileSizeBytes = dbFile.length())
    }

    /** Records the backup metadata row. Call this with a live database
     * connection — before closing the database for the file copy, or
     * immediately after backupTo returns, but never expect backupTo
     * itself to touch Room (see class doc). */
    suspend fun recordBackupMetadata(filePath: String, result: BackupResult, appVersionName: String, type: BackupType) {
        db.backupMetadataDao().insert(
            BackupMetadataEntity(
                filePath = filePath,
                fileSizeBytes = result.fileSizeBytes,
                type = type,
                appVersionName = appVersionName,
                databaseVersion = AppDatabase.SCHEMA_VERSION,
                checksumSha256 = result.checksumSha256
            )
        )
    }

    /**
     * Verifies the source file's checksum against [expectedChecksum] (from
     * BackupMetadataEntity, if known) and, only if it matches (or none was
     * supplied), copies it over the live database file. Returns false
     * without copying anything if the checksum doesn't match. The caller
     * MUST have already closed the Room database and MUST restart the app
     * process afterward — this function only ever touches the file on
     * disk.
     */
    fun restoreFrom(source: Uri, expectedChecksum: String?): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val tempFile = File(context.cacheDir, "restore_temp.db")

        context.contentResolver.openInputStream(source)?.use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead >= 0) {
                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
        } ?: throw IllegalStateException("Could not open backup file for reading")

        val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
        if (expectedChecksum != null && actualChecksum != expectedChecksum) {
            tempFile.delete()
            return false
        }

        val dbFile = databaseFile()
        tempFile.copyTo(dbFile, overwrite = true)
        tempFile.delete()
        // Clear any stale WAL/SHM files so SQLite doesn't try to replay
        // write-ahead-log frames from the OLD database against the
        // newly-restored one.
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        return true
    }
}
