# Re-check report

## Architecture checked
- Offline SQLite storage through sqflite.
- Foreign keys enabled and SQLite WAL mode enabled.
- Double-entry voucher header/entry model.
- Sales/purchase posting writes both accounting entries and stock movements in a database transaction.
- GST split supports CGST+SGST for intra-state and IGST for inter-state transactions.
- JSON backup/restore covers all application tables.
- GitHub Actions builds an Android release APK and runs analyze/test before the build.
- PDF invoice printing uses the pdf + printing packages.

## Accounting invariant
Every posted transaction created by the app uses equal debit and credit totals. Sales and purchase invoices calculate tax from the taxable line amount and post the invoice total to the customer/supplier ledger.

## Intentional boundaries
This package does not claim live government submission. E-invoice IRN generation/submission, E-Way Bill API submission, GSTR-1/3B filing, TDS returns, PF/ESIC filings, UPI/bank API synchronization and digital-signature certificates require external credentials, network access and production integrations. The app instead stores GST-ready transaction data offline.

## Build verification limitation
The execution environment used to prepare this archive does not contain the Flutter SDK, so a local `flutter analyze`/APK compilation could not be executed here. The GitHub Actions workflow is designed to perform those checks on a GitHub-hosted Flutter environment before producing the APK.
