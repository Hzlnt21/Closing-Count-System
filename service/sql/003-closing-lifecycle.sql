ALTER TABLE ccs.closings
  ADD COLUMN submitted_by uuid REFERENCES ccs.accounts(id),
  ADD COLUMN submitted_at timestamptz,
  ADD COLUMN submitted_actor jsonb,
  ADD COLUMN locked_by uuid REFERENCES ccs.accounts(id),
  ADD COLUMN locked_at timestamptz,
  ADD COLUMN locked_actor jsonb;
ALTER TABLE ccs.closings ADD CONSTRAINT closing_lifecycle_metadata CHECK (
  (state='DRAFT' AND submitted_by IS NULL AND submitted_at IS NULL AND submitted_actor IS NULL
    AND locked_by IS NULL AND locked_at IS NULL AND locked_actor IS NULL)
  OR (state='SUBMITTED' AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL AND submitted_actor IS NOT NULL
    AND locked_by IS NULL AND locked_at IS NULL AND locked_actor IS NULL)
  OR (state='LOCKED' AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL AND submitted_actor IS NOT NULL
    AND locked_by IS NOT NULL AND locked_at IS NOT NULL AND locked_actor IS NOT NULL)
);
ALTER TABLE ccs.closings ADD CONSTRAINT submitted_actor_identity CHECK (
  submitted_actor IS NULL OR COALESCE(jsonb_typeof(submitted_actor)='object' AND submitted_actor->>'id'=submitted_by::text,false)
);
ALTER TABLE ccs.closings ADD CONSTRAINT locked_actor_identity CHECK (
  locked_actor IS NULL OR COALESCE(jsonb_typeof(locked_actor)='object' AND locked_actor->>'id'=locked_by::text,false)
);
CREATE TABLE ccs.closing_reports (
  id uuid PRIMARY KEY,
  closing_id uuid NOT NULL,
  outlet_id uuid NOT NULL,
  generation bigint NOT NULL CHECK (generation BETWEEN 1 AND 9007199254740991),
  closing_revision bigint NOT NULL CHECK (closing_revision BETWEEN 1 AND 9007199254740991),
  locked_by uuid NOT NULL REFERENCES ccs.accounts(id),
  locked_at timestamptz NOT NULL,
  snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot)='object'),
  UNIQUE (closing_id,generation),
  FOREIGN KEY (closing_id,outlet_id) REFERENCES ccs.closings(id,outlet_id)
);
CREATE TRIGGER immutable_closing_reports BEFORE UPDATE OR DELETE ON ccs.closing_reports
  FOR EACH ROW EXECUTE FUNCTION ccs.prevent_history_rewrite();
