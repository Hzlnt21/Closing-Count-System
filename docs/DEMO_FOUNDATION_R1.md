# Local DEMO foundation R1

Service0.2.0-dev.7 adds an explicit DEMO profile on127.0.0.1:55437/closing_count_portfolio_demo, distinct from QA55436. Both require the fixed restricted application role, PG16 and the matching private marker. Ownership configuration is revalidated before identity queries. The service health endpoint now identifies its explicit local environment; the default remains QA. Domain SQL migrations, Android source, Room schema, sync/recovery engine and workflow contracts are unchanged from `ccs-transfer-local-r1`.

The root `demo/runtime.mjs` entry point uses relative source/module paths, private external state, a labelled Docker container/volume, exact bindings/mounts/resource caps, a restricted app role and ownership validation. Commands implement init, seed, start, status, stop, reset and explicit credential access. The detached server identifies its run with a noncredential run UUID; stop requests use the matched private control file and the child stops itself. No PID kill, remote target fallback, public reset endpoint or volume removal is provided.

The versioned manifest defines two employees, supervisor and admin, two menu recipes, an8-portion draft and a10-portion historical locked report. Bootstrap creates only the owned accounts/outlet/catalog. Ordinary authenticated HTTP operations create quantities, attribution, submission and the immutable report. Generated seed operation IDs persist so retry can reconcile existing receipts. Reset serializes on the owned outlet, archives its generation and revokes its accounts/sessions before activating a new one. Mutable control flags change; immutable catalog/receipt/audit/report data remain untouched. Device drafts are never accessed by the operator runtime.

## Evidence scope

- Thirty-one service unit tests pass, including profile separation, refusal before a forged configuration can query a database, marker/role/database checks, DEMO/QA health, Docker identity/mount/binding checks, source/linked private-path refusal and live/foreign controller-lock refusal.
- The complete existing real HTTP/PostgreSQL integration command passes80Node tests, including parent tests. These run in fresh retained namespaces on the existing owned QA database; the global QA wrapper now includes operation-status.test.mjs, matching the source package command.
- Nine local DEMO runtime scenarios pass against real Docker/PostgreSQL and the normal API: independent baseline, repeated init/seed, receipt replay, duplicate start, two stops, foreign API listener refusal before DB startup, restart with prior session/data, generation reset/access revocation and repeated seed after reset. Independent queries verify quantities/actors, revisions,6receipts/8audit rows/1report per generation, ingredient formula and preserved archived immutable hashes.
- After the nine scenarios, container checks were tightened to require the observed resource caps and exactly two mounts. Lock records also gained an explicit format/version so unrecognized lock files cannot be removed as stale. Final31units, actual start/seed/status/stop resource recheck and a current controller/status recheck pass. Baseline/reset implementation is unchanged by that tightening; the earlier nine scenarios are not described as nine new reruns.

Logs and sanitized proofs are linked in the checkpoint evidence. The raw developer private state, passwords, session tokens and database files are excluded. Source checkpoint/remote verification are recorded separately after preflight. Existing native/controller/process/Room/visual evidence remains inherited from the preceding checkpoints; no Android build or native DEMO acceptance is claimed here.

[Runtime proof](demo-foundation-evidence-r1/runtime-proof.json), [runtime log](demo-foundation-evidence-r1/runtime.log), [unit log](demo-foundation-evidence-r1/unit.log), [HTTP/PostgreSQL regression](demo-foundation-evidence-r1/integration.log), [final resource check](demo-foundation-evidence-r1/final-resource-guard-recheck.log), [final controller check](demo-foundation-evidence-r1/final-controller-lock-recheck.log).

## Remaining CCS08 acceptance

- Relocate/extract the sanitized package and run the documented commands without developer paths/secrets or the QA service.
- Exercise startup-failure/child cleanup and incomplete-seed recovery with explicit bounded faults; validate the actual operator behavior.
- Run one complete native Room/API/PostgreSQL demo with two isolated employee installations/accounts and supervisor: same/different-item edits, explicit conflict, held offline draft/retry, submit/review/lock, report/export, reopen and historical snapshot consistency. Validate server reset leaves old Android drafts available under their original scope while old server access is refused.
- Finalize the demo guide/scenario cards, readable screenshots/video, APK metadata/hash and sanitized distributable package. Review this actual DEMO profile natively; local debug endpoints are not public HTTPS/APK acceptance.
- Push the complete tested CCS08 checkpoint on the existing repository when all applicable local demo gates pass.

CCS08, the whole Closing Count candidate, physical PF03 and the eight-project program are not complete at this foundation checkpoint. Public deployment/budget and portfolio/CV finalization remain separate. Source rollback is not database downgrade.
