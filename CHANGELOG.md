# Changelog

All notable changes to AutoExpense are documented here. Versions map to the
Android `versionName` (`versionCode`) set in `app/build.gradle.kts`.

## 1.1.2 (12) — 2026-08-03

### Fixed
- **Google Sign-In rejected on installed builds** — the app's signing certificate was
  not registered for this package, so Google rejected sign-in with `DEVELOPER_ERROR`
  (status 10). Rebuilt against a Firebase configuration that includes the missing
  fingerprint.

## 1.1.1 (11) — 2026-08-03

### Fixed
- **Backups no longer stranded when you sign in** — if you used the app before
  signing in, your data was saved under an anonymous account; signing in with Google
  then created a *separate* account, leaving that history unreachable on a new device.
  The anonymous account is now linked (upgraded) to your Google account, so the same
  data carries over.

### Changed
- **Google Sign-In failures are now reported** — errors were being swallowed by an
  empty `catch`, so a failed sign-in looked like nothing happening. The specific
  cause is now logged (tag `ExpenseAuth`) and shown on screen, which makes sign-in
  problems in release builds diagnosable.

## 1.1.0 (10) — 2026-07-30

### Added
- **Premium glassmorphism UI** — frosted translucent cards, gradient accents, and a
  refreshed dark/light theme across the app.
- **Interactive feature tour** — a guided spotlight walkthrough covering splitting a
  transaction with friends and changing a category.

### Fixed
- **Valid bank debits were silently dropped** — messages that report the running
  balance (e.g. `Debited Rs:168.00 ... Avl Bal Rs:1055.52`) were rejected by the
  balance-enquiry filter. Balance phrases now only exclude an SMS when it contains no
  debit/credit action, so genuine transactions get through.
- **`Rs:` amount format not recognized** — banks that write `Rs:168.00` with a colon
  (e.g. Union Bank) had no amount extracted, so the transaction was discarded.
- **Upcoming debits logged as completed** — future-tense messages ("Rs 500 *will be*
  debited on 05-Aug") were recorded as actual spends and are now excluded.
- **Payee name missing on some debits** — `Fvg:` (Favouring) now yields the merchant
  name instead of "Unknown".

## 1.0.8 (9) — 2026-07-30

### Added
- **Split with friends** — split any expense into shares (yours + one per friend),
  divided equally by default and editable per person. Tick a friend once they pay you
  back and your recorded spend drops by their share; the original total is preserved.
  Fully offline — no accounts, no sync, no friend install needed.
- **Rename an "Unknown" merchant and auto-categorize** — renaming an uncategorized
  transaction (e.g. "Unknown" → "Swiggy") now also files it under the right category
  in one step.

### Changed
- **Better SMS merchant detection** — new patterns recognize the merchant in ICICI
  debit alerts, Axis card "Spent" messages, and UPI P2M/P2A payments, so fewer
  transactions show as "Unknown". Existing transactions are re-scanned and back-filled.
- **Tax page hidden** — the manual tax calculator was removed from the bottom navigation
  (behind a feature flag; the inline dashboard tax summary is unaffected).
- Spend totals now reflect your share of split expenses everywhere (dashboard, lists,
  daily totals, widget).

## 1.0.7 (8) — 2026-07-28

### Fixed
- **Crash on startup for some users** — resolved a `NullPointerException` in the
  monthly-stats loader (`ExpenseViewModel`) caused by a StateFlow being read
  before initialization.
- **Home-screen widget crash** — upgraded Glance to 1.1.0, fixing the
  "List adapter activity trampoline invoked without specifying target intent"
  crash thrown on some launchers.
- **Google Sign-In crash** — upgraded Google Play Services Auth to 21.2.0,
  fixing a `NullPointerException` in `SignInHubActivity` during sign-in.

### Changed
- `androidx.glance:glance-appwidget` / `glance-material3` 1.0.0 → 1.1.0
- `com.google.android.gms:play-services-auth` 20.7.0 → 21.2.0

## 1.0.6 (7)

### Changed
- Reject promotional/ad SMS that were misread as transactions.
- Expanded merchant keyword database for broader auto-categorization.
- Re-categorize existing "OTHER" transactions on refresh.

## 1.0.5 (6)

### Added
- Calendar date picker and custom date ranges.
- Salary category with passbook/balance exclusion.

### Changed
- Improved categorization, tax calculator, and dashboard/list UI.
- Fixed chip alignment with horizontal scroll.
