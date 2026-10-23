# Native layout R1

23 October 2026. Changes preserve the approved R2 blue/white direction while adapting the connected Android screens to limited space and large text. All six configurations passed ten selected native scene checks each on the same final app/test APK pair. The separate detector control test and three native regressions (save/conflict, outlet settings and catalog/recipe) also passed.

## Changes

- On short viewports or text at least 150%, a labelled page selector replaces the five narrow bottom tabs. All five destinations and the unsaved-change guard remain available. Compact chrome omits the repeated outlet subtitle and account shortcut; the Account destination remains in navigation.
- Long outlet names and large text stack the outlet/date controls. A two-line outlet label explicitly ellipsizes; its selector retains the full name. Closing summary labels and totals stack for larger text.
- Quantity inputs allow four digits at enlarged text sizes. On narrow content with text at least 130%, the full menu name appears above the quantity field, avoiding a squeezed column with broken words.
- Catalog section chips have equal widths. Settings review and the unsaved-change explanation can scroll inside their dialogs; action buttons remain outside those scrollable bodies.

## Verification method

| Owned emulator configuration, dp | Theme | Font scale |
| --- | --- | --- |
| Phone 360 x 800 | Light | 100% |
| Phone 360 x 800 | Dark | 200% |
| Phone 800 x 360 | Light | 130% |
| Tablet 800 x 1280 | Light | 100% |
| Tablet 800 x 1280 | Dark | 200% |
| Tablet 1280 x 800 | Dark | 130% |

The opt-in native test records the actual runtime configuration and navigates login, overview, quantity input, unsaved-change confirmation, review, history, account, settings preview, team form and catalog form. It uses long dummy outlet/menu/ingredient names, enters 9999, then discards that input. It previews and cancels administrative forms without publishing from the UI. Seven screenshots per configuration are available in the [review gallery](layout-review-r1.html).

All six final contact sheets were visually inspected, with additional focused inspection of the phone 200% input and original clipped-dialog screenshots. This is not full-resolution inspection of every image. [Archived evidence](layout-evidence-r1/) includes the 42 gallery screenshots with hashes, actual runtime configurations/restoration, logs, APK fingerprints, independent database proof, build/lint results and the local host harness.

Static-text checks inspect measured painted line bounds. The detector has a separate control test: fitting text passes; intentionally clipped and ellipsized labels are detected. Some Compose paragraph containers report overflow despite painted text fitting; these are counted separately. Only intentional outlet-label ellipses are exempted. Editable text and clipping at legitimate scroll viewports are not covered by that detector. Settings and unsaved-message tests scroll their explanation to the end when necessary.

Each case uses a newly seeded, retained outlet in the guarded local QA database. Setup publishes the long-name catalog and outlet settings through real APIs. Independent PostgreSQL checks distinguish those two setup receipts from UI writes, confirm unchanged zero quantities and draft closing, and check that no account creation or report occurred. The harness verifies and restores the emulator size, density, font and night-mode settings after each case. It does not access a physical phone.

## Findings retained

Initial test setup returned a Boolean instead of Unit; that JUnit harness error was corrected. The first raw overflow detector incorrectly treated paragraph-container width as painted text overflow; painted-line bounds and deliberate detector controls replaced it. Corrected checks found actual clipped settings-review and landscape unsaved-change text, now scrollable. Screenshot inspection also found the narrow large-font menu-name column; it now stacks above quantity. A tablet test initially waited for a lazy-list revision label outside the composed viewport; it now scrolls to the revision before asserting its content. These failed attempts remain in program evidence and are not counted as final passing cases.

## Scope limits

The final source build and 19 JVM tests passed. Lint reports 0 errors and 17 warnings: the prior 16 plus Compose's recommendation to use window-container size rather than configuration height for compact chrome. The tested configurations assert actual runtime dimensions; split-screen/multiwindow-specific sizing is not part of this matrix. The recommendation is recorded without suppression or dependency changes.

This is selected responsive/dark/font-scale coverage, not a complete accessibility audit, automated contrast proof, every dialog/error state, physical-device acceptance or public demo. Scrollable screens do not show all content in one screenshot. Existing service, SQL, Room schema and dependency versions are preserved; their earlier broader results are inherited, not newly rerun here. Android remains 0.1.1/versionCode9, Room8 and service0.2.0-dev.5.

Legacy/online transfer safety and complete dummy demo packaging remain open. The eight-project program continues through combined phone UAT, reviewed hosting/budget, public demos and final portfolio/CV review.
