package com.builtdifferent.erp.data.local.dao

import androidx.room.*
import com.builtdifferent.erp.data.local.entity.CompanyEntity
import com.builtdifferent.erp.data.local.entity.FinancialYearEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {

    @Insert
    suspend fun insert(company: CompanyEntity): Long

    @Update
    suspend fun update(company: CompanyEntity)

    @Query("SELECT * FROM companies WHERE isActive = 1 LIMIT 1")
    fun observeActiveCompany(): Flow<CompanyEntity?>

    @Query("SELECT * FROM companies WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveCompany(): CompanyEntity?

    @Query("SELECT COUNT(*) FROM companies")
    suspend fun countCompanies(): Int

    @Query("UPDATE companies SET nextInvoiceNumber = nextInvoiceNumber + 1 WHERE companyId = :companyId")
    suspend fun incrementInvoiceNumber(companyId: Long)
}

@Dao
interface FinancialYearDao {

    @Insert
    suspend fun insert(financialYear: FinancialYearEntity): Long

    @Update
    suspend fun update(financialYear: FinancialYearEntity)

    @Query("SELECT * FROM financial_years WHERE companyId = :companyId ORDER BY startDateMillis DESC")
    fun observeAllForCompany(companyId: Long): Flow<List<FinancialYearEntity>>

    @Query("SELECT * FROM financial_years WHERE companyId = :companyId AND isCurrent = 1 LIMIT 1")
    suspend fun getCurrentFinancialYear(companyId: Long): FinancialYearEntity?

    @Query("SELECT * FROM financial_years WHERE companyId = :companyId AND isCurrent = 1 LIMIT 1")
    fun observeCurrentFinancialYear(companyId: Long): Flow<FinancialYearEntity?>

    @Query("UPDATE financial_years SET isCurrent = 0 WHERE companyId = :companyId")
    suspend fun clearCurrentFlag(companyId: Long)

    @Query("SELECT * FROM financial_years WHERE :dateMillis BETWEEN startDateMillis AND endDateMillis AND companyId = :companyId LIMIT 1")
    suspend fun findFinancialYearForDate(companyId: Long, dateMillis: Long): FinancialYearEntity?

    /** Sets exactly one FY as current for the company; the clear+set pair
     * runs inside a single Room transaction (see FinancialYearRepository)
     * so a query mid-way through can never observe two "current" years or
     * zero of them. */
    @Transaction
    suspend fun setCurrent(companyId: Long, financialYearId: Long) {
        clearCurrentFlag(companyId)
        markCurrent(financialYearId)
    }

    @Query("UPDATE financial_years SET isCurrent = 1 WHERE financialYearId = :financialYearId")
    suspend fun markCurrent(financialYearId: Long)
}
