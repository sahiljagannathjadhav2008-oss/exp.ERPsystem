package com.builtdifferent.erp.di

import android.content.Context
import androidx.room.Room
import com.builtdifferent.erp.data.local.AppDatabase
import com.builtdifferent.erp.data.local.migration.AppDatabaseMigrations
import com.builtdifferent.erp.data.repository.AccountingRepository
import com.builtdifferent.erp.data.repository.BackupRepository
import com.builtdifferent.erp.data.repository.BankAccountRepository
import com.builtdifferent.erp.data.repository.BankBookRepository
import com.builtdifferent.erp.data.repository.CashBookRepository
import com.builtdifferent.erp.data.repository.CompanyRepository
import com.builtdifferent.erp.data.repository.DatabaseSeeder
import com.builtdifferent.erp.data.repository.DataExportRepository
import com.builtdifferent.erp.data.repository.DayBookRepository
import com.builtdifferent.erp.data.repository.ExpenseRepository
import com.builtdifferent.erp.data.repository.FinancialStatementRepository
import com.builtdifferent.erp.data.repository.FinancialYearRepository
import com.builtdifferent.erp.data.repository.GstReportRepository
import com.builtdifferent.erp.data.repository.HsnSacRepository
import com.builtdifferent.erp.data.repository.IncomeRepository
import com.builtdifferent.erp.data.repository.InventoryReportRepository
import com.builtdifferent.erp.data.repository.InventoryRepository
import com.builtdifferent.erp.data.repository.PartyRepository
import com.builtdifferent.erp.data.repository.ProductCategoryRepository
import com.builtdifferent.erp.data.repository.ProductRepository
import com.builtdifferent.erp.data.repository.PurchaseInvoiceRepository
import com.builtdifferent.erp.data.repository.PurchaseOrderRepository
import com.builtdifferent.erp.data.repository.PurchaseReturnRepository
import com.builtdifferent.erp.data.repository.PaymentRepository
import com.builtdifferent.erp.data.repository.ReceivablesPayablesRepository
import com.builtdifferent.erp.data.repository.SalesInvoiceRepository
import com.builtdifferent.erp.data.repository.SalesOrderRepository
import com.builtdifferent.erp.data.repository.SalesQuotationRepository
import com.builtdifferent.erp.data.repository.SalesReturnRepository
import com.builtdifferent.erp.data.repository.DeliveryChallanRepository
import com.builtdifferent.erp.data.repository.UnitRepository
import com.builtdifferent.erp.data.repository.WarehouseRepository

/**
 * Deliberately plain manual dependency injection (no Hilt/Koin) — this
 * project has no other reason to pull in an annotation-processing DI
 * framework, and a single container class is easy to reason about for an
 * app this shape. One instance lives on ErpApplication for the process
 * lifetime.
 */
class AppContainer(context: Context) {

    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        // Intentionally NOT using fallbackToDestructiveMigration(): see
        // AppDatabaseMigrations for why silently wiping user data on a
        // schema change is never acceptable for this app.
        .addMigrations(*AppDatabaseMigrations.ALL)
        .build()

    val companyRepository = CompanyRepository(database.companyDao())
    val financialYearRepository = FinancialYearRepository(database.financialYearDao())
    val partyRepository = PartyRepository(database)
    val unitRepository = UnitRepository(database.unitDao())
    val productCategoryRepository = ProductCategoryRepository(database.productCategoryDao())
    val hsnSacRepository = HsnSacRepository(database.hsnSacDao(), database.gstRateDao())
    val warehouseRepository = WarehouseRepository(database)
    val productRepository = ProductRepository(
        db = database,
        warehouseRepository = warehouseRepository,
        currentFinancialYearId = {
            companyRepository.getActiveCompany()?.let { company ->
                financialYearRepository.getCurrentFinancialYear(company.companyId)?.financialYearId
            }
        }
    )

    private val databaseSeeder = DatabaseSeeder(database.ledgerAccountDao(), database.unitDao())

    val inventoryRepository = InventoryRepository(database)
    val accountingRepository = AccountingRepository(database)
    val salesInvoiceRepository = SalesInvoiceRepository(database, inventoryRepository, accountingRepository)
    val purchaseInvoiceRepository = PurchaseInvoiceRepository(database, inventoryRepository, accountingRepository)
    val paymentRepository = PaymentRepository(database, accountingRepository)
    val salesReturnRepository = SalesReturnRepository(database, inventoryRepository, accountingRepository)
    val purchaseReturnRepository = PurchaseReturnRepository(database, inventoryRepository, accountingRepository)
    val salesQuotationRepository = SalesQuotationRepository(database)
    val salesOrderRepository = SalesOrderRepository(database)
    val purchaseOrderRepository = PurchaseOrderRepository(database)
    val deliveryChallanRepository = DeliveryChallanRepository(database, inventoryRepository)
    val expenseRepository = ExpenseRepository(database, accountingRepository)
    val incomeRepository = IncomeRepository(database, accountingRepository)
    val bankAccountRepository = BankAccountRepository(database)
    val receivablesPayablesRepository = ReceivablesPayablesRepository(database)
    val inventoryReportRepository = InventoryReportRepository(database)
    val gstReportRepository = GstReportRepository(database)
    val financialStatementRepository = FinancialStatementRepository(database)
    val dayBookRepository = DayBookRepository(database)
    val cashBookRepository = CashBookRepository(database)
    val bankBookRepository = BankBookRepository(database)
    val backupRepository = BackupRepository(context.applicationContext, database)
    val dataExportRepository = DataExportRepository(database, partyRepository)

    suspend fun seedIfNeeded() = databaseSeeder.seedIfNeeded()
}
