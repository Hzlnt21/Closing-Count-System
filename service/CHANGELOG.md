# Service changelog

## 0.2.0-dev.1 — CCS-02A

- First owned PostgreSQL schema/migration for accounts, outlets, memberships, sessions and administrator audit.
- Real HTTP login/logout/current identity, outlet membership gates, administrator account/membership management and server business date.
- Bounded hashed sessions, login throttling, strict payloads, database ownership refusal and last-admin transaction locking.
- Seven unit tests and fifteen real HTTP/PostgreSQL scenarios (Node reports sixteen including the parent suite); baseline Android source is unchanged. Detailed final evidence is kept in the program checkpoint.
- This is local service foundation. Collaborative closing/catalog/entry schema, sync/lifecycle, Room migrations/outbox, native integration, UAT and public demo are not implemented by this checkpoint.
