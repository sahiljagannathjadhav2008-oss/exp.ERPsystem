package com.builtdifferent.erp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.builtdifferent.erp.di.AppContainer
import com.builtdifferent.erp.di.ViewModelFactory
import com.builtdifferent.erp.ui.screens.companysetup.CompanySetupScreen
import com.builtdifferent.erp.ui.screens.companysetup.CompanySetupViewModel
import com.builtdifferent.erp.ui.screens.dashboard.DashboardScreen
import com.builtdifferent.erp.ui.screens.dashboard.DashboardViewModel
import com.builtdifferent.erp.ui.screens.party.PartyEditScreen
import com.builtdifferent.erp.ui.screens.party.PartyEditViewModel
import com.builtdifferent.erp.ui.screens.party.PartyListScreen
import com.builtdifferent.erp.ui.screens.party.PartyListViewModel
import com.builtdifferent.erp.ui.screens.product.ProductEditScreen
import com.builtdifferent.erp.ui.screens.product.ProductEditViewModel
import com.builtdifferent.erp.ui.screens.product.ProductListScreen
import com.builtdifferent.erp.ui.screens.product.ProductListViewModel
import com.builtdifferent.erp.ui.screens.purchase.NewPurchaseInvoiceScreen
import com.builtdifferent.erp.ui.screens.purchase.NewPurchaseInvoiceViewModel
import com.builtdifferent.erp.ui.screens.sales.NewSalesInvoiceScreen
import com.builtdifferent.erp.ui.screens.sales.NewSalesInvoiceViewModel
import com.builtdifferent.erp.ui.screens.sales.RecordPaymentViewModel
import com.builtdifferent.erp.ui.screens.sales.SalesInvoiceDetailScreen
import com.builtdifferent.erp.ui.screens.sales.SalesInvoiceDetailViewModel
import com.builtdifferent.erp.ui.screens.sales.SalesInvoiceListScreen
import com.builtdifferent.erp.ui.screens.sales.SalesInvoiceListViewModel

object ErpDestinations {
    const val COMPANY_SETUP = "company_setup"
    const val DASHBOARD = "dashboard"
    const val PARTY_LIST = "party_list"
    const val PARTY_EDIT = "party_edit/{partyId}"
    const val PRODUCT_LIST = "product_list"
    const val PRODUCT_EDIT = "product_edit/{productId}"
    const val SALES_INVOICE_LIST = "sales_invoice_list"
    const val NEW_SALES_INVOICE = "new_sales_invoice"
    const val SALES_INVOICE_DETAIL = "sales_invoice_detail/{invoiceId}"
    const val NEW_PURCHASE_INVOICE = "new_purchase_invoice"

    fun partyEdit(partyId: Long) = "party_edit/$partyId"
    fun productEdit(productId: Long) = "product_edit/$productId"
    fun salesInvoiceDetail(invoiceId: Long) = "sales_invoice_detail/$invoiceId"
}

@Composable
fun ErpNavGraph(
    container: AppContainer,
    startDestination: String,
    navController: NavHostController = rememberNavController()
) {
    val factory = ViewModelFactory(container)

    NavHost(navController = navController, startDestination = startDestination) {

        composable(ErpDestinations.COMPANY_SETUP) {
            val vm: CompanySetupViewModel = viewModel(factory = factory)
            CompanySetupScreen(
                viewModel = vm,
                onSaved = {
                    navController.navigate(ErpDestinations.DASHBOARD) {
                        popUpTo(ErpDestinations.COMPANY_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(ErpDestinations.DASHBOARD) {
            val vm: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = vm,
                onOpenParties = { navController.navigate(ErpDestinations.PARTY_LIST) },
                onOpenProducts = { navController.navigate(ErpDestinations.PRODUCT_LIST) },
                onOpenSalesInvoices = { navController.navigate(ErpDestinations.SALES_INVOICE_LIST) },
                onOpenNewPurchaseInvoice = { navController.navigate(ErpDestinations.NEW_PURCHASE_INVOICE) },
                onOpenCompanySettings = { navController.navigate(ErpDestinations.COMPANY_SETUP) }
            )
        }

        composable(ErpDestinations.PARTY_LIST) {
            val vm: PartyListViewModel = viewModel(factory = factory)
            PartyListScreen(
                viewModel = vm,
                onAddParty = { navController.navigate(ErpDestinations.partyEdit(0)) },
                onOpenParty = { id -> navController.navigate(ErpDestinations.partyEdit(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ErpDestinations.PARTY_EDIT,
            arguments = listOf(navArgument("partyId") { type = NavType.LongType })
        ) { backStackEntry ->
            val partyId = backStackEntry.arguments?.getLong("partyId") ?: 0L
            val vm: PartyEditViewModel = viewModel(factory = factory)
            PartyEditScreen(
                viewModel = vm,
                partyId = partyId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ErpDestinations.PRODUCT_LIST) {
            val vm: ProductListViewModel = viewModel(factory = factory)
            ProductListScreen(
                viewModel = vm,
                onAddProduct = { navController.navigate(ErpDestinations.productEdit(0)) },
                onOpenProduct = { id -> navController.navigate(ErpDestinations.productEdit(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ErpDestinations.PRODUCT_EDIT,
            arguments = listOf(navArgument("productId") { type = NavType.LongType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            val vm: ProductEditViewModel = viewModel(factory = factory)
            ProductEditScreen(
                viewModel = vm,
                productId = productId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ErpDestinations.SALES_INVOICE_LIST) {
            val vm: SalesInvoiceListViewModel = viewModel(factory = factory)
            SalesInvoiceListScreen(
                viewModel = vm,
                onNewInvoice = { navController.navigate(ErpDestinations.NEW_SALES_INVOICE) },
                onOpenInvoice = { id -> navController.navigate(ErpDestinations.salesInvoiceDetail(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ErpDestinations.SALES_INVOICE_DETAIL,
            arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
        ) { backStackEntry ->
            val invoiceId = backStackEntry.arguments?.getLong("invoiceId") ?: 0L
            val vm: SalesInvoiceDetailViewModel = viewModel(factory = factory)
            val paymentVm: RecordPaymentViewModel = viewModel(factory = factory)
            SalesInvoiceDetailScreen(viewModel = vm, paymentViewModel = paymentVm, invoiceId = invoiceId, onBack = { navController.popBackStack() })
        }

        composable(ErpDestinations.NEW_PURCHASE_INVOICE) {
            val vm: NewPurchaseInvoiceViewModel = viewModel(factory = factory)
            val productListVm: ProductListViewModel = viewModel(factory = factory)
            NewPurchaseInvoiceScreen(
                viewModel = vm,
                productListViewModel = productListVm,
                onPosted = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ErpDestinations.NEW_SALES_INVOICE) {
            val vm: NewSalesInvoiceViewModel = viewModel(factory = factory)
            val productListVm: ProductListViewModel = viewModel(factory = factory)
            NewSalesInvoiceScreen(
                viewModel = vm,
                productListViewModel = productListVm,
                onPosted = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
