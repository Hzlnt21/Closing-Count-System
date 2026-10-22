# Changelog

## Unreleased — CCS-05 Room foundation, 2026-10-22

- Added non-destructive schema6→7 online namespace and scoped durable operations with transactional edit/ack/pull/conflict/auth recovery.
- Added14 real Room instrumentation tests;18 including legacy backup/menu/closing regressions pass on the owned emulator. Fixture context/index parsing issues were corrected before acceptance.
- Preserved Android version metadata0.1.1 and existing offline UI. This is a source foundation checkpoint, not connected online feature availability, a release or phone UAT.
- Recorded approved FINORA-inspired visual R2 separately; Compose application remains next.

## 0.1.1 - 2026-08-20

Visual identity update without changes to the closing workflow or stored data.

### Changed

- Replaced the coffee-brown color scheme with an EXVE-inspired blue palette for light and dark themes.
- Added an About dialog accessible from the information icon in the top app bar.
- Added automatic app version information, Android APK/offline mode, and developer attribution.
- Added the EXVE Labs color logo and copyright attribution.
- Finalized the visible application name as Closing Count System.

## 0.1.0 - 2026-08-18

First stable MVP release.

### Included

- Offline ingredient categories and ingredient management.
- Menu categories, menus, and ingredient recipes.
- One editable daily closing per date.
- Automatic Terjual/Out calculation grouped by ingredient category.
- Closing history with stable menu and recipe snapshots.
- Excel export with ingredient summary and menu sales sheets.
- Full JSON backup and transactional restore.
- Validation, empty states, confirmations, and unsaved-closing protection.
- Dedicated release signing and verified in-place Android update path.

### Platform

- Android 8.0 or newer (`minSdk 26`).
- Portrait-first Android interface.
- Fully offline; no account or internet connection required.
