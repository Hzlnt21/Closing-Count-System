# Device Testing Report

## Checkpoint

- Version: `0.1.0`
- Date: 18 August 2026
- Device: Xiaomi/Redmi `23122PCD1G`
- Screen resolution: 1220 x 2712
- Orientation: Portrait
- Operation mode: Fully offline

## Automated Verification

The following Gradle tasks completed successfully:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug connectedDebugAndroidTest assembleRelease
```

Coverage includes:

- Ingredient and menu validation.
- Closing calculation for multiple menus and ingredients.
- Updating one closing for the same date.
- Historical menu and recipe snapshots.
- Backup JSON round-trip and invalid reference rejection.
- Transactional database restore.
- Excel workbook structure and both required worksheets.

## Physical Device Flow

The following full flow was completed on the target phone:

1. Created the `Coffee` menu category.
2. Created the `Latte` menu with `Fresh Milk` as its ingredient.
3. Entered two sold Latte items in Daily Closing.
4. Verified the result `Fresh Milk = 2` under `Bahan Baku`.
5. Saved the closing and opened it from History.
6. Verified menu sales and ingredient results in the detail screen.
7. Exported the closing to `.xlsx`.
8. Verified the workbook package, worksheet XML, and the sheets `Ringkasan Bahan` and `Penjualan Menu`.
9. Edited the historical closing from two to three Latte items.
10. Verified that History updated to three sold menu items.
11. Created a full JSON backup through Android's file picker.
12. Restored the backup and verified the five initial ingredient categories and 40 ingredients.

Test master data and generated files were removed after verification by restoring the clean baseline backup.

## Hardening Changes

- Added scrolling to the Home screen for shorter Android displays.
- Added a warning before unsaved closing quantities are discarded when selecting another date.
- Added the same protection when opening another closing from History.
- Confirmed accessibility descriptions for primary action buttons and switches.
- Confirmed database migrations, cold launch, update installation, and offline operation.

## Stable Release Verification

- Generated a dedicated 4096-bit RSA release key stored outside Git tracking.
- Built a signed release APK and verified its APK Signature Scheme v2 certificate.
- Installed a release-signed `0.0.7` test build on the target phone.
- Cold-launched it and confirmed the initial ingredient data was available.
- Updated in place to release-signed `0.1.0` using Android's replace/update flow.
- Confirmed `firstInstallTime` remained unchanged while `lastUpdateTime` advanced.
- Confirmed the application reported `versionCode 8` and `versionName 0.1.0` after update.
- Compared ingredient-screen UI snapshots before and after update; the local ingredient data remained available.
- Confirmed the final application identity is `Closing Count System` with package ID `com.closingcount.app`.

## Result

No critical issue remains in the agreed MVP scope for stable release `0.1.0`.

## v0.1.1 Visual Identity Verification

- Date: 20 August 2026.
- Unit tests, debug APK build, and Android lint completed successfully.
- Signed release APK compiled with `versionCode 9` and `versionName 0.1.1`.
- Release certificate SHA-256 matches the v0.1.0 signing certificate.
- Android resource compilation confirms the EXVE Labs VectorDrawable is valid.
- No database, calculation, export, backup, or restore implementation was changed.
- Physical-device visual inspection is pending because the target device was not connected during this checkpoint.
