CREATE TABLE ccs.catalog_versions (
  id uuid PRIMARY KEY,
  outlet_id uuid NOT NULL REFERENCES ccs.outlets(id),
  revision bigint NOT NULL CHECK (revision BETWEEN 1 AND 9007199254740991),
  snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot)='object'),
  created_by uuid NOT NULL REFERENCES ccs.accounts(id),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (outlet_id,revision),
  UNIQUE (id,outlet_id)
);
CREATE TABLE ccs.closings (
  id uuid PRIMARY KEY,
  outlet_id uuid NOT NULL REFERENCES ccs.outlets(id),
  business_date date NOT NULL,
  state text NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT','SUBMITTED','LOCKED')),
  revision bigint NOT NULL DEFAULT 0 CHECK (revision BETWEEN 0 AND 9007199254740991),
  generation bigint NOT NULL DEFAULT 1 CHECK (generation BETWEEN 1 AND 9007199254740991),
  catalog_version_id uuid NOT NULL,
  snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot)='object'),
  timezone text NOT NULL,
  cutoff_minutes integer NOT NULL CHECK (cutoff_minutes BETWEEN 0 AND 1439),
  created_by uuid NOT NULL REFERENCES ccs.accounts(id),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (outlet_id,business_date),
  UNIQUE (id,outlet_id),
  FOREIGN KEY (catalog_version_id,outlet_id) REFERENCES ccs.catalog_versions(id,outlet_id)
);
CREATE TABLE ccs.closing_entries (
  closing_id uuid NOT NULL REFERENCES ccs.closings(id),
  menu_id uuid NOT NULL,
  quantity integer NOT NULL DEFAULT 0 CHECK (quantity BETWEEN 0 AND 9999),
  revision bigint NOT NULL DEFAULT 0 CHECK (revision BETWEEN 0 AND 9007199254740991),
  updated_by uuid REFERENCES ccs.accounts(id),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (closing_id,menu_id)
);
CREATE TABLE ccs.operation_receipts (
  outlet_id uuid NOT NULL REFERENCES ccs.outlets(id),
  actor_id uuid NOT NULL REFERENCES ccs.accounts(id),
  operation_id uuid NOT NULL,
  fingerprint text NOT NULL CHECK (fingerprint ~ '^[a-f0-9]{64}$'),
  response jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (outlet_id,actor_id,operation_id)
);
CREATE TABLE ccs.closing_audit (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  closing_id uuid NOT NULL,
  outlet_id uuid NOT NULL,
  actor_id uuid NOT NULL REFERENCES ccs.accounts(id),
  actor_username text NOT NULL,
  actor_name text NOT NULL,
  operation_id uuid,
  action text NOT NULL CHECK (action IN ('CREATE','ENTRY_UPDATED','SUBMIT','RETURN','LOCK','REOPEN')),
  menu_id uuid,
  before_quantity integer,
  after_quantity integer,
  entry_revision bigint,
  closing_revision bigint NOT NULL,
  generation bigint NOT NULL,
  reason text,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  FOREIGN KEY (closing_id,outlet_id) REFERENCES ccs.closings(id,outlet_id)
);
CREATE INDEX ON ccs.closing_audit (closing_id,id);
CREATE FUNCTION ccs.prevent_history_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Immutable history cannot be rewritten' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER immutable_catalog BEFORE UPDATE OR DELETE ON ccs.catalog_versions
  FOR EACH ROW EXECUTE FUNCTION ccs.prevent_history_rewrite();
CREATE TRIGGER immutable_closing_audit BEFORE UPDATE OR DELETE ON ccs.closing_audit
  FOR EACH ROW EXECUTE FUNCTION ccs.prevent_history_rewrite();
CREATE TRIGGER immutable_operation_receipts BEFORE UPDATE OR DELETE ON ccs.operation_receipts
  FOR EACH ROW EXECUTE FUNCTION ccs.prevent_history_rewrite();
CREATE FUNCTION ccs.preserve_closing_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.outlet_id IS DISTINCT FROM NEW.outlet_id OR OLD.business_date IS DISTINCT FROM NEW.business_date
    OR OLD.snapshot IS DISTINCT FROM NEW.snapshot OR OLD.catalog_version_id IS DISTINCT FROM NEW.catalog_version_id
    OR OLD.timezone IS DISTINCT FROM NEW.timezone OR OLD.cutoff_minutes IS DISTINCT FROM NEW.cutoff_minutes THEN
    RAISE EXCEPTION 'Closing snapshot cannot be rewritten' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_closing_snapshot BEFORE UPDATE ON ccs.closings
  FOR EACH ROW EXECUTE FUNCTION ccs.preserve_closing_snapshot();
