ALTER TABLE ccs.outlets ADD COLUMN settings_revision bigint NOT NULL DEFAULT 0 CHECK (settings_revision >= 0);
CREATE TABLE ccs.outlet_settings_audit (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  outlet_id uuid NOT NULL REFERENCES ccs.outlets(id),
  actor_id uuid NOT NULL REFERENCES ccs.accounts(id),
  operation_id uuid NOT NULL,
  revision bigint NOT NULL CHECK (revision > 0),
  before_value jsonb NOT NULL,
  after_value jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (outlet_id,revision)
);
CREATE TRIGGER immutable_outlet_settings_audit BEFORE UPDATE OR DELETE ON ccs.outlet_settings_audit
  FOR EACH ROW EXECUTE FUNCTION ccs.prevent_history_rewrite();
