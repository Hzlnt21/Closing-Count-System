# Locked online report export R1

23 October 2026. A locked historical report can be downloaded as an Excel workbook from its report page. The export uses the selected immutable generation, including its catalog, recipes, quantities, ingredient totals, reviewer and entry attribution. Reopening or editing today's closing does not change the selected report. The current outlet name is explicitly labelled **Outlet saat ekspor**; it is not presented as a historical name.

The two tabs are **Pemakaian Bahan** and **Penjualan Menu**. Quantities and supported Excel dates are numeric cells, the menu total has a cached SUM, and wide generation/revision identifiers remain literal text. Names resembling formulas stay literal strings. The file contains no endpoint or session token, external workbook links, macros, or arbitrary user-controlled formulas. Blue headers, wrapped labels, frozen headings, filters, print settings and report/closing/outlet identities support reading and tracing the report.

## Access and native save flow

Preparing the file captures the selected report and exact account session. The system save dialog is claimed once by an Activity-scoped ViewModel. Returning from that dialog triggers fresh identity, report and outlet access checks before the application opens the output stream. The newly fetched immutable generation must equal the reviewed model. Account replacement, expired/revoked access and changed report identity stop the write. The repository session mutex prevents a local account switch during the authorized write.

Cancellation changes no closing data. Processing, success, failure and reauthentication have explicit UI states. Activity recreation retains the picker request; the test verifies a different Activity instance after the save and a single claimed request. This is not process-death recovery: a terminated process needs a new report/download action. Authorization reflects successful server checks immediately before the write; it does not promise revocation can interrupt bytes already being written.

Android's document provider can create an empty placeholder when the user chooses Save. If access then fails, the application writes no report bytes; it does not silently delete the provider's file. Tests distinguish that empty placeholder from a successfully exported workbook. Cloud providers and physical devices remain PF-03 UAT work.

Returning to a recreated app window reapplies edge-to-edge system-bar appearance, preserving light/dark icon contrast after the external picker.

## Verification

Current results, APK hashes, exact exported files, independent cell/database checks and screenshots are recorded in [report-export-evidence-r1](report-export-evidence-r1/). The [review gallery](report-export-review-r1.html) links actual native screenshots and read-only renders of the application's workbook. Verification is scoped to the owned emulator, isolated local HTTP fixture and owned PostgreSQL QA database. See the evidence README for final results and superseded attempts.

App metadata remains 0.1.1/versionCode 9; service 0.2.0-dev.5, Room 8 and dependency versions remain unchanged. This is a source checkpoint, not the final local demo release. Online import safety and CCS-08 complete dummy demo packaging remain open. No phone, operational database, hosting purchase, deployment or main-branch promotion is part of this checkpoint.
