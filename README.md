# Offline ERP

A single-company, offline-first Flutter Android ERP for Indian SMEs. Data stays on-device in SQLite.

## Included

- Company setup and fiscal year
- Double-entry accounting: groups, ledgers, journal/payment/receipt/contra
- Sales invoices and purchase bills with CGST/SGST/IGST
- Inventory, units, categories, warehouses, stock movements
- Customers and suppliers
- Banking and reconciliation records
- Employees and payroll records
- Day Book, Ledger, Trial Balance, Profit & Loss, Balance Sheet, Stock Summary
- Invoice PDF/print
- JSON backup and    restore
- Audit log
- Dashboard and quick actions
- GitHub Actions release APK build

## Build

Push this repository to GitHub. Open **Actions → Build Offline ERP APK → Run workflow** (or push to `main`). The workflow creates the Android host project, runs `flutter analyze` and `flutter test`, then builds `app-release.apk`.

## Important

This is a working offline ERP foundation, not a legal/tax certification. GST e-invoice/e-way bill network submission, GSTR filing, TDS, payroll statutory filings, and bank APIs require government/bank credentials and live integrations and are intentionally not faked in an offline app.
