package com.builtdifferent.erp.data.repository

import com.builtdifferent.erp.data.local.dao.CompanyDao
import com.builtdifferent.erp.data.local.dao.FinancialYearDao
import com.builtdifferent.erp.data.local.entity.CompanyEntity
import com.builtdifferent.erp.data.local.entity.FinancialYearEntity
import com.builtdifferent.erp.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class CompanyRepository(private val companyDao: CompanyDao) {

    fun observeActiveCompany(): Flow<CompanyEntity?> = companyDao.observeActiveCompany()

    suspend fun getActiveCompany(): CompanyEntity? = companyDao.getActiveCompany()

    suspend fun hasAnyCompany(): Boolean = companyDao.countCompanies() > 0

    suspend fun saveCompany(company: CompanyEntity): Long {
        return if (company.companyId == 0L) {
            companyDao.insert(company.copy(createdAt = DateUtils.nowMillis(), updatedAt = DateUtils.nowMillis()))
        } else {
            companyDao.update(company.copy(updatedAt = DateUtils.nowMillis()))
            company.companyId
        }
    }

    suspend fun reserveNextInvoiceNumber(company: CompanyEntity): String {
        val number = company.nextInvoiceNumber
        companyDao.incrementInvoiceNumber(company.companyId)
        return company.invoicePrefix + number.toString().padStart(company.invoiceNumberPadding, '0')
    }
}

class FinancialYearRepository(private val financialYearDao: FinancialYearDao) {

    fun observeAllForCompany(companyId: Long): Flow<List<FinancialYearEntity>> =
        financialYearDao.observeAllForCompany(companyId)

    fun observeCurrentFinancialYear(companyId: Long): Flow<FinancialYearEntity?> =
        financialYearDao.observeCurrentFinancialYear(companyId)

    suspend fun getCurrentFinancialYear(companyId: Long): FinancialYearEntity? =
        financialYearDao.getCurrentFinancialYear(companyId)

    /**
     * Creates the financial year that contains "today" based on the
     * company's configured start month/day (e.g. "04-01"), and marks it
     * current. Called once automatically right after a company is first
     * created (see CompanySetupViewModel), and can also be called manually
     * from Settings to open a new FY when the current one is closed.
     */
    suspend fun createAndActivateFinancialYearContainingToday(
        companyId: Long,
        startMonthDay: String
    ): FinancialYearEntity {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val (startMonth, startDay) = startMonthDay.split("-").map { it.toInt() }

        var fyStart = LocalDate.of(today.year, startMonth, startDay)
        if (today.isBefore(fyStart)) {
            fyStart = fyStart.minusYears(1)
        }
        val fyEnd = fyStart.plusYears(1).minusDays(1)

        val startMillis = fyStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = fyEnd.atStartOfDay(zone).plusHours(23).plusMinutes(59).plusSeconds(59).toInstant().toEpochMilli()

        val label = DateUtils.financialYearLabel(startMillis, endMillis)
        val entity = FinancialYearEntity(
            companyId = companyId,
            label = label,
            startDateMillis = startMillis,
            endDateMillis = endMillis,
            isCurrent = true
        )
        val id = financialYearDao.insert(entity)
        financialYearDao.setCurrent(companyId, id)
        return entity.copy(financialYearId = id)
    }

    suspend fun findFinancialYearForDate(companyId: Long, dateMillis: Long): FinancialYearEntity? =
        financialYearDao.findFinancialYearForDate(companyId, dateMillis)
}
