CREATE TABLE ccs.accounts (
  id uuid PRIMARY KEY,
  username text NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9][a-z0-9._-]{2,63}$'),
  display_name text NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 120),
  password_hash text NOT NULL CHECK (password_hash ~ '^scrypt\$v1\$[a-f0-9]{32}\$[a-f0-9]{128}$'),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE ccs.outlets (
  id uuid PRIMARY KEY,
  name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
  timezone text NOT NULL DEFAULT 'Asia/Jakarta',
  cutoff_minutes integer NOT NULL DEFAULT 0 CHECK (cutoff_minutes BETWEEN 0 AND 1439),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE ccs.memberships (
  outlet_id uuid NOT NULL REFERENCES ccs.outlets(id),
  account_id uuid NOT NULL REFERENCES ccs.accounts(id),
  role text NOT NULL CHECK (role IN ('EMPLOYEE','SUPERVISOR','ADMIN')),
  active boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (outlet_id,account_id)
);
CREATE TABLE ccs.sessions (
  id uuid PRIMARY KEY,
  account_id uuid NOT NULL REFERENCES ccs.accounts(id),
  token_hash text NOT NULL UNIQUE CHECK (token_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  CHECK (expires_at > created_at)
);
CREATE INDEX ON ccs.sessions (account_id,created_at DESC);
CREATE TABLE ccs.membership_audit (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  outlet_id uuid NOT NULL REFERENCES ccs.outlets(id),
  actor_id uuid NOT NULL REFERENCES ccs.accounts(id),
  subject_id uuid NOT NULL REFERENCES ccs.accounts(id),
  action text NOT NULL CHECK (action IN ('CREATE_ACCOUNT','UPDATE_MEMBERSHIP')),
  before_value jsonb,
  after_value jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
