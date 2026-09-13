package com.builtdifferent.erp.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.builtdifferent.erp.ui.screens.companysetup.CompanySetupViewModel
import com.builtdifferent.erp.ui.screens.dashboard.DashboardViewModel
import com.builtdifferent.erp.ui.screens.party.PartyEditViewModel
import com.builtdifferent.erp.ui.screens.party.PartyListViewModel
import com.builtdifferent.erp.ui.screens.product.ProductEditViewModel
import com.builtdifferent.erp.ui.screens.product.ProductListViewModel
import com.builtdifferent.erp.ui.screens.purchase.NewPurchaseInvoiceViewModel
import com.builtdifferent.erp.ui.screens.sales.NewSalesInvoiceViewModel
import com.builtdifferent.erp.ui.screens.sales.RecordPaymentViewModel
import com.builtdifferent.erp.ui.screens.sales.SalesInvoiceDetailViewModel
import com.builtdifferent.erp.ui.screens.sales.SalesInvoiceListViewModel

/**
 * One small factory covering every ViewModel in the app. With a handful of
 * screens this reads more clearly than per-screen factory boilerplate or
 * pulling in Hilt purely for view model injection; it can be split up if
 * the screen count grows large enough to make that worthwhile.
 */
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when (modelClass) {
            CompanySetupViewModel::class.java -> CompanySetupViewModel(
                container.companyRepository, container.financialYearRepository
            ) as T

            DashboardViewModel::class.java -> DashboardViewModel(
                container.companyRepository, container.financialYearRepository, container.partyRepository, container.productRepository, container.database
            ) as T

            PartyListViewModel::class.java -> PartyListViewModel(container.partyRepository) as T

            PartyEditViewModel::class.java -> PartyEditViewModel(container.partyRepository) as T

            ProductListViewModel::class.java -> ProductListViewModel(container.productRepository) as T

            ProductEditViewModel::class.java -> ProductEditViewModel(
                container.productRepository,
                container.unitRepository,
                container.productCategoryRepository,
                container.hsnSacRepository
            ) as T

            NewSalesInvoiceViewModel::class.java -> NewSalesInvoiceViewModel(
                container.companyRepository,
                container.financialYearRepository,
                container.partyRepository,
                container.productRepository,
                container.hsnSacRepository,
                container.warehouseRepository,
                container.salesInvoiceRepository
            ) as T

            SalesInvoiceListViewModel::class.java -> SalesInvoiceListViewModel(
                container.salesInvoiceRepository, container.companyRepository, container.financialYearRepository
            ) as T

            SalesInvoiceDetailViewModel::class.java -> SalesInvoiceDetailViewModel(
                container.salesInvoiceRepository
            ) as T

            NewPurchaseInvoiceViewModel::class.java -> NewPurchaseInvoiceViewModel(
                container.companyRepository,
                container.financialYearRepository,
                container.partyRepository,
                container.hsnSacRepository,
                container.warehouseRepository,
                container.purchaseInvoiceRepository
            ) as T

            RecordPaymentViewModel::class.java -> RecordPaymentViewModel(
                container.paymentRepository,
                container.companyRepository,
                container.financialYearRepository,
                container.partyRepository
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
