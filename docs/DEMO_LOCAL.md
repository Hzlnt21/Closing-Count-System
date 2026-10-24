# Closing Count local demo

The service/control-plane foundation is verified locally. The complete Android demo journey, relocation test, media and distributable package are still being prepared under CCS08. This is not a public deployment or physical-device acceptance. Use fictional data only.

## Prerequisites and first start

Windows with Node24, Docker Desktop's local Linux engine, and the local `postgres:16` image. The service dependencies are locked in `service/package-lock.json`. From the extracted/source root:

```text
npm ci --prefix service
docker pull postgres:16
node demo/runtime.mjs init
node demo/runtime.mjs start
node demo/runtime.mjs status
```

`init` creates/reuses only the separately labelled demo container/volume, restricted database and private instance. It seeds four fictional accounts: Rani and Dimas as employees, Arif as supervisor, and Dewi as administrator. The shared catalog contains Cappuccino and Latte. The generation's business-date anchor uses Asia/Jakarta with a04:00 cutoff. Initial current-day quantities are3/5 (8portions); the preceding day's locked report contains6/4 (10portions). Both employees' authorship and supervisor approval are produced through the real service API, rather than fabricated audit rows.

The API binds to `http://127.0.0.1:4372`; its dedicated PostgreSQL database binds to127.0.0.1:55437. The laptop QA target remains separate on55436. The profile refuses remote database hosts, other database/role names, mismatched markers, unknown containers/volumes and an occupied foreign API port. It does not create a cloud account or public listener.

Generated credentials and runtime control files live outside the package, under LOCALAPPDATA/ClosingCountDemo by default. To choose another private absolute directory, append the same `--state-dir ABSOLUTE_PRIVATE_DIRECTORY` to every command. A state directory inside the source/package, including linked descendants, is refused. Keep this directory private and retain it across restarts; it identifies the owned database and contains its credentials. Do not put it into the demo ZIP, screenshots, Git or an operational backup.

To view the local fictional login credentials intentionally:

```text
node demo/runtime.mjs access
```

This is the only command that prints account passwords. Do not use it while recording evidence/media. Android emulator access is intended to use `http://10.0.2.2:4372`; retesting the complete native workflow with this DEMO profile remains pending. The existing local Android debug build is not a final public Demo APK.

## Stop, restart and reset

```text
node demo/runtime.mjs stop
node demo/runtime.mjs start
node demo/runtime.mjs seed
node demo/runtime.mjs reset
node demo/runtime.mjs access
```

Stop closes only the matched owned child and labelled demo database. It retains the volume and history. Repeated start/stop is idempotent. Start preserves the active fixture rather than implicitly resetting it. Repeated seed also preserves a completed fixture and any later edits.

Reset creates a new logical generation with fresh outlet/account/menu/operation IDs and credentials. In one control-plane transaction it archives the former generation, deactivates its outlet/accounts and revokes its sessions. Catalog versions, receipts, audit, reports and existing device files are retained. The new generation is marked ready only after the ordinary API creates its baseline; an interrupted baseline can be resumed with `seed`, using its saved operation IDs without duplicate audit. Reset does not truncate tables, disable immutable triggers, remove a volume or rewrite historical authorship.

After reset, old credentials/sessions cannot use the archived outlet. Sign out and use the new accounts for the new generation. A server reset cannot clear Android drafts: export/review any old pending work in its original account/outlet context; it is never relabelled as new-generation work. Native evidence for this handover is still required before CCS08 is declared complete.

## Troubleshooting and limits

Docker must be running on a local context. Missing private identity, an unexpected marker/resource, linked state path or occupied port causes refusal; the runtime never adopts another process or database. A redacted server log and internal-error event IDs are stored in the private directory. A live controller lock refuses another command; a dead controller PID's lock can be recovered without deleting source/data. An unresponsive owned child receives a private stop request, rather than killing a guessed or reused PID. Startup failure/recovery permutations beyond the linked foundation evidence remain to be verified for the final package.

There is no unauthenticated reset endpoint. Reset is a local operator command. Normal service limits remain: per-menu integer0–9999, bounded requests, active membership/roles, explicit revision conflicts, session expiry and real login throttling. No accelerated limiter clock is used in this demo. Sessions and credentials are not restored by pending-data import; legacy local history remains separate from shared server history.

The manifest defines logical values, names and roles. UUIDs, generated credentials, timestamps and the business-date anchor vary at generation creation and then persist. Deterministic logical state is not byte-identical database storage. Physical HP, real network behavior, cloud provider, HTTPS/APK signing/distribution, public quotas/reset scheduling and portfolio claims remain later program gates.

[Verified foundation and remaining acceptance](DEMO_FOUNDATION_R1.md), [local transfer acceptance](TRANSFER_LOCAL_ACCEPTANCE_R1.md), [manifest](../demo/manifest.v1.json).
