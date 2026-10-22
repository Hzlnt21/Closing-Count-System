ALTER TABLE ccs.memberships ADD COLUMN revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0);
CREATE TRIGGER immutable_membership_audit BEFORE UPDATE OR DELETE ON ccs.membership_audit
  FOR EACH ROW EXECUTE FUNCTION ccs.prevent_history_rewrite();
