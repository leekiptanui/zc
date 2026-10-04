-- =============================================================================
-- zcare-agreed-design.sql -- the agreed ZCare schema: the parity baseline for
-- AgreedDesignParityIT, which builds this file and the Liquibase changelog side
-- by side and requires their catalogues to be identical.
-- Source: docs/db.sql from the Migration & Development Roadmap v0.3
-- (2026-09-28). Only its V003 block differs: replaced by the trial system's real
-- V003 (ADR-0004). Every other line is unchanged.
-- =============================================================================
-- =============================================================================
-- db.sql -- ZCare database (60 tables). V001..V005 in one transaction.
-- =============================================================================
-- Requires PostgreSQL 16. Run as the DB owner with CREATEROLE.
--
--   createdb zcare
--   psql -v ON_ERROR_STOP=1 -d zcare -f db.sql
--   psql -d zcare -c "ALTER ROLE zc_app PASSWORD '...'"
--   psql -v ON_ERROR_STOP=1 -d zcare -f verify/verify_schema.sql   -- as superuser
--
-- Before production:
--   * V003 is reconstructed from its title only.
--   * V005 stores a WhatsApp access_token (conflicts with 04D 14).
--   * TODOs: care models, audit operations, risk tiers.
-- =============================================================================
\set ON_ERROR_STOP on
SET client_min_messages = warning;

BEGIN;

DO $guard$
BEGIN
  IF current_setting('server_version_num')::int < 160000 THEN
    RAISE EXCEPTION 'ZCare requires PostgreSQL 16 or later (found %)', current_setting('server_version');
  END IF;
  IF to_regclass('public.zc_tenant') IS NOT NULL THEN
    RAISE EXCEPTION 'ZCare schema already exists in this database; db.sql only creates a fresh one';
  END IF;
END
$guard$;



-- #############################################################################
-- ##  V001__initial_schema.sql
-- #############################################################################

-- =============================================================================
-- V001__initial_schema.sql -- founding schema (58 tables)
-- =============================================================================
-- Naming: pk_/fk_/uq_/ix_/ck_/trg_<table>_<purpose>. The API maps constraint
-- names to error codes, so renaming one is a contract change.
-- Ids: bigint identity, unique per table. Read back with RETURNING id.
-- All intra-tenant FKs include tenant_id, so no row can point at another tenant.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- for uq_zc_role_assignment_no_overlap

-- -----------------------------------------------------------------------------
-- Current tenant. Set per transaction:
--     SELECT set_config('zcare.tenant_id', '<id>', true);
-- Unset = NULL = all RLS policies false (fail closed).
-- -----------------------------------------------------------------------------
CREATE FUNCTION zc_current_tenant() RETURNS bigint
  LANGUAGE sql STABLE PARALLEL SAFE
AS $fn$
  SELECT NULLIF(pg_catalog.current_setting('zcare.tenant_id', true), '')::bigint
$fn$;
COMMENT ON FUNCTION zc_current_tenant() IS
  'Tenant for the current transaction, from the zcare.tenant_id setting. NULL when unset.';

-- -----------------------------------------------------------------------------
-- Locks id/tenant/created_* and bumps updated_at + row_version.
-- -----------------------------------------------------------------------------
CREATE FUNCTION zc_touch_row() RETURNS trigger
  LANGUAGE plpgsql
AS $fn$
DECLARE
  v_old jsonb := to_jsonb(OLD);
  v_new jsonb := to_jsonb(NEW);
  v_col text;
BEGIN
  FOREACH v_col IN ARRAY ARRAY['id','tenant_id','created_at','created_by'] LOOP
    IF v_old ? v_col AND (v_old -> v_col) IS DISTINCT FROM (v_new -> v_col) THEN
      RAISE EXCEPTION 'trg_zc_row_identity_immutable: %.% cannot be changed', TG_TABLE_NAME, v_col
        USING ERRCODE = 'integrity_constraint_violation',
              CONSTRAINT = 'trg_zc_row_identity_immutable',
              TABLE = TG_TABLE_NAME, COLUMN = v_col;
    END IF;
  END LOOP;
  NEW.updated_at  := now();
  NEW.row_version := OLD.row_version + 1;
  RETURN NEW;
END
$fn$;

-- -----------------------------------------------------------------------------
-- Append-only: blocks UPDATE, DELETE, TRUNCATE. TG_ARGV[0] = constraint name.
-- -----------------------------------------------------------------------------
CREATE FUNCTION zc_forbid_change() RETURNS trigger
  LANGUAGE plpgsql
AS $fn$
BEGIN
  RAISE EXCEPTION '%: % is append-only; % is not permitted', TG_ARGV[0], TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'integrity_constraint_violation',
          CONSTRAINT = TG_ARGV[0],
          TABLE = TG_TABLE_NAME;
END
$fn$;

-- -----------------------------------------------------------------------------
-- Pinned columns. TG_ARGV[0] = constraint name, TG_ARGV[1..] = locked columns.
-- -----------------------------------------------------------------------------
CREATE FUNCTION zc_forbid_pinned_change() RETURNS trigger
  LANGUAGE plpgsql
AS $fn$
DECLARE
  v_old jsonb := to_jsonb(OLD);
  v_new jsonb := to_jsonb(NEW);
  v_col text;
  i     integer;
BEGIN
  FOR i IN 1 .. TG_NARGS - 1 LOOP
    v_col := TG_ARGV[i];
    IF NOT (v_old ? v_col) THEN
      RAISE EXCEPTION 'zc_forbid_pinned_change misconfigured: %.% does not exist', TG_TABLE_NAME, v_col;
    END IF;
    IF (v_old -> v_col) IS DISTINCT FROM (v_new -> v_col) THEN
      RAISE EXCEPTION '%: %.% is pinned and cannot be changed', TG_ARGV[0], TG_TABLE_NAME, v_col
        USING ERRCODE = 'integrity_constraint_violation',
              CONSTRAINT = TG_ARGV[0],
              TABLE = TG_TABLE_NAME, COLUMN = v_col;
    END IF;
  END LOOP;
  RETURN NEW;
END
$fn$;

-- -----------------------------------------------------------------------------
-- Published versions are immutable; the only allowed change is retiring them.
-- Retired rows are fully frozen. TG_ARGV[0] = constraint name.
-- -----------------------------------------------------------------------------
CREATE FUNCTION zc_forbid_published_version_change() RETURNS trigger
  LANGUAGE plpgsql
AS $fn$
DECLARE
  c_retire_cols CONSTANT text[] := ARRAY['status','retired_at','updated_at','updated_by','row_version'];
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.status IN ('published','retired') THEN
      RAISE EXCEPTION '%: a % version cannot be deleted', TG_ARGV[0], OLD.status
        USING ERRCODE = 'integrity_constraint_violation', CONSTRAINT = TG_ARGV[0], TABLE = TG_TABLE_NAME;
    END IF;
    RETURN OLD;
  END IF;

  IF OLD.status = 'retired' THEN
    RAISE EXCEPTION '%: a retired version is frozen', TG_ARGV[0]
      USING ERRCODE = 'integrity_constraint_violation', CONSTRAINT = TG_ARGV[0], TABLE = TG_TABLE_NAME;
  END IF;

  IF OLD.status = 'published' THEN
    IF NEW.status <> 'retired'
       OR (to_jsonb(NEW) - c_retire_cols) IS DISTINCT FROM (to_jsonb(OLD) - c_retire_cols) THEN
      RAISE EXCEPTION '%: a published version is immutable; publish a new version instead', TG_ARGV[0]
        USING ERRCODE = 'integrity_constraint_violation', CONSTRAINT = TG_ARGV[0], TABLE = TG_TABLE_NAME;
    END IF;
  END IF;

  RETURN NEW;
END
$fn$;

-- =============================================================================
-- 01  TENANCY & ACCESS
-- =============================================================================

CREATE TABLE zc_tenant (
  id          bigint GENERATED ALWAYS AS IDENTITY,
  code        text NOT NULL,
  name        text NOT NULL,
  status      text NOT NULL DEFAULT 'active',
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_tenant_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_tenant_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_tenant PRIMARY KEY (id),
  CONSTRAINT uq_zc_tenant_code UNIQUE (code),
  CONSTRAINT ck_zc_tenant_code   CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_tenant_name   CHECK (btrim(name) <> ''),
  CONSTRAINT ck_zc_tenant_status CHECK (status IN ('active','suspended','closed'))
);
COMMENT ON TABLE zc_tenant IS
  'Tenant registry. The only table without tenant_id and without RLS: zc_app must read it to resolve X-Tenant-Id (04D 12.3). Holds no health or member data.';

CREATE TABLE zc_organisation (
  id          bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id   bigint NOT NULL,
  code        text NOT NULL,
  name        text NOT NULL,
  org_type    text NOT NULL,
  status      text NOT NULL DEFAULT 'active',
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_organisation_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_organisation_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_organisation PRIMARY KEY (id),
  CONSTRAINT fk_zc_organisation_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_organisation_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_organisation_code UNIQUE (tenant_id, code),
  CONSTRAINT ck_zc_organisation_code     CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_organisation_name     CHECK (btrim(name) <> ''),
  CONSTRAINT ck_zc_organisation_org_type CHECK (btrim(org_type) <> ''),
  CONSTRAINT ck_zc_organisation_status   CHECK (status IN ('active','suspended','inactive'))
);
COMMENT ON TABLE zc_organisation IS 'Every org is scoped to exactly one tenant -- no cross-tenant orgs.';

CREATE TABLE zc_role_assignment (
  id               bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id        bigint NOT NULL,
  organisation_id  bigint NOT NULL,
  actor_id         text NOT NULL,
  role_code        text NOT NULL,
  valid_from       timestamptz NOT NULL DEFAULT now(),
  valid_to         timestamptz,
  granted_by       text NOT NULL,
  revoked_at       timestamptz,
  revoked_by       text,
  revoke_reason    text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_role_assignment_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_role_assignment_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_role_assignment PRIMARY KEY (id),
  CONSTRAINT fk_zc_role_assignment_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_role_assignment_organisation FOREIGN KEY (tenant_id, organisation_id)
    REFERENCES zc_organisation (tenant_id, id),
  CONSTRAINT ck_zc_role_assignment_actor      CHECK (btrim(actor_id) <> ''),
  CONSTRAINT ck_zc_role_assignment_role_code  CHECK (btrim(role_code) <> ''),
  CONSTRAINT ck_zc_role_assignment_granted_by CHECK (btrim(granted_by) <> ''),
  CONSTRAINT ck_zc_role_assignment_validity   CHECK (valid_to IS NULL OR valid_to > valid_from),
  CONSTRAINT ck_zc_role_assignment_revocation CHECK (
    (revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL)
    OR (revoked_at IS NOT NULL
        AND nullif(btrim(revoked_by), '') IS NOT NULL
        AND nullif(btrim(revoke_reason), '') IS NOT NULL)),
  -- No overlapping grants of the same role.
  CONSTRAINT uq_zc_role_assignment_no_overlap EXCLUDE USING gist (
    tenant_id       WITH =,
    organisation_id WITH =,
    actor_id        WITH =,
    role_code       WITH =,
    (tstzrange(valid_from, valid_to)) WITH &&
  ) WHERE (revoked_at IS NULL)
);
COMMENT ON TABLE zc_role_assignment IS 'A role is always granted within an org, never floating free of one.';
CREATE INDEX ix_zc_role_assignment_actor ON zc_role_assignment (tenant_id, actor_id);

CREATE TABLE zc_break_glass_grant (
  id           bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id    bigint NOT NULL,
  actor_id     text NOT NULL,
  reason       text NOT NULL,
  granted_at   timestamptz NOT NULL DEFAULT now(),
  expires_at   timestamptz NOT NULL,
  revoked_at   timestamptz,
  revoked_by   text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_break_glass_grant_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_break_glass_grant_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_break_glass_grant PRIMARY KEY (id),
  CONSTRAINT fk_zc_break_glass_grant_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT ck_zc_break_glass_grant_actor      CHECK (btrim(actor_id) <> ''),
  CONSTRAINT ck_zc_break_glass_grant_reason     CHECK (btrim(reason) <> ''),
  CONSTRAINT ck_zc_break_glass_grant_time_bound CHECK (expires_at > granted_at),
  CONSTRAINT ck_zc_break_glass_grant_revocation CHECK (
    (revoked_at IS NULL AND revoked_by IS NULL)
    OR (revoked_at IS NOT NULL AND nullif(btrim(revoked_by), '') IS NOT NULL))
);
COMMENT ON TABLE zc_break_glass_grant IS 'Time-bound elevated access. Use of a grant is also written to zc_security_event.';
CREATE INDEX ix_zc_break_glass_grant_actor ON zc_break_glass_grant (tenant_id, actor_id, expires_at);

-- Append-only. A config change is a new row.
CREATE TABLE zc_config_history (
  id              bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id       bigint NOT NULL,
  config_scope    text NOT NULL,
  config_key      text NOT NULL,
  config_value    text NOT NULL,
  effective_from  timestamptz NOT NULL,
  change_reason   text NOT NULL,
  approved_by     text,
  approved_at     timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_config_history_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_config_history PRIMARY KEY (id),
  CONSTRAINT fk_zc_config_history_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_config_history_effective UNIQUE (tenant_id, config_scope, config_key, effective_from),
  CONSTRAINT ck_zc_config_history_scope  CHECK (btrim(config_scope) <> ''),
  CONSTRAINT ck_zc_config_history_key    CHECK (btrim(config_key) <> ''),
  CONSTRAINT ck_zc_config_history_reason CHECK (btrim(change_reason) <> ''),
  CONSTRAINT ck_zc_config_history_approval_pair CHECK ((approved_by IS NULL) = (approved_at IS NULL))
);

-- =============================================================================
-- 02  EXTERNAL REFERENCES  (the minimum-copy boundary, PRD 12.6 / DEC-009)
-- =============================================================================

CREATE TABLE zc_member_reference (
  id                     bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id              bigint NOT NULL,
  source_system          text NOT NULL,   -- free text from adapter
  source_member_number   text NOT NULL,   -- may be household-level
  source_individual_ref  text NOT NULL,   -- identifies the person in the household
  display_name           text,
  contact_msisdn         text,
  source_date            date NOT NULL,
  is_authoritative       boolean NOT NULL DEFAULT false,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_member_reference_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_member_reference_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_member_reference PRIMARY KEY (id),
  CONSTRAINT fk_zc_member_reference_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_member_reference_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_member_reference_individual
    UNIQUE (tenant_id, source_system, source_member_number, source_individual_ref),
  CONSTRAINT ck_zc_member_reference_non_auth CHECK (is_authoritative = false),
  CONSTRAINT ck_zc_member_reference_source_system CHECK (btrim(source_system) <> ''),
  CONSTRAINT ck_zc_member_reference_member_number CHECK (btrim(source_member_number) <> ''),
  CONSTRAINT ck_zc_member_reference_individual    CHECK (btrim(source_individual_ref) <> ''),
  CONSTRAINT ck_zc_member_reference_msisdn CHECK (contact_msisdn IS NULL OR contact_msisdn ~ '^\+?[1-9][0-9]{6,14}$')
);
COMMENT ON TABLE zc_member_reference IS
  'Minimum reference to a person, resolved to an individual. NOT a member master. is_authoritative is CHECK-pinned false so a copy can never be promoted to truth.';
CREATE INDEX ix_zc_member_reference_msisdn ON zc_member_reference (tenant_id, contact_msisdn)
  WHERE contact_msisdn IS NOT NULL;

CREATE TABLE zc_external_reference (
  id                bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id         bigint NOT NULL,
  entity_type       text NOT NULL,
  entity_id         bigint NOT NULL, -- polymorphic, no FK
  system_code       text NOT NULL,   -- free text from adapter
  code_system       text,
  external_id       text NOT NULL,
  source_date       date,
  is_authoritative  boolean NOT NULL DEFAULT false,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_external_reference_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_external_reference_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_external_reference PRIMARY KEY (id),
  CONSTRAINT fk_zc_external_reference_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_external_reference_mapping UNIQUE NULLS NOT DISTINCT
    (tenant_id, entity_type, entity_id, system_code, code_system),
  CONSTRAINT ck_zc_external_reference_non_auth CHECK (is_authoritative = false),
  CONSTRAINT ck_zc_external_reference_entity_type CHECK (entity_type IN (
    'member_reference','organisation','enrolment','care_plan','goal','task','referral',
    'transition','medication_coordination','refill_request','observation','care_gap',
    'assessment','consent_record','provider_participation','outcome_observation')),
  CONSTRAINT ck_zc_external_reference_system_code CHECK (btrim(system_code) <> ''),
  CONSTRAINT ck_zc_external_reference_external_id CHECK (btrim(external_id) <> '')
);
COMMENT ON TABLE zc_external_reference IS
  'General map from a ZCare entity to an external identifier (provider, lab, pharmacy, FHIR). entity_id is polymorphic by design; the entity_type CHECK carries the meaning.';
CREATE INDEX ix_zc_external_reference_lookup ON zc_external_reference (tenant_id, system_code, external_id);

-- =============================================================================
-- 03  PROGRAMME MANAGEMENT
-- =============================================================================

CREATE TABLE zc_programme (
  id           bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id    bigint NOT NULL,
  code         text NOT NULL,
  name         text NOT NULL,
  care_model   text NOT NULL,
  status       text NOT NULL DEFAULT 'draft',
  description  text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_programme_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_programme_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_programme PRIMARY KEY (id),
  CONSTRAINT fk_zc_programme_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_programme_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_programme_code UNIQUE (tenant_id, code),
  CONSTRAINT ck_zc_programme_code   CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_programme_name   CHECK (btrim(name) <> ''),
  CONSTRAINT ck_zc_programme_status CHECK (status IN ('draft','active','suspended','retired')),
  -- TODO: replace with the 13 approved care models (PRD 7.5).
  CONSTRAINT ck_zc_programme_care_model CHECK (btrim(care_model) <> '')
);

CREATE TABLE zc_programme_version (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  programme_id          bigint NOT NULL,
  version_number        integer NOT NULL,
  status                text NOT NULL DEFAULT 'draft',
  content               jsonb,
  clinical_approved_by  text,
  clinical_approved_at  timestamptz,
  published_at          timestamptz,
  retired_at            timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_programme_version_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_programme_version_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_programme_version PRIMARY KEY (id),
  CONSTRAINT fk_zc_programme_version_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_programme_version_programme FOREIGN KEY (tenant_id, programme_id)
    REFERENCES zc_programme (tenant_id, id),
  CONSTRAINT uq_zc_programme_version_fk_target    UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_programme_version_fk_programme UNIQUE (tenant_id, id, programme_id),
  CONSTRAINT uq_zc_programme_version_number UNIQUE (tenant_id, programme_id, version_number),
  CONSTRAINT ck_zc_programme_version_number CHECK (version_number > 0),
  CONSTRAINT ck_zc_programme_version_status CHECK (status IN ('draft','pending_approval','published','retired')),
  CONSTRAINT ck_zc_programme_version_publication CHECK (
    status <> 'published'
    OR (nullif(btrim(clinical_approved_by), '') IS NOT NULL
        AND clinical_approved_at IS NOT NULL
        AND published_at IS NOT NULL)),
  CONSTRAINT ck_zc_programme_version_published_approved CHECK (
    published_at IS NULL OR (clinical_approved_by IS NOT NULL AND clinical_approved_at IS NOT NULL)),
  CONSTRAINT ck_zc_programme_version_retired CHECK (status <> 'retired' OR retired_at IS NOT NULL)
);
COMMENT ON COLUMN zc_programme_version.content IS
  'jsonb: programme content is an unresolved external dependency (Aga Khan clinical content package). No invariant is enforced inside it.';

CREATE TABLE zc_assessment_template (
  id           bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id    bigint NOT NULL,
  code         text NOT NULL,
  name         text NOT NULL,
  description  text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_assessment_template_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_assessment_template_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_assessment_template PRIMARY KEY (id),
  CONSTRAINT fk_zc_assessment_template_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_assessment_template_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_assessment_template_code UNIQUE (tenant_id, code),
  CONSTRAINT ck_zc_assessment_template_code CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_assessment_template_name CHECK (btrim(name) <> '')
);

CREATE TABLE zc_assessment_template_version (
  id              bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id       bigint NOT NULL,
  template_id     bigint NOT NULL,
  version_number  integer NOT NULL,
  status          text NOT NULL DEFAULT 'draft',
  questionnaire   jsonb NOT NULL,
  scoring_rules   jsonb,
  published_at    timestamptz,
  retired_at      timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_assessment_template_version_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_assessment_template_version_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_assessment_template_version PRIMARY KEY (id),
  CONSTRAINT fk_zc_assessment_template_version_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_assessment_template_version_template FOREIGN KEY (tenant_id, template_id)
    REFERENCES zc_assessment_template (tenant_id, id),
  CONSTRAINT uq_zc_assessment_template_version_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_assessment_template_version_number UNIQUE (tenant_id, template_id, version_number),
  CONSTRAINT ck_zc_assessment_template_version_number CHECK (version_number > 0),
  CONSTRAINT ck_zc_assessment_template_version_status CHECK (status IN ('draft','published','retired')),
  CONSTRAINT ck_zc_assessment_template_version_published CHECK (status <> 'published' OR published_at IS NOT NULL),
  CONSTRAINT ck_zc_assessment_template_version_retired CHECK (status <> 'retired' OR retired_at IS NOT NULL)
);
COMMENT ON COLUMN zc_assessment_template_version.questionnaire IS
  'jsonb: questionnaire content is an unresolved external dependency (clinical content package).';
COMMENT ON COLUMN zc_assessment_template_version.scoring_rules IS
  'jsonb: scoring rules are an unresolved external dependency (clinical content package).';

CREATE TABLE zc_gap_rule (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  gap_type              text NOT NULL,
  rule_version          integer NOT NULL DEFAULT 1,
  period_strategy       text NOT NULL,
  definition            jsonb NOT NULL,
  description           text,
  is_active             boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_gap_rule_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_gap_rule_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_gap_rule PRIMARY KEY (id),
  CONSTRAINT fk_zc_gap_rule_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_gap_rule_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT uq_zc_gap_rule_fk_target UNIQUE (tenant_id, id, programme_version_id, gap_type, rule_version),
  CONSTRAINT uq_zc_gap_rule_version UNIQUE (tenant_id, programme_version_id, gap_type, rule_version),
  CONSTRAINT ck_zc_gap_rule_gap_type CHECK (btrim(gap_type) <> ''),
  CONSTRAINT ck_zc_gap_rule_rule_version CHECK (rule_version > 0),
  CONSTRAINT ck_zc_gap_rule_period_strategy CHECK (btrim(period_strategy) <> '')
);
COMMENT ON COLUMN zc_gap_rule.definition IS
  'jsonb: gap-rule definition and thresholds are an unresolved external dependency (clinical content package).';

CREATE TABLE zc_task_template (
  id                     bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id              bigint NOT NULL,
  programme_version_id   bigint NOT NULL,
  task_type              text NOT NULL,
  name                   text NOT NULL,
  description            text,
  default_priority       smallint NOT NULL DEFAULT 3,
  sla_hours              integer,
  default_assignee_role  text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_task_template_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_task_template_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_task_template PRIMARY KEY (id),
  CONSTRAINT fk_zc_task_template_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_task_template_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT uq_zc_task_template_type UNIQUE (tenant_id, programme_version_id, task_type),
  CONSTRAINT ck_zc_task_template_task_type CHECK (btrim(task_type) <> ''),
  CONSTRAINT ck_zc_task_template_name CHECK (btrim(name) <> ''),
  CONSTRAINT ck_zc_task_template_priority CHECK (default_priority BETWEEN 1 AND 5),
  CONSTRAINT ck_zc_task_template_sla CHECK (sla_hours IS NULL OR sla_hours > 0)
);

-- =============================================================================
-- 3a  PROGRAMME-SCOPED CLINICAL VOCABULARY  (configuration, not schema: 04D 2.3)
-- =============================================================================

CREATE TABLE zc_referral_type (
  id                   bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id            bigint NOT NULL,
  code                 text NOT NULL,
  name                 text NOT NULL,
  is_platform_defined  boolean NOT NULL DEFAULT false,
  is_active            boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_referral_type_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_referral_type_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_referral_type PRIMARY KEY (id),
  CONSTRAINT fk_zc_referral_type_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_referral_type_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_referral_type_code UNIQUE (tenant_id, code),
  CONSTRAINT ck_zc_referral_type_code CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_referral_type_name CHECK (btrim(name) <> '')
);
COMMENT ON TABLE zc_referral_type IS 'Tenant-scoped, not programme-scoped: any programme may use a referral type.';

CREATE TABLE zc_observation_type (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  code                  text NOT NULL,
  name                  text NOT NULL,
  unit                  text NOT NULL,   -- UCUM; use '1' for unitless
  loinc_code            text,
  plausible_min         numeric,
  plausible_max         numeric,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_observation_type_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_observation_type_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_observation_type PRIMARY KEY (id),
  CONSTRAINT fk_zc_observation_type_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_observation_type_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT uq_zc_observation_type_fk_version UNIQUE (tenant_id, id, programme_version_id),
  CONSTRAINT uq_zc_observation_type_fk_unit    UNIQUE (tenant_id, id, unit),
  CONSTRAINT uq_zc_observation_type_code UNIQUE (tenant_id, programme_version_id, code),
  CONSTRAINT ck_zc_observation_type_code CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_observation_type_name CHECK (btrim(name) <> ''),
  CONSTRAINT ck_zc_observation_type_unit CHECK (btrim(unit) <> ''),
  CONSTRAINT ck_zc_observation_type_plausible_range CHECK (
    plausible_min IS NULL OR plausible_max IS NULL OR plausible_min <= plausible_max)
);

CREATE TABLE zc_goal_type (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  code                  text NOT NULL,
  name                  text NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_goal_type_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_goal_type_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_goal_type PRIMARY KEY (id),
  CONSTRAINT fk_zc_goal_type_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_goal_type_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT uq_zc_goal_type_fk_version UNIQUE (tenant_id, id, programme_version_id),
  CONSTRAINT uq_zc_goal_type_code UNIQUE (tenant_id, programme_version_id, code),
  CONSTRAINT ck_zc_goal_type_code CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_goal_type_name CHECK (btrim(name) <> '')
);

CREATE TABLE zc_outcome_definition (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  measure_code          text NOT NULL,
  measure_version       integer NOT NULL DEFAULT 1,
  name                  text NOT NULL,
  category              text NOT NULL,
  definition            jsonb NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_outcome_definition_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_outcome_definition_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_outcome_definition PRIMARY KEY (id),
  CONSTRAINT fk_zc_outcome_definition_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_outcome_definition_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT uq_zc_outcome_definition_fk_version UNIQUE (tenant_id, id, measure_version),
  CONSTRAINT uq_zc_outcome_definition_measure UNIQUE (tenant_id, programme_version_id, measure_code, measure_version),
  CONSTRAINT ck_zc_outcome_definition_measure_code CHECK (btrim(measure_code) <> ''),
  CONSTRAINT ck_zc_outcome_definition_measure_version CHECK (measure_version > 0),
  CONSTRAINT ck_zc_outcome_definition_name CHECK (btrim(name) <> ''),
  CONSTRAINT ck_zc_outcome_definition_category CHECK (btrim(category) <> '')
);
COMMENT ON COLUMN zc_outcome_definition.definition IS
  'jsonb: outcome measure definitions are configuration pending the clinical content package.';

-- =============================================================================
-- 04  POPULATION & COHORT
-- =============================================================================

CREATE TABLE zc_cohort (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  code                  text NOT NULL,
  name                  text NOT NULL,
  status                text NOT NULL DEFAULT 'draft',
  description           text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_cohort_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_cohort_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_cohort PRIMARY KEY (id),
  CONSTRAINT fk_zc_cohort_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_cohort_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT uq_zc_cohort_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_cohort_code UNIQUE (tenant_id, programme_version_id, code),
  CONSTRAINT ck_zc_cohort_code CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_cohort_name CHECK (btrim(name) <> ''),
  CONSTRAINT ck_zc_cohort_status CHECK (status IN ('draft','active','closed'))
);

CREATE TABLE zc_cohort_run (
  id                bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id         bigint NOT NULL,
  cohort_id         bigint NOT NULL,
  status            text NOT NULL DEFAULT 'running',
  rule_reference    text NOT NULL,
  started_at        timestamptz NOT NULL DEFAULT now(),
  finished_at       timestamptz,
  source_data_date  date,
  result_count      integer,
  hold_reason       text,
  reviewed_by       text,
  reviewed_at       timestamptz,
  failure_reason    text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_cohort_run_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_cohort_run_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_cohort_run PRIMARY KEY (id),
  CONSTRAINT fk_zc_cohort_run_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_cohort_run_cohort FOREIGN KEY (tenant_id, cohort_id)
    REFERENCES zc_cohort (tenant_id, id),
  CONSTRAINT uq_zc_cohort_run_fk_cohort UNIQUE (tenant_id, id, cohort_id),
  CONSTRAINT ck_zc_cohort_run_status CHECK (status IN ('running','pending_review','completed','failed','rejected')),
  CONSTRAINT ck_zc_cohort_run_rule_reference CHECK (btrim(rule_reference) <> ''),
  CONSTRAINT ck_zc_cohort_run_result_count CHECK (result_count IS NULL OR result_count >= 0),
  -- Held runs need a reason.
  CONSTRAINT ck_zc_cohort_run_hold CHECK (status <> 'pending_review' OR nullif(btrim(hold_reason), '') IS NOT NULL),
  CONSTRAINT ck_zc_cohort_run_finished CHECK (status NOT IN ('completed','failed','rejected') OR finished_at IS NOT NULL),
  CONSTRAINT ck_zc_cohort_run_failure CHECK (status <> 'failed' OR nullif(btrim(failure_reason), '') IS NOT NULL),
  CONSTRAINT ck_zc_cohort_run_review_pair CHECK ((reviewed_by IS NULL) = (reviewed_at IS NULL)),
  CONSTRAINT ck_zc_cohort_run_rejected CHECK (status <> 'rejected' OR reviewed_by IS NOT NULL)
);
CREATE INDEX ix_zc_cohort_run_cohort ON zc_cohort_run (tenant_id, cohort_id, started_at DESC);

CREATE TABLE zc_cohort_membership (
  id                       bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id                bigint NOT NULL,
  cohort_id                bigint NOT NULL,
  member_id                bigint NOT NULL,
  cohort_run_id            bigint,
  inclusion_method         text NOT NULL,
  inclusion_source_detail  text,   -- free text from adapter
  inclusion_reason         text NOT NULL,
  source_data_date         date NOT NULL,
  status                   text NOT NULL DEFAULT 'included',
  removed_at               timestamptz,
  removed_by               text,
  removal_reason           text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_cohort_membership_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_cohort_membership_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_cohort_membership PRIMARY KEY (id),
  CONSTRAINT fk_zc_cohort_membership_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_cohort_membership_cohort FOREIGN KEY (tenant_id, cohort_id)
    REFERENCES zc_cohort (tenant_id, id),
  CONSTRAINT fk_zc_cohort_membership_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  -- Run must belong to the same cohort.
  CONSTRAINT fk_zc_cohort_membership_cohort_run FOREIGN KEY (tenant_id, cohort_run_id, cohort_id)
    REFERENCES zc_cohort_run (tenant_id, id, cohort_id),
  CONSTRAINT uq_zc_cohort_membership_fk_member UNIQUE (tenant_id, id, member_id),
  CONSTRAINT uq_zc_cohort_membership_member UNIQUE (tenant_id, cohort_id, member_id),
  CONSTRAINT ck_zc_cohort_membership_inclusion_method CHECK (
    inclusion_method IN ('payer_flagged','claims_derived','manual','imported')),
  CONSTRAINT ck_zc_cohort_membership_run_provenance CHECK (
    inclusion_method IN ('manual','imported') OR cohort_run_id IS NOT NULL),
  CONSTRAINT ck_zc_cohort_membership_reason CHECK (btrim(inclusion_reason) <> ''),
  CONSTRAINT ck_zc_cohort_membership_status CHECK (status IN ('included','removed')),
  CONSTRAINT ck_zc_cohort_membership_removal CHECK (
    status <> 'removed'
    OR (removed_at IS NOT NULL
        AND nullif(btrim(removed_by), '') IS NOT NULL
        AND nullif(btrim(removal_reason), '') IS NOT NULL))
);
COMMENT ON TABLE zc_cohort_membership IS 'Eligibility for invitation. Membership <> enrolment <> consent: three distinct gates.';
CREATE INDEX ix_zc_cohort_membership_member ON zc_cohort_membership (tenant_id, member_id);

-- =============================================================================
-- 05  ENROLMENT, CONSENT & PREFERENCES
-- =============================================================================

CREATE TABLE zc_enrolment (
  id                       bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id                bigint NOT NULL,
  member_id                bigint NOT NULL,
  programme_id             bigint NOT NULL,
  programme_version_id     bigint NOT NULL,
  cohort_membership_id     bigint,
  supersedes_enrolment_id  bigint,
  status                   text NOT NULL DEFAULT 'invited',
  invited_at               timestamptz NOT NULL DEFAULT now(),
  activated_at             timestamptz,
  responsible_cm           text,
  exited_at                timestamptz,
  exit_reason              text,
  eligibility_snapshot     jsonb,
  eligibility_source_date  date,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_enrolment_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_enrolment_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_enrolment PRIMARY KEY (id),
  CONSTRAINT fk_zc_enrolment_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_enrolment_fk_target         UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_enrolment_fk_member         UNIQUE (tenant_id, id, member_id),
  CONSTRAINT uq_zc_enrolment_fk_version        UNIQUE (tenant_id, id, programme_version_id),
  CONSTRAINT uq_zc_enrolment_fk_version_member UNIQUE (tenant_id, id, programme_version_id, member_id),
  CONSTRAINT fk_zc_enrolment_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT fk_zc_enrolment_programme FOREIGN KEY (tenant_id, programme_id)
    REFERENCES zc_programme (tenant_id, id),
  -- Version must belong to the enrolled programme.
  CONSTRAINT fk_zc_enrolment_programme_version FOREIGN KEY (tenant_id, programme_version_id, programme_id)
    REFERENCES zc_programme_version (tenant_id, id, programme_id),
  CONSTRAINT fk_zc_enrolment_cohort_membership FOREIGN KEY (tenant_id, cohort_membership_id, member_id)
    REFERENCES zc_cohort_membership (tenant_id, id, member_id),
  CONSTRAINT fk_zc_enrolment_supersedes FOREIGN KEY (tenant_id, supersedes_enrolment_id, member_id)
    REFERENCES zc_enrolment (tenant_id, id, member_id),
  CONSTRAINT ck_zc_enrolment_status CHECK (status IN ('invited','active','suspended','completed','withdrawn')),
  CONSTRAINT ck_zc_enrolment_active_evidence CHECK (
    status <> 'active' OR (activated_at IS NOT NULL AND nullif(btrim(responsible_cm), '') IS NOT NULL)),
  CONSTRAINT ck_zc_enrolment_exit_evidence CHECK (
    status NOT IN ('completed','withdrawn') OR (exited_at IS NOT NULL AND nullif(btrim(exit_reason), '') IS NOT NULL)),
  CONSTRAINT ck_zc_enrolment_not_self_superseding CHECK (supersedes_enrolment_id IS NULL OR supersedes_enrolment_id <> id)
);
COMMENT ON TABLE zc_enrolment IS
  'Aggregate root: accountable participation of one member in one programme. programme_version_id is pinned at invitation and immutable (trg_zc_enrolment_pins).';
COMMENT ON COLUMN zc_enrolment.eligibility_snapshot IS
  'jsonb: cached, NON-authoritative copy of the payer eligibility envelope at invitation time. Displayed, never searched, never indexed.';

-- One live enrolment per member + programme ('invited' counts as live).
CREATE UNIQUE INDEX uq_zc_enrolment_one_active
  ON zc_enrolment (tenant_id, member_id, programme_id)
  WHERE status IN ('invited','active','suspended');
CREATE INDEX ix_zc_enrolment_caseload ON zc_enrolment (tenant_id, responsible_cm, status);
CREATE INDEX ix_zc_enrolment_programme_version ON zc_enrolment (tenant_id, programme_version_id, status);

CREATE TABLE zc_consent_record (
  id                      bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id               bigint NOT NULL,
  enrolment_id            bigint NOT NULL,
  member_id               bigint NOT NULL,
  status                  text NOT NULL DEFAULT 'captured',
  wording_version         text NOT NULL,   -- no fixed list yet
  channel                 text NOT NULL,
  scope_content_classes   text[] NOT NULL,
  scope_sharing_purposes  text[] NOT NULL DEFAULT '{}',
  captured_at             timestamptz NOT NULL DEFAULT now(),
  captured_by             text NOT NULL,
  evidence_ref            text,
  validated_by            text,
  validated_at            timestamptz,
  revoked_at              timestamptz,
  revocation_channel      text,
  revocation_verbatim     text,
  superseded_at           timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_consent_record_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_consent_record_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_consent_record PRIMARY KEY (id),
  CONSTRAINT fk_zc_consent_record_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  -- Consent member must match the enrolment member.
  CONSTRAINT fk_zc_consent_record_enrolment FOREIGN KEY (tenant_id, enrolment_id, member_id)
    REFERENCES zc_enrolment (tenant_id, id, member_id),
  CONSTRAINT fk_zc_consent_record_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT ck_zc_consent_record_status CHECK (
    status IN ('captured','validated','revoked','scope_reduced','superseded')),
  CONSTRAINT ck_zc_consent_record_wording_version CHECK (btrim(wording_version) <> ''),
  CONSTRAINT ck_zc_consent_record_channel CHECK (btrim(channel) <> ''),
  CONSTRAINT ck_zc_consent_record_captured_by CHECK (btrim(captured_by) <> ''),
  CONSTRAINT ck_zc_consent_record_scope CHECK (
    cardinality(scope_content_classes) >= 1
    AND scope_content_classes <@ ARRAY['condition_neutral','health_content']::text[]),
  CONSTRAINT ck_zc_consent_record_purposes CHECK (array_position(scope_sharing_purposes, NULL) IS NULL),
  CONSTRAINT ck_zc_consent_record_validated CHECK (
    status <> 'validated' OR (nullif(btrim(validated_by), '') IS NOT NULL AND validated_at IS NOT NULL)),
  CONSTRAINT ck_zc_consent_record_validation_pair CHECK ((validated_by IS NULL) = (validated_at IS NULL)),
  CONSTRAINT ck_zc_consent_record_revoked CHECK (
    status <> 'revoked'
    OR (revoked_at IS NOT NULL
        AND nullif(btrim(revocation_channel), '') IS NOT NULL
        AND nullif(btrim(revocation_verbatim), '') IS NOT NULL)),
  CONSTRAINT ck_zc_consent_record_superseded CHECK (status <> 'superseded' OR superseded_at IS NOT NULL)
);
COMMENT ON TABLE zc_consent_record IS
  'ZCare-owned consent (ADR-002). The DB makes the consent gate CHECKABLE; the gate itself is enforced at command, dispatch and query layers (04D 9.4 - residual risk).';

-- One consent in force per enrolment.
CREATE UNIQUE INDEX uq_zc_consent_record_in_force
  ON zc_consent_record (tenant_id, enrolment_id)
  WHERE status IN ('captured','validated','scope_reduced');
CREATE INDEX ix_zc_consent_record_member ON zc_consent_record (tenant_id, member_id, status);

CREATE TABLE zc_member_preference (
  id                     bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id              bigint NOT NULL,
  member_id              bigint NOT NULL,
  do_not_contact         boolean NOT NULL DEFAULT false,
  do_not_contact_reason  text,
  do_not_contact_at      timestamptz,
  preferred_channel      text,
  preferred_language     text,
  contact_window_start   time,
  contact_window_end     time,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_member_preference_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_member_preference_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_member_preference PRIMARY KEY (id),
  CONSTRAINT fk_zc_member_preference_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_member_preference_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT uq_zc_member_preference_member UNIQUE (tenant_id, member_id),
  CONSTRAINT ck_zc_member_preference_dnc_evidence CHECK (
    NOT do_not_contact
    OR (nullif(btrim(do_not_contact_reason), '') IS NOT NULL AND do_not_contact_at IS NOT NULL)),
  CONSTRAINT ck_zc_member_preference_window CHECK ((contact_window_start IS NULL) = (contact_window_end IS NULL))
);
-- =============================================================================
-- 06  ASSESSMENT & RISK
-- =============================================================================

CREATE TABLE zc_assessment (
  id                   bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id            bigint NOT NULL,
  enrolment_id         bigint NOT NULL,
  template_version_id  bigint NOT NULL,
  status               text NOT NULL DEFAULT 'assigned',
  assigned_at          timestamptz NOT NULL DEFAULT now(),
  due_at               timestamptz,
  completed_at         timestamptz,
  score                numeric,
  score_detail         jsonb,
  cancel_reason        text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_assessment_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_assessment_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_assessment PRIMARY KEY (id),
  CONSTRAINT fk_zc_assessment_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_assessment_enrolment FOREIGN KEY (tenant_id, enrolment_id)
    REFERENCES zc_enrolment (tenant_id, id),
  CONSTRAINT fk_zc_assessment_template_version FOREIGN KEY (tenant_id, template_version_id)
    REFERENCES zc_assessment_template_version (tenant_id, id),
  CONSTRAINT uq_zc_assessment_fk_target    UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_assessment_fk_enrolment UNIQUE (tenant_id, id, enrolment_id),
  CONSTRAINT ck_zc_assessment_status CHECK (status IN ('assigned','in_progress','completed','cancelled','expired')),
  CONSTRAINT ck_zc_assessment_completed CHECK (status <> 'completed' OR completed_at IS NOT NULL),
  CONSTRAINT ck_zc_assessment_cancelled CHECK (status <> 'cancelled' OR nullif(btrim(cancel_reason), '') IS NOT NULL)
);
COMMENT ON COLUMN zc_assessment.template_version_id IS
  'Pinned at assignment (trg_zc_assessment_pins): responses always score against the version live when assigned.';
COMMENT ON COLUMN zc_assessment.score_detail IS 'jsonb: explainability payload for the computed score.';
CREATE INDEX ix_zc_assessment_enrolment ON zc_assessment (tenant_id, enrolment_id, assigned_at DESC);

CREATE TABLE zc_assessment_response (
  id             bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id      bigint NOT NULL,
  assessment_id  bigint NOT NULL,
  question_code  text NOT NULL,
  answer_value   text,
  answered_at    timestamptz NOT NULL DEFAULT now(),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_assessment_response_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_assessment_response PRIMARY KEY (id),
  CONSTRAINT fk_zc_assessment_response_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_assessment_response_assessment FOREIGN KEY (tenant_id, assessment_id)
    REFERENCES zc_assessment (tenant_id, id),
  CONSTRAINT ck_zc_assessment_response_question CHECK (btrim(question_code) <> '')
);
COMMENT ON TABLE zc_assessment_response IS 'Append-only. A changed answer is a new row; the latest answered_at wins.';
CREATE INDEX ix_zc_assessment_response_assessment
  ON zc_assessment_response (tenant_id, assessment_id, question_code, answered_at DESC);

CREATE TABLE zc_risk_classification (
  id               bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id        bigint NOT NULL,
  enrolment_id     bigint NOT NULL,
  assessment_id    bigint,
  overrides_id     bigint,
  priority_tier    text NOT NULL,   -- TODO: approved tier names
  derived_from     text NOT NULL,
  is_override      boolean NOT NULL DEFAULT false,
  override_reason  text,
  rationale        text,
  classified_at    timestamptz NOT NULL DEFAULT now(),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_risk_classification_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_risk_classification PRIMARY KEY (id),
  CONSTRAINT fk_zc_risk_classification_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_risk_classification_enrolment FOREIGN KEY (tenant_id, enrolment_id)
    REFERENCES zc_enrolment (tenant_id, id),
  CONSTRAINT fk_zc_risk_classification_assessment FOREIGN KEY (tenant_id, assessment_id, enrolment_id)
    REFERENCES zc_assessment (tenant_id, id, enrolment_id),
  CONSTRAINT fk_zc_risk_classification_overrides FOREIGN KEY (tenant_id, overrides_id, enrolment_id)
    REFERENCES zc_risk_classification (tenant_id, id, enrolment_id),
  CONSTRAINT uq_zc_risk_classification_fk_enrolment UNIQUE (tenant_id, id, enrolment_id),
  CONSTRAINT ck_zc_risk_classification_tier CHECK (btrim(priority_tier) <> ''),
  CONSTRAINT ck_zc_risk_classification_derived_from CHECK (derived_from IN ('assessment','manual')),
  CONSTRAINT ck_zc_risk_classification_derivation CHECK (derived_from <> 'assessment' OR assessment_id IS NOT NULL),
  CONSTRAINT ck_zc_risk_classification_override CHECK (
    is_override = (overrides_id IS NOT NULL)
    AND (NOT is_override OR nullif(btrim(override_reason), '') IS NOT NULL)),
  CONSTRAINT ck_zc_risk_classification_not_self CHECK (overrides_id IS NULL OR overrides_id <> id)
);
COMMENT ON TABLE zc_risk_classification IS
  'Append-only programme/operational priority tier. NEVER a diagnosis. An override inserts a new row pointing back via overrides_id.';
CREATE INDEX ix_zc_risk_classification_current ON zc_risk_classification (tenant_id, enrolment_id, classified_at DESC);

-- =============================================================================
-- 07  CARE PLANNING
-- =============================================================================

CREATE TABLE zc_care_plan (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  enrolment_id          bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  supersedes_plan_id    bigint,
  version_number        integer NOT NULL DEFAULT 1,
  status                text NOT NULL DEFAULT 'draft',
  summary               text,
  submitted_at          timestamptz,
  approved_by           text,
  approved_at           timestamptz,
  activated_at          timestamptz,
  rejection_reason      text,
  closed_at             timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_care_plan_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_care_plan_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_care_plan PRIMARY KEY (id),
  CONSTRAINT fk_zc_care_plan_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_care_plan_fk_target    UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_care_plan_fk_version   UNIQUE (tenant_id, id, programme_version_id),
  CONSTRAINT uq_zc_care_plan_fk_enrolment UNIQUE (tenant_id, id, enrolment_id),
  CONSTRAINT uq_zc_care_plan_version_number UNIQUE (tenant_id, enrolment_id, version_number),
  -- Plan version must match the enrolment's version.
  CONSTRAINT fk_zc_care_plan_enrolment FOREIGN KEY (tenant_id, enrolment_id, programme_version_id)
    REFERENCES zc_enrolment (tenant_id, id, programme_version_id),
  CONSTRAINT fk_zc_care_plan_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT fk_zc_care_plan_supersedes FOREIGN KEY (tenant_id, supersedes_plan_id, enrolment_id)
    REFERENCES zc_care_plan (tenant_id, id, enrolment_id),
  CONSTRAINT ck_zc_care_plan_status CHECK (status IN (
    'draft','pending_approval','active','revised','suspended','completed','closed','rejected')),
  CONSTRAINT ck_zc_care_plan_version_number CHECK (version_number > 0),
  -- Active plans need an approver.
  CONSTRAINT ck_zc_care_plan_activation CHECK (
    status <> 'active'
    OR (nullif(btrim(approved_by), '') IS NOT NULL AND approved_at IS NOT NULL AND activated_at IS NOT NULL)),
  CONSTRAINT ck_zc_care_plan_approval_pair CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
  CONSTRAINT ck_zc_care_plan_activation_after_approval CHECK (
    activated_at IS NULL OR (approved_at IS NOT NULL AND activated_at >= approved_at)),
  CONSTRAINT ck_zc_care_plan_submitted CHECK (status <> 'pending_approval' OR submitted_at IS NOT NULL),
  CONSTRAINT ck_zc_care_plan_rejection CHECK (status <> 'rejected' OR nullif(btrim(rejection_reason), '') IS NOT NULL),
  CONSTRAINT ck_zc_care_plan_not_self_superseding CHECK (supersedes_plan_id IS NULL OR supersedes_plan_id <> id)
);
COMMENT ON TABLE zc_care_plan IS
  'Care-coordination artefact, NOT a clinical order. Absence of approval is never approval.';
CREATE UNIQUE INDEX uq_zc_care_plan_one_active ON zc_care_plan (tenant_id, enrolment_id) WHERE status = 'active';
CREATE INDEX ix_zc_care_plan_approval ON zc_care_plan (tenant_id, submitted_at) WHERE status = 'pending_approval';

CREATE TABLE zc_goal (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  care_plan_id          bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  goal_type_id          bigint NOT NULL,
  description           text NOT NULL,
  target_value          numeric,
  target_unit           text,
  target_criteria       text,
  target_date           date,
  status                text NOT NULL DEFAULT 'active',
  status_reason         text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_goal_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_goal_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_goal PRIMARY KEY (id),
  CONSTRAINT fk_zc_goal_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  -- Goal type must match the plan's programme version.
  CONSTRAINT fk_zc_goal_care_plan FOREIGN KEY (tenant_id, care_plan_id, programme_version_id)
    REFERENCES zc_care_plan (tenant_id, id, programme_version_id),
  CONSTRAINT fk_zc_goal_goal_type FOREIGN KEY (tenant_id, goal_type_id, programme_version_id)
    REFERENCES zc_goal_type (tenant_id, id, programme_version_id),
  CONSTRAINT uq_zc_goal_fk_care_plan UNIQUE (tenant_id, id, care_plan_id),
  CONSTRAINT ck_zc_goal_description CHECK (btrim(description) <> ''),
  CONSTRAINT ck_zc_goal_measurable CHECK (
    (target_value IS NOT NULL AND nullif(btrim(target_unit), '') IS NOT NULL)
    OR nullif(btrim(target_criteria), '') IS NOT NULL),
  CONSTRAINT ck_zc_goal_status CHECK (status IN ('proposed','active','achieved','not_achieved','discontinued'))
);
CREATE INDEX ix_zc_goal_care_plan ON zc_goal (tenant_id, care_plan_id);

CREATE TABLE zc_intervention (
  id             bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id      bigint NOT NULL,
  care_plan_id   bigint NOT NULL,
  goal_id        bigint, -- NULL = plan-level
  owner_role     text NOT NULL,
  description    text NOT NULL,
  frequency      text,
  status         text NOT NULL DEFAULT 'planned',
  status_reason  text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_intervention_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_intervention_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_intervention PRIMARY KEY (id),
  CONSTRAINT fk_zc_intervention_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_intervention_care_plan FOREIGN KEY (tenant_id, care_plan_id)
    REFERENCES zc_care_plan (tenant_id, id),
  -- Goal must be in the same plan.
  CONSTRAINT fk_zc_intervention_goal FOREIGN KEY (tenant_id, goal_id, care_plan_id)
    REFERENCES zc_goal (tenant_id, id, care_plan_id),
  CONSTRAINT ck_zc_intervention_owner_role CHECK (btrim(owner_role) <> ''),
  CONSTRAINT ck_zc_intervention_description CHECK (btrim(description) <> ''),
  CONSTRAINT ck_zc_intervention_status CHECK (status IN ('planned','active','completed','discontinued'))
);
CREATE INDEX ix_zc_intervention_care_plan ON zc_intervention (tenant_id, care_plan_id);

CREATE TABLE zc_care_plan_link (
  id            bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id     bigint NOT NULL,
  care_plan_id  bigint NOT NULL,
  link_type     text NOT NULL,
  ref_id        bigint NOT NULL, -- polymorphic, no FK
  note          text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_care_plan_link_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_care_plan_link_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_care_plan_link PRIMARY KEY (id),
  CONSTRAINT fk_zc_care_plan_link_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_care_plan_link_care_plan FOREIGN KEY (tenant_id, care_plan_id)
    REFERENCES zc_care_plan (tenant_id, id),
  CONSTRAINT uq_zc_care_plan_link_target UNIQUE (tenant_id, care_plan_id, link_type, ref_id),
  CONSTRAINT ck_zc_care_plan_link_type CHECK (link_type IN (
    'referral','task','transition','medication_coordination','monitoring_schedule',
    'assessment','care_gap','outreach_request'))
);

CREATE TABLE zc_plan_review (
  id            bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id     bigint NOT NULL,
  care_plan_id  bigint NOT NULL,
  outcome       text NOT NULL,
  reviewed_by   text NOT NULL,
  reviewed_at   timestamptz NOT NULL DEFAULT now(),
  notes         text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_plan_review_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_plan_review PRIMARY KEY (id),
  CONSTRAINT fk_zc_plan_review_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_plan_review_care_plan FOREIGN KEY (tenant_id, care_plan_id)
    REFERENCES zc_care_plan (tenant_id, id),
  CONSTRAINT ck_zc_plan_review_outcome CHECK (outcome IN (
    'approved','rejected','changes_requested','continue_unchanged','revise','close')),
  CONSTRAINT ck_zc_plan_review_reviewer CHECK (btrim(reviewed_by) <> ''),
  CONSTRAINT ck_zc_plan_review_reason CHECK (
    outcome IN ('approved','continue_unchanged') OR nullif(btrim(notes), '') IS NOT NULL)
);
COMMENT ON TABLE zc_plan_review IS 'Append-only: every approval round and review is retained.';
CREATE INDEX ix_zc_plan_review_care_plan ON zc_plan_review (tenant_id, care_plan_id, reviewed_at DESC);

-- =============================================================================
-- 08  CARE WORK MANAGEMENT
-- =============================================================================

CREATE TABLE zc_task (
  id                 bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id          bigint NOT NULL,
  enrolment_id       bigint NOT NULL,
  member_id          bigint NOT NULL,
  task_type          text NOT NULL,
  title              text,
  description        text,
  priority           smallint NOT NULL DEFAULT 3,   -- 1 = most urgent
  status             text NOT NULL DEFAULT 'assigned',
  assigned_to_actor  text,
  assigned_to_role   text,
  due_at             timestamptz,   -- SLA deadline
  sla_state          text NOT NULL DEFAULT 'on_track',   -- set by the SLA job
  origin_type        text NOT NULL DEFAULT 'manual',
  origin_id          bigint,        -- polymorphic, no FK
  blocked_reason     text,
  completed_at       timestamptz,
  completed_by       text,
  completion_note    text,
  cancelled_at       timestamptz,
  cancel_reason      text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_task_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_task_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_task PRIMARY KEY (id),
  CONSTRAINT fk_zc_task_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_task_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT uq_zc_task_fk_member UNIQUE (tenant_id, id, member_id),
  CONSTRAINT fk_zc_task_enrolment FOREIGN KEY (tenant_id, enrolment_id, member_id)
    REFERENCES zc_enrolment (tenant_id, id, member_id),
  CONSTRAINT fk_zc_task_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT ck_zc_task_task_type CHECK (btrim(task_type) <> ''),
  CONSTRAINT ck_zc_task_priority CHECK (priority BETWEEN 1 AND 5),
  CONSTRAINT ck_zc_task_status CHECK (status IN ('assigned','accepted','in_progress','blocked','completed','cancelled')),
  CONSTRAINT ck_zc_task_sla_state CHECK (sla_state IN ('on_track','at_risk','breached','not_applicable')),
  -- Assigned to a person or a role.
  CONSTRAINT ck_zc_task_assignee CHECK (
    nullif(btrim(assigned_to_actor), '') IS NOT NULL OR nullif(btrim(assigned_to_role), '') IS NOT NULL),
  CONSTRAINT ck_zc_task_origin_type CHECK (origin_type IN (
    'manual','care_plan','intervention','care_gap','referral','transition','refill_request',
    'assessment','observation','outreach_request','inbound_message','dead_letter','task_template')),
  CONSTRAINT ck_zc_task_origin CHECK (origin_type = 'manual' OR origin_id IS NOT NULL),
  CONSTRAINT ck_zc_task_blocked CHECK (status <> 'blocked' OR nullif(btrim(blocked_reason), '') IS NOT NULL),
  CONSTRAINT ck_zc_task_completion CHECK (
    status <> 'completed' OR (nullif(btrim(completed_by), '') IS NOT NULL AND completed_at IS NOT NULL)),
  CONSTRAINT ck_zc_task_cancellation CHECK (
    status <> 'cancelled' OR nullif(btrim(cancel_reason), '') IS NOT NULL)
);
-- Work queues.
CREATE INDEX ix_zc_task_queue
  ON zc_task (tenant_id, assigned_to_actor, status, priority, due_at)
  WHERE status IN ('assigned','accepted','in_progress','blocked');
CREATE INDEX ix_zc_task_team_queue
  ON zc_task (tenant_id, assigned_to_role, status, priority, due_at)
  WHERE status IN ('assigned','accepted','in_progress','blocked');
CREATE INDEX ix_zc_task_enrolment ON zc_task (tenant_id, enrolment_id, status);

CREATE TABLE zc_task_dependency (
  id                  bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id           bigint NOT NULL,
  task_id             bigint NOT NULL,
  depends_on_task_id  bigint NOT NULL,
  removed_at          timestamptz,   -- soft delete
  removed_by          text,
  removal_reason      text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_task_dependency_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_task_dependency_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_task_dependency PRIMARY KEY (id),
  CONSTRAINT fk_zc_task_dependency_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_task_dependency_task FOREIGN KEY (tenant_id, task_id)
    REFERENCES zc_task (tenant_id, id),
  CONSTRAINT fk_zc_task_dependency_depends_on FOREIGN KEY (tenant_id, depends_on_task_id)
    REFERENCES zc_task (tenant_id, id),
  CONSTRAINT ck_zc_task_dependency_not_self CHECK (task_id <> depends_on_task_id),
  CONSTRAINT ck_zc_task_dependency_removal CHECK (
    (removed_at IS NULL AND removed_by IS NULL AND removal_reason IS NULL)
    OR (removed_at IS NOT NULL
        AND nullif(btrim(removed_by), '') IS NOT NULL
        AND nullif(btrim(removal_reason), '') IS NOT NULL))
);
CREATE UNIQUE INDEX uq_zc_task_dependency_pair
  ON zc_task_dependency (tenant_id, task_id, depends_on_task_id) WHERE removed_at IS NULL;
CREATE INDEX ix_zc_task_dependency_depends_on ON zc_task_dependency (tenant_id, depends_on_task_id);

CREATE TABLE zc_task_assignment_history (
  id           bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id    bigint NOT NULL,
  task_id      bigint NOT NULL,
  from_actor   text,
  to_actor     text,
  from_role    text,
  to_role      text,
  reason       text,
  assigned_at  timestamptz NOT NULL DEFAULT now(),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_task_assignment_history_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_task_assignment_history PRIMARY KEY (id),
  CONSTRAINT fk_zc_task_assignment_history_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_task_assignment_history_task FOREIGN KEY (tenant_id, task_id)
    REFERENCES zc_task (tenant_id, id),
  CONSTRAINT ck_zc_task_assignment_history_target CHECK (
    nullif(btrim(to_actor), '') IS NOT NULL OR nullif(btrim(to_role), '') IS NOT NULL)
);
CREATE INDEX ix_zc_task_assignment_history_task ON zc_task_assignment_history (tenant_id, task_id, assigned_at);

CREATE TABLE zc_work_queue_definition (
  id                 bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id          bigint NOT NULL,
  code               text NOT NULL,
  name               text NOT NULL,
  role_code          text,
  scope              text NOT NULL,
  filter_definition  jsonb NOT NULL DEFAULT '{}'::jsonb,
  is_active          boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_work_queue_definition_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_work_queue_definition_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_work_queue_definition PRIMARY KEY (id),
  CONSTRAINT fk_zc_work_queue_definition_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_work_queue_definition_code UNIQUE (tenant_id, code),
  CONSTRAINT ck_zc_work_queue_definition_code  CHECK (btrim(code) <> ''),
  CONSTRAINT ck_zc_work_queue_definition_name  CHECK (btrim(name) <> ''),
  CONSTRAINT ck_zc_work_queue_definition_scope CHECK (btrim(scope) <> '')
);
COMMENT ON TABLE zc_work_queue_definition IS
  'A queue is a live query over zc_task, not a stored relationship. No FK beyond tenant_id.';
COMMENT ON COLUMN zc_work_queue_definition.filter_definition IS
  'jsonb: the stored queue filter. Read by the query layer only; no domain invariant lives in it.';

-- =============================================================================
-- 09  REFERRALS, TRANSITIONS & PROVIDER PARTICIPATION
-- =============================================================================

CREATE TABLE zc_referral (
  id                      bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id               bigint NOT NULL,
  enrolment_id            bigint NOT NULL,
  member_id               bigint NOT NULL,
  referral_type_id        bigint NOT NULL,
  receiving_org_id        bigint, -- NULL until receiver is known
  receiving_provider_ref  text,
  status                  text NOT NULL DEFAULT 'created',
  reason                  text NOT NULL,
  routed_at               timestamptz,
  responded_at            timestamptz,
  decline_reason          text,
  completed_at            timestamptz,
  confirmed_by            text,
  confirmation_source     text,
  evidence_ref            text,
  cancel_reason           text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_referral_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_referral_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_referral PRIMARY KEY (id),
  CONSTRAINT fk_zc_referral_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_referral_enrolment FOREIGN KEY (tenant_id, enrolment_id, member_id)
    REFERENCES zc_enrolment (tenant_id, id, member_id),
  CONSTRAINT fk_zc_referral_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT fk_zc_referral_referral_type FOREIGN KEY (tenant_id, referral_type_id)
    REFERENCES zc_referral_type (tenant_id, id),
  CONSTRAINT fk_zc_referral_receiving_org FOREIGN KEY (tenant_id, receiving_org_id)
    REFERENCES zc_organisation (tenant_id, id),
  CONSTRAINT ck_zc_referral_status CHECK (status IN (
    'created','routed','accepted','declined','in_progress','completed','cancelled','expired')),
  CONSTRAINT ck_zc_referral_reason CHECK (btrim(reason) <> ''),
  CONSTRAINT ck_zc_referral_confirmation_source CHECK (
    confirmation_source IN ('provider','clinician','member_reported')),
  -- Completion needs provider or clinician confirmation.
  CONSTRAINT ck_zc_referral_completion CHECK (
    status <> 'completed'
    OR (nullif(btrim(confirmed_by), '') IS NOT NULL
        AND confirmation_source IN ('provider','clinician')
        AND completed_at IS NOT NULL)),
  CONSTRAINT ck_zc_referral_routing CHECK (
    status IN ('created','cancelled','expired')
    OR receiving_org_id IS NOT NULL
    OR nullif(btrim(receiving_provider_ref), '') IS NOT NULL),
  CONSTRAINT ck_zc_referral_decline CHECK (status <> 'declined' OR nullif(btrim(decline_reason), '') IS NOT NULL),
  CONSTRAINT ck_zc_referral_cancellation CHECK (status <> 'cancelled' OR nullif(btrim(cancel_reason), '') IS NOT NULL)
);
COMMENT ON TABLE zc_referral IS 'Coordination record, NOT a clinical order.';
CREATE INDEX ix_zc_referral_enrolment ON zc_referral (tenant_id, enrolment_id, status);

CREATE TABLE zc_transition (
  id                bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id         bigint NOT NULL,
  enrolment_id      bigint NOT NULL,
  transition_type   text NOT NULL,
  status            text NOT NULL DEFAULT 'notified',
  occurred_at       timestamptz,
  source_ref        text,
  follow_up_due_at  timestamptz,
  completed_at      timestamptz,
  cancel_reason     text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_transition_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_transition_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_transition PRIMARY KEY (id),
  CONSTRAINT fk_zc_transition_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_transition_enrolment FOREIGN KEY (tenant_id, enrolment_id)
    REFERENCES zc_enrolment (tenant_id, id),
  CONSTRAINT ck_zc_transition_type CHECK (btrim(transition_type) <> ''),
  CONSTRAINT ck_zc_transition_status CHECK (status IN ('notified','in_progress','completed','cancelled')),
  CONSTRAINT ck_zc_transition_completed CHECK (status <> 'completed' OR completed_at IS NOT NULL),
  CONSTRAINT ck_zc_transition_cancelled CHECK (status <> 'cancelled' OR nullif(btrim(cancel_reason), '') IS NOT NULL)
);
CREATE INDEX ix_zc_transition_enrolment ON zc_transition (tenant_id, enrolment_id, status);

CREATE TABLE zc_provider_participation (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  organisation_id       bigint NOT NULL,
  status                text NOT NULL DEFAULT 'invited',
  agreement_ref         text,
  joined_at             timestamptz,
  exited_at             timestamptz,
  exit_reason           text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_provider_participation_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_provider_participation_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_provider_participation PRIMARY KEY (id),
  CONSTRAINT fk_zc_provider_participation_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_provider_participation_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT fk_zc_provider_participation_organisation FOREIGN KEY (tenant_id, organisation_id)
    REFERENCES zc_organisation (tenant_id, id),
  CONSTRAINT uq_zc_provider_participation_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT ck_zc_provider_participation_status CHECK (status IN ('invited','active','suspended','withdrawn')),
  CONSTRAINT ck_zc_provider_participation_joined CHECK (status <> 'active' OR joined_at IS NOT NULL),
  CONSTRAINT ck_zc_provider_participation_exit CHECK (
    status <> 'withdrawn' OR (exited_at IS NOT NULL AND nullif(btrim(exit_reason), '') IS NOT NULL))
);
CREATE UNIQUE INDEX uq_zc_provider_participation_live
  ON zc_provider_participation (tenant_id, programme_version_id, organisation_id)
  WHERE status IN ('invited','active','suspended');

CREATE TABLE zc_provider_action (
  id                bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id         bigint NOT NULL,
  participation_id  bigint NOT NULL,
  subject_type      text NOT NULL,
  subject_id        bigint NOT NULL, -- polymorphic, no FK
  action_code       text NOT NULL,
  action_at         timestamptz NOT NULL DEFAULT now(),
  actor_ref         text,
  evidence_ref      text,
  notes             text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_provider_action_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_provider_action PRIMARY KEY (id),
  CONSTRAINT fk_zc_provider_action_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_provider_action_participation FOREIGN KEY (tenant_id, participation_id)
    REFERENCES zc_provider_participation (tenant_id, id),
  CONSTRAINT ck_zc_provider_action_subject_type CHECK (subject_type IN (
    'referral','transition','care_plan','refill_request','task','enrolment','observation')),
  CONSTRAINT ck_zc_provider_action_action_code CHECK (btrim(action_code) <> '')
);
COMMENT ON TABLE zc_provider_action IS
  'Append-only evidence of provider actions; recordable only for a registered participant in that programme version.';
CREATE INDEX ix_zc_provider_action_subject ON zc_provider_action (tenant_id, subject_type, subject_id);

-- =============================================================================
-- 10  MEDICATION COORDINATION  (ZCare never prescribes or dispenses)
-- =============================================================================

CREATE TABLE zc_medication_coordination (
  id                  bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id           bigint NOT NULL,
  enrolment_id        bigint NOT NULL,
  plan_ref            text NOT NULL,   -- plan id in the provider system
  plan_ref_system     text NOT NULL,   -- free text from adapter
  medication_display  text,
  status              text NOT NULL DEFAULT 'active',
  started_on          date,
  ended_on            date,
  end_reason          text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_medication_coordination_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_medication_coordination_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_medication_coordination PRIMARY KEY (id),
  CONSTRAINT fk_zc_medication_coordination_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_medication_coordination_enrolment FOREIGN KEY (tenant_id, enrolment_id)
    REFERENCES zc_enrolment (tenant_id, id),
  CONSTRAINT uq_zc_medication_coordination_fk_enrolment UNIQUE (tenant_id, id, enrolment_id),
  CONSTRAINT ck_zc_medication_coordination_plan_ref CHECK (btrim(plan_ref) <> ''),
  CONSTRAINT ck_zc_medication_coordination_plan_ref_system CHECK (btrim(plan_ref_system) <> ''),
  CONSTRAINT ck_zc_medication_coordination_status CHECK (status IN ('active','paused','ended')),
  CONSTRAINT ck_zc_medication_coordination_ended CHECK (
    status <> 'ended' OR (ended_on IS NOT NULL AND nullif(btrim(end_reason), '') IS NOT NULL)),
  CONSTRAINT ck_zc_medication_coordination_dates CHECK (
    started_on IS NULL OR ended_on IS NULL OR ended_on >= started_on)
);
CREATE INDEX ix_zc_medication_coordination_enrolment ON zc_medication_coordination (tenant_id, enrolment_id);

CREATE TABLE zc_refill_request (
  id                          bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id                   bigint NOT NULL,
  enrolment_id                bigint NOT NULL,
  medication_coordination_id  bigint NOT NULL,
  status                      text NOT NULL DEFAULT 'requested',
  due_on                      date,
  is_overdue                  boolean NOT NULL DEFAULT false,   -- independent of status
  routed_to_ref               text,
  fulfilment_state            text NOT NULL DEFAULT 'pending',  -- separate from routing
  confirmation_source         text,
  fulfilment_recorded_at      timestamptz,
  fulfilment_recorded_by      text,
  concluded_at                timestamptz,
  cancel_reason               text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_refill_request_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_refill_request_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_refill_request PRIMARY KEY (id),
  CONSTRAINT fk_zc_refill_request_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_refill_request_enrolment FOREIGN KEY (tenant_id, enrolment_id)
    REFERENCES zc_enrolment (tenant_id, id),
  CONSTRAINT fk_zc_refill_request_medication_coordination
    FOREIGN KEY (tenant_id, medication_coordination_id, enrolment_id)
    REFERENCES zc_medication_coordination (tenant_id, id, enrolment_id),
  CONSTRAINT ck_zc_refill_request_status CHECK (status IN ('requested','routed','in_progress','concluded','cancelled')),
  CONSTRAINT ck_zc_refill_request_fulfilment_state CHECK (
    fulfilment_state IN ('pending','member_reported','confirmed','not_fulfilled')),
  CONSTRAINT ck_zc_refill_request_confirmation_source CHECK (
    confirmation_source IN ('member_reported','pharmacy','provider')),
  -- Confirmed needs pharmacy or provider.
  CONSTRAINT ck_zc_refill_confirmed_requires_authorised_source CHECK (
    fulfilment_state <> 'confirmed' OR confirmation_source IN ('pharmacy','provider')),
  CONSTRAINT ck_zc_refill_request_member_reported CHECK (
    fulfilment_state <> 'member_reported' OR confirmation_source = 'member_reported'),
  CONSTRAINT ck_zc_refill_request_fulfilment_evidence CHECK (
    (fulfilment_state = 'pending' AND confirmation_source IS NULL)
    OR (fulfilment_state <> 'pending' AND confirmation_source IS NOT NULL AND fulfilment_recorded_at IS NOT NULL)),
  CONSTRAINT ck_zc_refill_request_concluded CHECK (status <> 'concluded' OR concluded_at IS NOT NULL),
  CONSTRAINT ck_zc_refill_request_cancelled CHECK (status <> 'cancelled' OR nullif(btrim(cancel_reason), '') IS NOT NULL)
);
CREATE UNIQUE INDEX uq_zc_refill_one_open
  ON zc_refill_request (tenant_id, medication_coordination_id)
  WHERE status IN ('requested','routed','in_progress');
CREATE INDEX ix_zc_refill_overdue
  ON zc_refill_request (tenant_id, due_on)
  WHERE is_overdue AND status IN ('requested','routed','in_progress');

CREATE TABLE zc_adherence_event (
  id                          bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id                   bigint NOT NULL,
  enrolment_id                bigint NOT NULL,
  medication_coordination_id  bigint, -- optional
  event_type                  text NOT NULL,
  source                      text NOT NULL,
  occurred_at                 timestamptz NOT NULL,
  notes                       text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_adherence_event_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_adherence_event_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_adherence_event PRIMARY KEY (id),
  CONSTRAINT fk_zc_adherence_event_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_adherence_event_enrolment FOREIGN KEY (tenant_id, enrolment_id)
    REFERENCES zc_enrolment (tenant_id, id),
  CONSTRAINT fk_zc_adherence_event_medication_coordination
    FOREIGN KEY (tenant_id, medication_coordination_id, enrolment_id)
    REFERENCES zc_medication_coordination (tenant_id, id, enrolment_id),
  CONSTRAINT ck_zc_adherence_event_type CHECK (btrim(event_type) <> ''),
  CONSTRAINT ck_zc_adherence_event_source CHECK (source IN ('member_reported','care_team','pharmacy','provider'))
);
CREATE INDEX ix_zc_adherence_event_enrolment ON zc_adherence_event (tenant_id, enrolment_id, occurred_at DESC);

-- =============================================================================
-- 11  OBSERVATION & MONITORING
-- =============================================================================

CREATE TABLE zc_monitoring_schedule (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  enrolment_id          bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  observation_type_id   bigint NOT NULL,
  cadence_days          integer NOT NULL,
  starts_on             date NOT NULL DEFAULT current_date,
  next_due_on           date,
  is_active             boolean NOT NULL DEFAULT true,
  ended_on              date,
  end_reason            text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_monitoring_schedule_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_monitoring_schedule_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_monitoring_schedule PRIMARY KEY (id),
  CONSTRAINT fk_zc_monitoring_schedule_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_monitoring_schedule_enrolment FOREIGN KEY (tenant_id, enrolment_id, programme_version_id)
    REFERENCES zc_enrolment (tenant_id, id, programme_version_id),
  CONSTRAINT fk_zc_monitoring_schedule_observation_type
    FOREIGN KEY (tenant_id, observation_type_id, programme_version_id)
    REFERENCES zc_observation_type (tenant_id, id, programme_version_id),
  CONSTRAINT ck_zc_monitoring_schedule_cadence CHECK (cadence_days > 0),
  CONSTRAINT ck_zc_monitoring_schedule_ended CHECK (is_active OR ended_on IS NOT NULL)
);
CREATE UNIQUE INDEX uq_zc_monitoring_schedule_active
  ON zc_monitoring_schedule (tenant_id, enrolment_id, observation_type_id) WHERE is_active;
CREATE INDEX ix_zc_monitoring_schedule_due ON zc_monitoring_schedule (tenant_id, next_due_on) WHERE is_active;

CREATE TABLE zc_observation (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  enrolment_id          bigint NOT NULL,
  member_id             bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  observation_type_id   bigint NOT NULL,
  value_numeric         numeric NOT NULL,
  unit                  text NOT NULL,
  observed_at           timestamptz NOT NULL,
  source                text NOT NULL,
  source_ref            text,
  loinc_code            text,
  is_invalidated        boolean NOT NULL DEFAULT false,
  invalidated_at        timestamptz,
  invalidated_by        text,
  invalidation_reason   text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_observation_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_observation_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_observation PRIMARY KEY (id),
  CONSTRAINT fk_zc_observation_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  -- Type must match the enrolment's version; unit must match the type.
  CONSTRAINT fk_zc_observation_enrolment
    FOREIGN KEY (tenant_id, enrolment_id, programme_version_id, member_id)
    REFERENCES zc_enrolment (tenant_id, id, programme_version_id, member_id),
  CONSTRAINT fk_zc_observation_type_version
    FOREIGN KEY (tenant_id, observation_type_id, programme_version_id)
    REFERENCES zc_observation_type (tenant_id, id, programme_version_id),
  CONSTRAINT fk_zc_observation_type_unit
    FOREIGN KEY (tenant_id, observation_type_id, unit)
    REFERENCES zc_observation_type (tenant_id, id, unit) ON UPDATE RESTRICT,
  CONSTRAINT fk_zc_observation_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT ck_zc_observation_source CHECK (source IN (
    'member_reported','care_team_recorded','provider','laboratory','device')),
  CONSTRAINT ck_zc_observation_invalidation CHECK (
    (NOT is_invalidated AND invalidated_at IS NULL AND invalidated_by IS NULL AND invalidation_reason IS NULL)
    OR (is_invalidated
        AND invalidated_at IS NOT NULL
        AND nullif(btrim(invalidated_by), '') IS NOT NULL
        AND nullif(btrim(invalidation_reason), '') IS NOT NULL))
);
COMMENT ON TABLE zc_observation IS
  'Programme-required measurement of record. A wrong value is invalidated, never edited (trg_zc_observation_correction_only).';
CREATE INDEX ix_zc_observation_trend
  ON zc_observation (tenant_id, enrolment_id, observation_type_id, observed_at DESC)
  WHERE NOT is_invalidated;

-- =============================================================================
-- 12  CARE GAPS
-- =============================================================================

CREATE TABLE zc_care_gap (
  id                    bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id             bigint NOT NULL,
  enrolment_id          bigint NOT NULL,
  member_id             bigint NOT NULL,
  programme_version_id  bigint NOT NULL,
  gap_rule_id           bigint NOT NULL,
  gap_type              text NOT NULL,
  rule_version          integer NOT NULL,
  gap_key               text NOT NULL,
  period_key            text NOT NULL,
  status                text NOT NULL DEFAULT 'detected',
  detect_reason         text NOT NULL,
  evaluated_data        jsonb NOT NULL,
  detected_at           timestamptz NOT NULL DEFAULT now(),
  due_on                date,
  assigned_task_id      bigint, -- NULL until a task is created
  action_reason         text,
  actioned_by           text,
  actioned_at           timestamptz,
  reopened_count        integer NOT NULL DEFAULT 0,
  last_reopened_at      timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_care_gap_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_care_gap_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_care_gap PRIMARY KEY (id),
  CONSTRAINT fk_zc_care_gap_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_care_gap_enrolment
    FOREIGN KEY (tenant_id, enrolment_id, programme_version_id, member_id)
    REFERENCES zc_enrolment (tenant_id, id, programme_version_id, member_id),
  CONSTRAINT fk_zc_care_gap_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT fk_zc_care_gap_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  -- gap_type and rule_version must match the rule.
  CONSTRAINT fk_zc_care_gap_gap_rule
    FOREIGN KEY (tenant_id, gap_rule_id, programme_version_id, gap_type, rule_version)
    REFERENCES zc_gap_rule (tenant_id, id, programme_version_id, gap_type, rule_version),
  CONSTRAINT fk_zc_care_gap_assigned_task FOREIGN KEY (tenant_id, assigned_task_id, member_id)
    REFERENCES zc_task (tenant_id, id, member_id),
  CONSTRAINT ck_zc_care_gap_status CHECK (status IN ('detected','assigned','closed','suppressed')),
  CONSTRAINT ck_zc_care_gap_gap_key CHECK (btrim(gap_key) <> ''),
  CONSTRAINT ck_zc_care_gap_period_key CHECK (btrim(period_key) <> ''),
  CONSTRAINT ck_zc_care_gap_detect_reason CHECK (btrim(detect_reason) <> ''),
  -- Closing or suppressing needs a reason.
  CONSTRAINT ck_zc_care_gap_action_reason CHECK (
    status NOT IN ('closed','suppressed')
    OR (nullif(btrim(action_reason), '') IS NOT NULL
        AND nullif(btrim(actioned_by), '') IS NOT NULL
        AND actioned_at IS NOT NULL)),
  CONSTRAINT ck_zc_care_gap_assignment CHECK (status <> 'assigned' OR assigned_task_id IS NOT NULL),
  CONSTRAINT ck_zc_care_gap_reopened_count CHECK (reopened_count >= 0)
);
COMMENT ON COLUMN zc_care_gap.evaluated_data IS
  'jsonb: explainability payload - the data the rule evaluated when it raised the gap.';
-- One open gap per member + version + type + period.
CREATE UNIQUE INDEX uq_zc_care_gap_dedup
  ON zc_care_gap (tenant_id, member_id, programme_version_id, gap_type, period_key)
  WHERE status IN ('detected','assigned');
CREATE INDEX ix_zc_care_gap_ageing
  ON zc_care_gap (tenant_id, status, detected_at)
  WHERE status IN ('detected','assigned');
CREATE INDEX ix_zc_care_gap_enrolment ON zc_care_gap (tenant_id, enrolment_id);
-- =============================================================================
-- 13  ENGAGEMENT ORCHESTRATION
-- =============================================================================

CREATE TABLE zc_outreach_request (
  id                  bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id           bigint NOT NULL,
  member_id           bigint NOT NULL,
  enrolment_id        bigint, -- NULL for pre-enrolment invites
  content_class       text NOT NULL,
  purpose             text NOT NULL,
  template_ref        text,
  status              text NOT NULL DEFAULT 'requested',
  requested_at        timestamptz NOT NULL DEFAULT now(),
  scheduled_for       timestamptz,
  dispatched_at       timestamptz,
  completed_at        timestamptz,
  hold_reason         text,
  suppression_reason  text,
  failure_reason      text,
  cancel_reason       text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_outreach_request_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_outreach_request_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_outreach_request PRIMARY KEY (id),
  CONSTRAINT fk_zc_outreach_request_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_outreach_request_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT fk_zc_outreach_request_enrolment FOREIGN KEY (tenant_id, enrolment_id, member_id)
    REFERENCES zc_enrolment (tenant_id, id, member_id),
  CONSTRAINT uq_zc_outreach_request_fk_target UNIQUE (tenant_id, id),
  CONSTRAINT ck_zc_outreach_request_content_class CHECK (content_class IN ('condition_neutral','health_content')),
  -- Health content needs an enrolment.
  CONSTRAINT ck_zc_outreach_request_health_content CHECK (
    content_class <> 'health_content' OR enrolment_id IS NOT NULL),
  CONSTRAINT ck_zc_outreach_request_purpose CHECK (btrim(purpose) <> ''),
  CONSTRAINT ck_zc_outreach_request_status CHECK (status IN (
    'requested','queued','held','dispatched','delivered','completed','failed','cancelled','suppressed')),
  CONSTRAINT ck_zc_outreach_request_held CHECK (status <> 'held' OR nullif(btrim(hold_reason), '') IS NOT NULL),
  CONSTRAINT ck_zc_outreach_request_suppressed CHECK (
    status <> 'suppressed' OR nullif(btrim(suppression_reason), '') IS NOT NULL),
  CONSTRAINT ck_zc_outreach_request_failed CHECK (status <> 'failed' OR nullif(btrim(failure_reason), '') IS NOT NULL),
  CONSTRAINT ck_zc_outreach_request_cancelled CHECK (
    status <> 'cancelled' OR nullif(btrim(cancel_reason), '') IS NOT NULL)
);
CREATE INDEX ix_zc_outreach_pending
  ON zc_outreach_request (tenant_id, scheduled_for, requested_at)
  WHERE status IN ('requested','queued');
CREATE INDEX ix_zc_outreach_request_member ON zc_outreach_request (tenant_id, member_id, requested_at DESC);

CREATE TABLE zc_outreach_delivery (
  id                   bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id            bigint NOT NULL,
  outreach_request_id  bigint NOT NULL,
  attempt_number       integer NOT NULL DEFAULT 1,
  channel              text NOT NULL,
  status               text NOT NULL DEFAULT 'pending',
  channel_message_ref  text,   -- e.g. WhatsApp wamid
  sent_at              timestamptz,
  delivered_at         timestamptz,
  read_at              timestamptz,
  failed_at            timestamptz,
  failure_reason       text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_outreach_delivery_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_outreach_delivery_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_outreach_delivery PRIMARY KEY (id),
  CONSTRAINT fk_zc_outreach_delivery_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_outreach_delivery_outreach_request FOREIGN KEY (tenant_id, outreach_request_id)
    REFERENCES zc_outreach_request (tenant_id, id),
  -- One row per retry.
  CONSTRAINT uq_zc_outreach_delivery_attempt UNIQUE (tenant_id, outreach_request_id, attempt_number),
  CONSTRAINT ck_zc_outreach_delivery_attempt CHECK (attempt_number >= 1),
  CONSTRAINT ck_zc_outreach_delivery_channel CHECK (btrim(channel) <> ''),
  CONSTRAINT ck_zc_outreach_delivery_status CHECK (status IN ('pending','sent','delivered','read','failed')),
  CONSTRAINT ck_zc_outreach_delivery_failed CHECK (
    status <> 'failed' OR (failed_at IS NOT NULL AND nullif(btrim(failure_reason), '') IS NOT NULL))
);
CREATE UNIQUE INDEX uq_zc_outreach_delivery_channel_ref
  ON zc_outreach_delivery (tenant_id, channel, channel_message_ref)
  WHERE channel_message_ref IS NOT NULL;

CREATE TABLE zc_inbound_message (
  id                   bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id            bigint NOT NULL,
  member_id            bigint, -- NULL until sender is matched
  channel              text NOT NULL,
  channel_message_ref  text NOT NULL,   -- e.g. wamid
  sender_address       text NOT NULL,
  context_message_ref  text,            -- message being replied to
  received_at          timestamptz NOT NULL DEFAULT now(),
  body                 text,
  interpreted_as       text,
  interpreted_at       timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_inbound_message_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_inbound_message_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_inbound_message PRIMARY KEY (id),
  CONSTRAINT fk_zc_inbound_message_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_inbound_message_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  -- Dedupe webhook redeliveries.
  CONSTRAINT uq_zc_inbound_message_dedup UNIQUE (tenant_id, channel, channel_message_ref),
  CONSTRAINT ck_zc_inbound_message_channel CHECK (btrim(channel) <> ''),
  CONSTRAINT ck_zc_inbound_message_ref CHECK (btrim(channel_message_ref) <> ''),
  CONSTRAINT ck_zc_inbound_message_interpreted_as CHECK (interpreted_as IN (
    'opt_out','opt_in','consent_given','consent_declined','reply','unrecognised')),
  CONSTRAINT ck_zc_inbound_message_interpretation_pair CHECK ((interpreted_as IS NULL) = (interpreted_at IS NULL))
);
CREATE INDEX ix_zc_inbound_message_member ON zc_inbound_message (tenant_id, member_id, received_at DESC);

-- =============================================================================
-- 14  OUTCOMES & PROGRAMME PROOF
-- =============================================================================

CREATE TABLE zc_outcome_observation (
  id                     bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id              bigint NOT NULL,
  outcome_definition_id  bigint NOT NULL,
  measure_version        integer NOT NULL,
  enrolment_id           bigint, -- NULL for aggregate outcomes
  period_start           date NOT NULL,
  period_end             date NOT NULL,
  value_numeric          numeric,
  numerator              numeric,
  denominator            numeric,
  is_incomplete          boolean NOT NULL DEFAULT false,
  incomplete_reason      text,
  observed_at            timestamptz NOT NULL DEFAULT now(),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_outcome_observation_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_outcome_observation_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_outcome_observation PRIMARY KEY (id),
  CONSTRAINT fk_zc_outcome_observation_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  -- measure_version must match the definition.
  CONSTRAINT fk_zc_outcome_observation_definition
    FOREIGN KEY (tenant_id, outcome_definition_id, measure_version)
    REFERENCES zc_outcome_definition (tenant_id, id, measure_version),
  CONSTRAINT fk_zc_outcome_observation_enrolment FOREIGN KEY (tenant_id, enrolment_id)
    REFERENCES zc_enrolment (tenant_id, id),
  CONSTRAINT ck_zc_outcome_observation_period CHECK (period_end >= period_start),
  CONSTRAINT ck_zc_outcome_observation_denominator CHECK (denominator IS NULL OR denominator > 0),
  CONSTRAINT ck_zc_outcome_observation_value CHECK (
    value_numeric IS NOT NULL OR (numerator IS NOT NULL AND denominator IS NOT NULL) OR is_incomplete),
  CONSTRAINT ck_zc_outcome_observation_incomplete CHECK (
    NOT is_incomplete OR nullif(btrim(incomplete_reason), '') IS NOT NULL)
);
CREATE INDEX ix_zc_outcome_observation_definition
  ON zc_outcome_observation (tenant_id, outcome_definition_id, period_start);

CREATE TABLE zc_report_snapshot (
  id                        bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id                 bigint NOT NULL,
  programme_version_id      bigint NOT NULL,
  audience                  text NOT NULL,
  period_start              date NOT NULL,
  period_end                date NOT NULL,
  figures                   jsonb NOT NULL,
  min_population_threshold  integer,
  withheld_measures         text[] NOT NULL DEFAULT '{}',
  attribution_note          text,
  is_incomplete             boolean NOT NULL DEFAULT false,
  generated_at              timestamptz NOT NULL DEFAULT now(),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_report_snapshot_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_report_snapshot PRIMARY KEY (id),
  CONSTRAINT fk_zc_report_snapshot_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_report_snapshot_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT ck_zc_report_snapshot_audience CHECK (audience IN ('internal','payer','provider','employer_aggregate')),
  -- Employer reports need a threshold (value is config).
  CONSTRAINT ck_zc_report_employer_threshold CHECK (
    audience <> 'employer_aggregate' OR (min_population_threshold IS NOT NULL AND min_population_threshold > 0)),
  CONSTRAINT ck_zc_report_snapshot_threshold CHECK (min_population_threshold IS NULL OR min_population_threshold > 0),
  CONSTRAINT ck_zc_report_snapshot_period CHECK (period_end >= period_start),
  CONSTRAINT ck_zc_report_snapshot_withheld CHECK (array_position(withheld_measures, NULL) IS NULL)
);
COMMENT ON TABLE zc_report_snapshot IS
  'Append-only. Below-threshold figures are WITHHELD and listed in withheld_measures - never rounded or approximated.';
COMMENT ON COLUMN zc_report_snapshot.figures IS
  'jsonb: the frozen report payload as issued, each figure carrying its measure_version. Never rewritten.';
CREATE INDEX ix_zc_report_snapshot_lookup
  ON zc_report_snapshot (tenant_id, programme_version_id, audience, generated_at DESC);

-- =============================================================================
-- 15  AI DECISION SUPPORT  (dormant in the founding slice)
-- =============================================================================

CREATE TABLE zc_ai_recommendation (
  id                   bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id            bigint NOT NULL,
  subject_type         text NOT NULL,
  subject_id           bigint NOT NULL, -- polymorphic, no FK
  recommendation_type  text NOT NULL,
  content              jsonb NOT NULL,
  model_ref            text NOT NULL,
  rationale            text,
  is_advisory          boolean NOT NULL DEFAULT true,
  status               text NOT NULL DEFAULT 'pending_review',
  generated_at         timestamptz NOT NULL DEFAULT now(),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_ai_recommendation_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_ai_recommendation_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_ai_recommendation PRIMARY KEY (id),
  CONSTRAINT fk_zc_ai_recommendation_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_ai_recommendation_fk_target UNIQUE (tenant_id, id),
  -- AI output is always advisory.
  CONSTRAINT ck_zc_ai_advisory CHECK (is_advisory = true),
  CONSTRAINT ck_zc_ai_recommendation_subject_type CHECK (subject_type IN (
    'enrolment','care_plan','care_gap','task','risk_classification','outreach_request','assessment','referral')),
  CONSTRAINT ck_zc_ai_recommendation_type CHECK (btrim(recommendation_type) <> ''),
  CONSTRAINT ck_zc_ai_recommendation_model_ref CHECK (btrim(model_ref) <> ''),
  CONSTRAINT ck_zc_ai_recommendation_status CHECK (status IN ('pending_review','reviewed','expired'))
);
COMMENT ON COLUMN zc_ai_recommendation.content IS 'jsonb: explainability payload of the recommendation.';
CREATE INDEX ix_zc_ai_recommendation_subject ON zc_ai_recommendation (tenant_id, subject_type, subject_id);

CREATE TABLE zc_ai_review (
  id                 bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id          bigint NOT NULL,
  recommendation_id  bigint NOT NULL,
  decision           text NOT NULL,
  reviewed_by        text NOT NULL,
  reviewed_at        timestamptz NOT NULL DEFAULT now(),
  rationale          text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_ai_review_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_ai_review_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_ai_review PRIMARY KEY (id),
  CONSTRAINT fk_zc_ai_review_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_ai_review_recommendation FOREIGN KEY (tenant_id, recommendation_id)
    REFERENCES zc_ai_recommendation (tenant_id, id),
  CONSTRAINT ck_zc_ai_review_decision CHECK (decision IN ('accepted','modified','rejected')),
  CONSTRAINT ck_zc_ai_review_reviewer CHECK (btrim(reviewed_by) <> ''),
  CONSTRAINT ck_zc_ai_review_rationale CHECK (decision = 'accepted' OR nullif(btrim(rationale), '') IS NOT NULL)
);
CREATE INDEX ix_zc_ai_review_recommendation ON zc_ai_review (tenant_id, recommendation_id);

-- =============================================================================
-- 16  AUDIT & SECURITY  (append-only by trigger AND by privilege)
-- =============================================================================

CREATE TABLE zc_domain_audit (
  id                       bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id                bigint NOT NULL,
  occurred_at              timestamptz NOT NULL DEFAULT now(),   -- partition key
  actor_id                 text NOT NULL,
  organisation_id          bigint,
  member_id                bigint,
  programme_version_id     bigint,
  entity_type              text NOT NULL,
  entity_id                bigint NOT NULL,
  -- TODO: restrict to the 26 approved operations.
  operation                text NOT NULL,
  previous_state           jsonb,
  new_state                jsonb,
  reason                   text,
  correlation_id           text NOT NULL,
  consent_wording_version  text,
  consent_channel          text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_domain_audit_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_domain_audit PRIMARY KEY (id),
  CONSTRAINT fk_zc_domain_audit_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_domain_audit_organisation FOREIGN KEY (tenant_id, organisation_id)
    REFERENCES zc_organisation (tenant_id, id),
  CONSTRAINT fk_zc_domain_audit_member FOREIGN KEY (tenant_id, member_id)
    REFERENCES zc_member_reference (tenant_id, id),
  CONSTRAINT fk_zc_domain_audit_programme_version FOREIGN KEY (tenant_id, programme_version_id)
    REFERENCES zc_programme_version (tenant_id, id),
  CONSTRAINT ck_zc_domain_audit_actor CHECK (btrim(actor_id) <> ''),
  CONSTRAINT ck_zc_domain_audit_entity_type CHECK (btrim(entity_type) <> ''),
  CONSTRAINT ck_zc_domain_audit_operation CHECK (btrim(operation) <> ''),
  CONSTRAINT ck_zc_domain_audit_correlation CHECK (btrim(correlation_id) <> '')
);
COMMENT ON TABLE zc_domain_audit IS
  'Append-only record of every material write (PRD 9.8), separate from application logs. Not correctable.';
COMMENT ON COLUMN zc_domain_audit.previous_state IS 'jsonb: state snapshot before the change. Deliberately not indexed.';
COMMENT ON COLUMN zc_domain_audit.new_state IS 'jsonb: state snapshot after the change. Deliberately not indexed (04D 15).';
CREATE INDEX ix_zc_domain_audit_entity      ON zc_domain_audit (tenant_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_zc_domain_audit_member      ON zc_domain_audit (tenant_id, member_id, occurred_at DESC) WHERE member_id IS NOT NULL;
CREATE INDEX ix_zc_domain_audit_actor       ON zc_domain_audit (tenant_id, actor_id, occurred_at DESC);
CREATE INDEX ix_zc_domain_audit_correlation ON zc_domain_audit (tenant_id, correlation_id);

CREATE TABLE zc_security_event (
  id              bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id       bigint, -- NULL if no tenant yet
  occurred_at     timestamptz NOT NULL DEFAULT now(),   -- partition key
  event_type      text NOT NULL,
  actor_id        text,
  source_address  inet,
  route           text,
  correlation_id  text,
  detail          text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_security_event_created_by CHECK (btrim(created_by) <> ''),
  CONSTRAINT pk_zc_security_event PRIMARY KEY (id),
  CONSTRAINT fk_zc_security_event_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT ck_zc_security_event_type CHECK (event_type IN (
    'authorisation_denied','tenant_violation_attempt','invalid_webhook_signature',
    'break_glass_use','audit_export'))
);
COMMENT ON TABLE zc_security_event IS
  'Separate append-only sink. Deliberately EXCLUDED from RLS: events such as an invalid webhook signature occur before a tenant is resolved (04D 12.3).';
CREATE INDEX ix_zc_security_event_tenant ON zc_security_event (tenant_id, occurred_at DESC);
CREATE INDEX ix_zc_security_event_type   ON zc_security_event (event_type, occurred_at DESC);

-- =============================================================================
-- 17  INTEGRATION PERSISTENCE
-- =============================================================================

CREATE TABLE zc_idempotency_key (
  id                 bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id          bigint NOT NULL,
  idempotency_key    text NOT NULL,
  actor_id           text NOT NULL,
  route              text NOT NULL,
  request_body_hash  text NOT NULL,
  status             text NOT NULL DEFAULT 'in_progress',
  response_status    integer,
  response_envelope  jsonb,
  expires_at         timestamptz NOT NULL DEFAULT (now() + interval '24 hours'),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_idempotency_key_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_idempotency_key_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_idempotency_key PRIMARY KEY (id),
  CONSTRAINT fk_zc_idempotency_key_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_idempotency_key UNIQUE (tenant_id, idempotency_key, actor_id, route),
  CONSTRAINT ck_zc_idempotency_key_key   CHECK (btrim(idempotency_key) <> ''),
  CONSTRAINT ck_zc_idempotency_key_actor CHECK (btrim(actor_id) <> ''),
  CONSTRAINT ck_zc_idempotency_key_route CHECK (btrim(route) <> ''),
  CONSTRAINT ck_zc_idempotency_key_hash  CHECK (btrim(request_body_hash) <> ''),
  CONSTRAINT ck_zc_idempotency_key_status CHECK (status IN ('in_progress','completed')),
  CONSTRAINT ck_zc_idempotency_key_response CHECK (
    status <> 'completed' OR (response_envelope IS NOT NULL AND response_status IS NOT NULL)),
  CONSTRAINT ck_zc_idempotency_key_retention CHECK (expires_at >= created_at + interval '24 hours')
);
COMMENT ON COLUMN zc_idempotency_key.response_envelope IS
  'jsonb: cached API response envelope, replayed byte-identical.';
CREATE INDEX ix_zc_idempotency_key_expiry ON zc_idempotency_key (tenant_id, expires_at);

CREATE TABLE zc_integration_outbox (
  id                   bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id            bigint NOT NULL,
  event_type           text NOT NULL,
  aggregate_type       text NOT NULL,
  aggregate_id         bigint NOT NULL,
  correlation_id       text NOT NULL,
  occurred_at          timestamptz NOT NULL DEFAULT now(),
  payload              jsonb NOT NULL,
  data_classification  text NOT NULL,
  status               text NOT NULL DEFAULT 'pending',
  attempt_count        integer NOT NULL DEFAULT 0,
  next_attempt_at      timestamptz NOT NULL DEFAULT now(),
  published_at         timestamptz,
  last_error           text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_integration_outbox_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_integration_outbox_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_integration_outbox PRIMARY KEY (id),
  CONSTRAINT fk_zc_integration_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT ck_zc_integration_outbox_event_type CHECK (btrim(event_type) <> ''),
  CONSTRAINT ck_zc_integration_outbox_aggregate_type CHECK (btrim(aggregate_type) <> ''),
  CONSTRAINT ck_zc_integration_outbox_correlation CHECK (btrim(correlation_id) <> ''),
  CONSTRAINT ck_zc_integration_outbox_classification CHECK (data_classification IN ('phi','non_phi')),
  CONSTRAINT ck_zc_integration_outbox_status CHECK (status IN ('pending','published','failed','dead_lettered')),
  CONSTRAINT ck_zc_integration_outbox_attempts CHECK (attempt_count >= 0),
  CONSTRAINT ck_zc_integration_outbox_published CHECK (status <> 'published' OR published_at IS NOT NULL)
);
COMMENT ON TABLE zc_integration_outbox IS
  'Transactional outbox: written in the SAME transaction as the aggregate change that produced the event.';
COMMENT ON COLUMN zc_integration_outbox.payload IS 'jsonb: 04B 12 event envelope payload.';
CREATE INDEX ix_zc_outbox_pending
  ON zc_integration_outbox (tenant_id, next_attempt_at, occurred_at)
  WHERE status IN ('pending','failed');

CREATE TABLE zc_integration_inbox (
  id                   bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id            bigint NOT NULL,
  source_system        text NOT NULL,   -- free text from adapter
  external_message_id  text NOT NULL,   -- e.g. wamid
  received_at          timestamptz NOT NULL DEFAULT now(),
  payload              jsonb NOT NULL,
  data_classification  text NOT NULL,
  status               text NOT NULL DEFAULT 'received',
  attempt_count        integer NOT NULL DEFAULT 0,
  next_attempt_at      timestamptz NOT NULL DEFAULT now(),
  processed_at         timestamptz,
  last_error           text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_integration_inbox_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_integration_inbox_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_integration_inbox PRIMARY KEY (id),
  CONSTRAINT fk_zc_integration_inbox_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT uq_zc_integration_inbox_dedup UNIQUE (tenant_id, source_system, external_message_id),
  CONSTRAINT ck_zc_integration_inbox_source_system CHECK (btrim(source_system) <> ''),
  CONSTRAINT ck_zc_integration_inbox_message_id CHECK (btrim(external_message_id) <> ''),
  CONSTRAINT ck_zc_integration_inbox_classification CHECK (data_classification IN ('phi','non_phi')),
  CONSTRAINT ck_zc_integration_inbox_status CHECK (status IN ('received','processing','processed','failed','dead_lettered')),
  CONSTRAINT ck_zc_integration_inbox_attempts CHECK (attempt_count >= 0),
  CONSTRAINT ck_zc_integration_inbox_processed CHECK (status <> 'processed' OR processed_at IS NOT NULL)
);
COMMENT ON COLUMN zc_integration_inbox.payload IS 'jsonb: raw inbound envelope as received.';
CREATE INDEX ix_zc_integration_inbox_pending
  ON zc_integration_inbox (tenant_id, next_attempt_at)
  WHERE status IN ('received','failed');

CREATE TABLE zc_dead_letter (
  id                      bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id               bigint NOT NULL,
  origin                  text NOT NULL,
  origin_id               bigint NOT NULL, -- polymorphic, no FK
  failure_reason          text NOT NULL,
  payload                 jsonb,
  attempt_count           integer NOT NULL DEFAULT 0,
  parked_at               timestamptz NOT NULL DEFAULT now(),
  status                  text NOT NULL DEFAULT 'parked',
  operational_task_id     bigint, -- follow-up task
  reconciliation_outcome  text,
  resolved_by             text,
  resolved_at             timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL CONSTRAINT ck_zc_dead_letter_created_by CHECK (btrim(created_by) <> ''),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  updated_by   text,
  row_version  integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_dead_letter_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_dead_letter PRIMARY KEY (id),
  CONSTRAINT fk_zc_dead_letter_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  CONSTRAINT fk_zc_dead_letter_operational_task FOREIGN KEY (tenant_id, operational_task_id)
    REFERENCES zc_task (tenant_id, id),
  CONSTRAINT ck_zc_dead_letter_origin CHECK (origin IN (
    'integration_outbox','integration_inbox','outreach_delivery','inbound_message')),
  CONSTRAINT ck_zc_dead_letter_failure_reason CHECK (btrim(failure_reason) <> ''),
  CONSTRAINT ck_zc_dead_letter_attempts CHECK (attempt_count >= 0),
  CONSTRAINT ck_zc_dead_letter_status CHECK (status IN ('parked','reconciling','resolved','discarded')),
  CONSTRAINT ck_zc_dead_letter_resolution CHECK (
    status NOT IN ('resolved','discarded')
    OR (nullif(btrim(reconciliation_outcome), '') IS NOT NULL
        AND nullif(btrim(resolved_by), '') IS NOT NULL
        AND resolved_at IS NOT NULL))
);
COMMENT ON COLUMN zc_dead_letter.payload IS 'jsonb: copy of the failed message envelope, kept for reconciliation.';
CREATE INDEX ix_zc_dead_letter_open ON zc_dead_letter (tenant_id, parked_at) WHERE status IN ('parked','reconciling');

-- =============================================================================
-- TABLE-SPECIFIC INVARIANT TRIGGERS
-- =============================================================================

-- Care plan approval can't be changed once set.
CREATE FUNCTION zc_care_plan_approval_immutable() RETURNS trigger
  LANGUAGE plpgsql
AS $fn$
BEGIN
  IF OLD.approved_at IS NOT NULL
     AND (NEW.approved_by IS DISTINCT FROM OLD.approved_by
          OR NEW.approved_at IS DISTINCT FROM OLD.approved_at) THEN
    RAISE EXCEPTION 'trg_zc_care_plan_approval_immutable: the recorded approval of care plan % cannot be rewritten', OLD.id
      USING ERRCODE = 'integrity_constraint_violation',
            CONSTRAINT = 'trg_zc_care_plan_approval_immutable', TABLE = TG_TABLE_NAME;
  END IF;
  IF OLD.activated_at IS NOT NULL AND NEW.activated_at IS DISTINCT FROM OLD.activated_at THEN
    RAISE EXCEPTION 'trg_zc_care_plan_approval_immutable: activated_at of care plan % cannot be rewritten', OLD.id
      USING ERRCODE = 'integrity_constraint_violation',
            CONSTRAINT = 'trg_zc_care_plan_approval_immutable', TABLE = TG_TABLE_NAME;
  END IF;
  RETURN NEW;
END
$fn$;

CREATE TRIGGER trg_zc_care_plan_approval_immutable
  BEFORE UPDATE ON zc_care_plan
  FOR EACH ROW EXECUTE FUNCTION zc_care_plan_approval_immutable();

-- Observations can't be edited, only invalidated.
CREATE FUNCTION zc_observation_correction_only() RETURNS trigger
  LANGUAGE plpgsql
AS $fn$
DECLARE
  c_pinned CONSTANT text[] := ARRAY['enrolment_id','member_id','programme_version_id','observation_type_id',
                                    'value_numeric','unit','observed_at','source'];
  v_old jsonb := to_jsonb(OLD);
  v_new jsonb := to_jsonb(NEW);
  v_col text;
BEGIN
  FOREACH v_col IN ARRAY c_pinned LOOP
    IF (v_old -> v_col) IS DISTINCT FROM (v_new -> v_col) THEN
      RAISE EXCEPTION 'trg_zc_observation_correction_only: %.% cannot be edited; invalidate the row and record a new observation', TG_TABLE_NAME, v_col
        USING ERRCODE = 'integrity_constraint_violation',
              CONSTRAINT = 'trg_zc_observation_correction_only', TABLE = TG_TABLE_NAME, COLUMN = v_col;
    END IF;
  END LOOP;
  IF OLD.is_invalidated
     AND (NEW.is_invalidated IS DISTINCT FROM OLD.is_invalidated
          OR NEW.invalidated_at IS DISTINCT FROM OLD.invalidated_at
          OR NEW.invalidated_by IS DISTINCT FROM OLD.invalidated_by
          OR NEW.invalidation_reason IS DISTINCT FROM OLD.invalidation_reason) THEN
    RAISE EXCEPTION 'trg_zc_observation_correction_only: invalidation of observation % is final', OLD.id
      USING ERRCODE = 'integrity_constraint_violation',
            CONSTRAINT = 'trg_zc_observation_correction_only', TABLE = TG_TABLE_NAME;
  END IF;
  RETURN NEW;
END
$fn$;

CREATE TRIGGER trg_zc_observation_correction_only
  BEFORE UPDATE ON zc_observation
  FOR EACH ROW EXECUTE FUNCTION zc_observation_correction_only();

-- A task can't complete while a prerequisite is open. Cancelled counts as closed.
CREATE FUNCTION zc_task_dependency_gate() RETURNS trigger
  LANGUAGE plpgsql
AS $fn$
DECLARE
  v_open_count integer;
BEGIN
  SELECT count(*)
    INTO v_open_count
    FROM public.zc_task_dependency d
    JOIN public.zc_task p
      ON p.tenant_id = d.tenant_id
     AND p.id = d.depends_on_task_id
   WHERE d.tenant_id = NEW.tenant_id
     AND d.task_id = NEW.id
     AND d.removed_at IS NULL
     AND p.status NOT IN ('completed','cancelled');
  IF v_open_count > 0 THEN
    RAISE EXCEPTION 'trg_zc_task_dependency_gate: task % has % open prerequisite(s)', NEW.id, v_open_count
      USING ERRCODE = 'integrity_constraint_violation',
            CONSTRAINT = 'trg_zc_task_dependency_gate', TABLE = TG_TABLE_NAME;
  END IF;
  RETURN NEW;
END
$fn$;

CREATE TRIGGER trg_zc_task_dependency_gate
  BEFORE UPDATE OF status ON zc_task
  FOR EACH ROW
  WHEN (NEW.status = 'completed' AND OLD.status IS DISTINCT FROM 'completed')
  EXECUTE FUNCTION zc_task_dependency_gate();

-- Published versions are immutable.
CREATE TRIGGER trg_zc_programme_version_immutable
  BEFORE UPDATE OR DELETE ON zc_programme_version
  FOR EACH ROW EXECUTE FUNCTION zc_forbid_published_version_change('trg_zc_programme_version_immutable');

-- Published questionnaires are immutable.
CREATE TRIGGER trg_zc_assessment_template_version_immutable
  BEFORE UPDATE OR DELETE ON zc_assessment_template_version
  FOR EACH ROW EXECUTE FUNCTION zc_forbid_published_version_change('trg_zc_assessment_template_version_immutable');

-- Pinned columns.
CREATE TRIGGER trg_zc_enrolment_pins
  BEFORE UPDATE ON zc_enrolment
  FOR EACH ROW EXECUTE FUNCTION zc_forbid_pinned_change(
    'trg_zc_enrolment_pins', 'member_id', 'programme_id', 'programme_version_id');

CREATE TRIGGER trg_zc_assessment_pins
  BEFORE UPDATE ON zc_assessment
  FOR EACH ROW EXECUTE FUNCTION zc_forbid_pinned_change(
    'trg_zc_assessment_pins', 'enrolment_id', 'template_version_id');

CREATE TRIGGER trg_zc_care_plan_pins
  BEFORE UPDATE ON zc_care_plan
  FOR EACH ROW EXECUTE FUNCTION zc_forbid_pinned_change(
    'trg_zc_care_plan_pins', 'enrolment_id', 'programme_version_id', 'supersedes_plan_id', 'version_number');


-- =============================================================================
-- GENERATED: row maintenance, append-only enforcement, row-level security
-- =============================================================================

-- Row touch triggers.
CREATE TRIGGER trg_zc_tenant_touch BEFORE UPDATE ON zc_tenant FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_organisation_touch BEFORE UPDATE ON zc_organisation FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_role_assignment_touch BEFORE UPDATE ON zc_role_assignment FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_break_glass_grant_touch BEFORE UPDATE ON zc_break_glass_grant FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_member_reference_touch BEFORE UPDATE ON zc_member_reference FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_external_reference_touch BEFORE UPDATE ON zc_external_reference FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_programme_touch BEFORE UPDATE ON zc_programme FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_programme_version_touch BEFORE UPDATE ON zc_programme_version FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_assessment_template_touch BEFORE UPDATE ON zc_assessment_template FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_assessment_template_version_touch BEFORE UPDATE ON zc_assessment_template_version FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_gap_rule_touch BEFORE UPDATE ON zc_gap_rule FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_task_template_touch BEFORE UPDATE ON zc_task_template FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_referral_type_touch BEFORE UPDATE ON zc_referral_type FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_observation_type_touch BEFORE UPDATE ON zc_observation_type FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_goal_type_touch BEFORE UPDATE ON zc_goal_type FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_outcome_definition_touch BEFORE UPDATE ON zc_outcome_definition FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_cohort_touch BEFORE UPDATE ON zc_cohort FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_cohort_run_touch BEFORE UPDATE ON zc_cohort_run FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_cohort_membership_touch BEFORE UPDATE ON zc_cohort_membership FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_enrolment_touch BEFORE UPDATE ON zc_enrolment FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_consent_record_touch BEFORE UPDATE ON zc_consent_record FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_member_preference_touch BEFORE UPDATE ON zc_member_preference FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_assessment_touch BEFORE UPDATE ON zc_assessment FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_care_plan_touch BEFORE UPDATE ON zc_care_plan FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_goal_touch BEFORE UPDATE ON zc_goal FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_intervention_touch BEFORE UPDATE ON zc_intervention FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_care_plan_link_touch BEFORE UPDATE ON zc_care_plan_link FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_task_touch BEFORE UPDATE ON zc_task FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_task_dependency_touch BEFORE UPDATE ON zc_task_dependency FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_work_queue_definition_touch BEFORE UPDATE ON zc_work_queue_definition FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_referral_touch BEFORE UPDATE ON zc_referral FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_transition_touch BEFORE UPDATE ON zc_transition FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_provider_participation_touch BEFORE UPDATE ON zc_provider_participation FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_medication_coordination_touch BEFORE UPDATE ON zc_medication_coordination FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_refill_request_touch BEFORE UPDATE ON zc_refill_request FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_adherence_event_touch BEFORE UPDATE ON zc_adherence_event FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_monitoring_schedule_touch BEFORE UPDATE ON zc_monitoring_schedule FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_observation_touch BEFORE UPDATE ON zc_observation FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_care_gap_touch BEFORE UPDATE ON zc_care_gap FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_outreach_request_touch BEFORE UPDATE ON zc_outreach_request FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_outreach_delivery_touch BEFORE UPDATE ON zc_outreach_delivery FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_inbound_message_touch BEFORE UPDATE ON zc_inbound_message FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_outcome_observation_touch BEFORE UPDATE ON zc_outcome_observation FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_ai_recommendation_touch BEFORE UPDATE ON zc_ai_recommendation FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_ai_review_touch BEFORE UPDATE ON zc_ai_review FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_idempotency_key_touch BEFORE UPDATE ON zc_idempotency_key FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_integration_outbox_touch BEFORE UPDATE ON zc_integration_outbox FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_integration_inbox_touch BEFORE UPDATE ON zc_integration_inbox FOR EACH ROW EXECUTE FUNCTION zc_touch_row();
CREATE TRIGGER trg_zc_dead_letter_touch BEFORE UPDATE ON zc_dead_letter FOR EACH ROW EXECUTE FUNCTION zc_touch_row();

-- Append-only triggers.
CREATE TRIGGER trg_zc_config_history_append_only BEFORE UPDATE OR DELETE ON zc_config_history FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_config_history_append_only');
CREATE TRIGGER trg_zc_config_history_no_truncate BEFORE TRUNCATE ON zc_config_history FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_config_history_append_only');
CREATE TRIGGER trg_zc_assessment_response_append_only BEFORE UPDATE OR DELETE ON zc_assessment_response FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_assessment_response_append_only');
CREATE TRIGGER trg_zc_assessment_response_no_truncate BEFORE TRUNCATE ON zc_assessment_response FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_assessment_response_append_only');
CREATE TRIGGER trg_zc_risk_classification_append_only BEFORE UPDATE OR DELETE ON zc_risk_classification FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_risk_classification_append_only');
CREATE TRIGGER trg_zc_risk_classification_no_truncate BEFORE TRUNCATE ON zc_risk_classification FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_risk_classification_append_only');
CREATE TRIGGER trg_zc_plan_review_append_only BEFORE UPDATE OR DELETE ON zc_plan_review FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_plan_review_append_only');
CREATE TRIGGER trg_zc_plan_review_no_truncate BEFORE TRUNCATE ON zc_plan_review FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_plan_review_append_only');
CREATE TRIGGER trg_zc_task_assignment_history_append_only BEFORE UPDATE OR DELETE ON zc_task_assignment_history FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_task_assignment_history_append_only');
CREATE TRIGGER trg_zc_task_assignment_history_no_truncate BEFORE TRUNCATE ON zc_task_assignment_history FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_task_assignment_history_append_only');
CREATE TRIGGER trg_zc_provider_action_append_only BEFORE UPDATE OR DELETE ON zc_provider_action FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_provider_action_append_only');
CREATE TRIGGER trg_zc_provider_action_no_truncate BEFORE TRUNCATE ON zc_provider_action FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_provider_action_append_only');
CREATE TRIGGER trg_zc_report_snapshot_append_only BEFORE UPDATE OR DELETE ON zc_report_snapshot FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_report_snapshot_append_only');
CREATE TRIGGER trg_zc_report_snapshot_no_truncate BEFORE TRUNCATE ON zc_report_snapshot FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_report_snapshot_append_only');
CREATE TRIGGER trg_zc_domain_audit_append_only BEFORE UPDATE OR DELETE ON zc_domain_audit FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_domain_audit_append_only');
CREATE TRIGGER trg_zc_domain_audit_no_truncate BEFORE TRUNCATE ON zc_domain_audit FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_domain_audit_append_only');
CREATE TRIGGER trg_zc_security_event_append_only BEFORE UPDATE OR DELETE ON zc_security_event FOR EACH ROW EXECUTE FUNCTION zc_forbid_change('trg_zc_security_event_append_only');
CREATE TRIGGER trg_zc_security_event_no_truncate BEFORE TRUNCATE ON zc_security_event FOR EACH STATEMENT EXECUTE FUNCTION zc_forbid_change('trg_zc_security_event_append_only');

-- Tenant isolation. App must connect as zc_app (superusers bypass RLS).
ALTER TABLE zc_organisation ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_organisation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_organisation_tenant ON zc_organisation
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_role_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_role_assignment FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_role_assignment_tenant ON zc_role_assignment
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_break_glass_grant ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_break_glass_grant FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_break_glass_grant_tenant ON zc_break_glass_grant
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_config_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_config_history FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_config_history_tenant ON zc_config_history
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_member_reference ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_member_reference FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_member_reference_tenant ON zc_member_reference
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_external_reference ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_external_reference FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_external_reference_tenant ON zc_external_reference
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_programme ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_programme FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_programme_tenant ON zc_programme
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_programme_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_programme_version FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_programme_version_tenant ON zc_programme_version
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_assessment_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_assessment_template FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_assessment_template_tenant ON zc_assessment_template
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_assessment_template_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_assessment_template_version FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_assessment_template_version_tenant ON zc_assessment_template_version
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_gap_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_gap_rule FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_gap_rule_tenant ON zc_gap_rule
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_task_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_task_template FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_task_template_tenant ON zc_task_template
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_referral_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_referral_type FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_referral_type_tenant ON zc_referral_type
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_observation_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_observation_type FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_observation_type_tenant ON zc_observation_type
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_goal_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_goal_type FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_goal_type_tenant ON zc_goal_type
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_outcome_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_outcome_definition FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_outcome_definition_tenant ON zc_outcome_definition
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_cohort ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_cohort FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_cohort_tenant ON zc_cohort
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_cohort_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_cohort_run FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_cohort_run_tenant ON zc_cohort_run
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_cohort_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_cohort_membership FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_cohort_membership_tenant ON zc_cohort_membership
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_enrolment ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_enrolment FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_enrolment_tenant ON zc_enrolment
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_consent_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_consent_record FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_consent_record_tenant ON zc_consent_record
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_member_preference ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_member_preference FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_member_preference_tenant ON zc_member_preference
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_assessment ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_assessment FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_assessment_tenant ON zc_assessment
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_assessment_response ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_assessment_response FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_assessment_response_tenant ON zc_assessment_response
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_risk_classification ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_risk_classification FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_risk_classification_tenant ON zc_risk_classification
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_care_plan ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_care_plan FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_care_plan_tenant ON zc_care_plan
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_goal ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_goal FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_goal_tenant ON zc_goal
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_intervention ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_intervention FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_intervention_tenant ON zc_intervention
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_care_plan_link ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_care_plan_link FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_care_plan_link_tenant ON zc_care_plan_link
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_plan_review ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_plan_review FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_plan_review_tenant ON zc_plan_review
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_task ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_task FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_task_tenant ON zc_task
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_task_dependency ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_task_dependency FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_task_dependency_tenant ON zc_task_dependency
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_task_assignment_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_task_assignment_history FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_task_assignment_history_tenant ON zc_task_assignment_history
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_work_queue_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_work_queue_definition FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_work_queue_definition_tenant ON zc_work_queue_definition
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_referral ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_referral FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_referral_tenant ON zc_referral
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_transition ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_transition FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_transition_tenant ON zc_transition
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_provider_participation ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_provider_participation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_provider_participation_tenant ON zc_provider_participation
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_provider_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_provider_action FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_provider_action_tenant ON zc_provider_action
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_medication_coordination ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_medication_coordination FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_medication_coordination_tenant ON zc_medication_coordination
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_refill_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_refill_request FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_refill_request_tenant ON zc_refill_request
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_adherence_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_adherence_event FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_adherence_event_tenant ON zc_adherence_event
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_monitoring_schedule ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_monitoring_schedule FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_monitoring_schedule_tenant ON zc_monitoring_schedule
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_observation ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_observation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_observation_tenant ON zc_observation
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_care_gap ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_care_gap FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_care_gap_tenant ON zc_care_gap
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_outreach_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_outreach_request FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_outreach_request_tenant ON zc_outreach_request
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_outreach_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_outreach_delivery FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_outreach_delivery_tenant ON zc_outreach_delivery
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_inbound_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_inbound_message FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_inbound_message_tenant ON zc_inbound_message
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_outcome_observation ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_outcome_observation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_outcome_observation_tenant ON zc_outcome_observation
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_report_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_report_snapshot FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_report_snapshot_tenant ON zc_report_snapshot
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_ai_recommendation ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_ai_recommendation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_ai_recommendation_tenant ON zc_ai_recommendation
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_ai_review ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_ai_review FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_ai_review_tenant ON zc_ai_review
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_domain_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_domain_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_domain_audit_tenant ON zc_domain_audit
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_idempotency_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_idempotency_key FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_idempotency_key_tenant ON zc_idempotency_key
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_integration_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_integration_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_integration_outbox_tenant ON zc_integration_outbox
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_integration_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_integration_inbox FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_integration_inbox_tenant ON zc_integration_inbox
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());
ALTER TABLE zc_dead_letter ENABLE ROW LEVEL SECURITY;
ALTER TABLE zc_dead_letter FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_zc_dead_letter_tenant ON zc_dead_letter
  USING (tenant_id = zc_current_tenant())
  WITH CHECK (tenant_id = zc_current_tenant());


-- #############################################################################
-- ##  V002__application_role_and_grants.sql
-- #############################################################################

-- =============================================================================
-- V002__application_role_and_grants.sql
-- =============================================================================
-- zc_app: no superuser, no ownership, no DELETE, no UPDATE on append-only tables.
-- Created without a password; set it from the secret store. Never commit it.
-- =============================================================================

DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'zc_app') THEN
    CREATE ROLE zc_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT;
  END IF;
  -- Deletes expired idempotency keys only. Subject to RLS.
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'zc_housekeeping') THEN
    CREATE ROLE zc_housekeeping NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END
$do$;

-- Check existing roles weren't widened.
DO $do$
DECLARE r record;
BEGIN
  FOR r IN SELECT rolname, rolsuper, rolbypassrls, rolcreaterole
             FROM pg_roles WHERE rolname IN ('zc_app','zc_housekeeping') LOOP
    IF r.rolsuper OR r.rolbypassrls OR r.rolcreaterole THEN
      RAISE EXCEPTION '% must not be SUPERUSER, BYPASSRLS or CREATEROLE - RLS would be inert', r.rolname;
    END IF;
  END LOOP;
END
$do$;

-- Only the owner creates objects.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO zc_app, zc_housekeeping;

-- Reset, then grant.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM zc_app;

GRANT SELECT ON zc_tenant TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_organisation TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_role_assignment TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_break_glass_grant TO zc_app;
GRANT SELECT, INSERT ON zc_config_history TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_member_reference TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_external_reference TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_programme TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_programme_version TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_assessment_template TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_assessment_template_version TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_gap_rule TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_task_template TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_referral_type TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_observation_type TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_goal_type TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_outcome_definition TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_cohort TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_cohort_run TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_cohort_membership TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_enrolment TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_consent_record TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_member_preference TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_assessment TO zc_app;
GRANT SELECT, INSERT ON zc_assessment_response TO zc_app;
GRANT SELECT, INSERT ON zc_risk_classification TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_care_plan TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_goal TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_intervention TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_care_plan_link TO zc_app;
GRANT SELECT, INSERT ON zc_plan_review TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_task TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_task_dependency TO zc_app;
GRANT SELECT, INSERT ON zc_task_assignment_history TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_work_queue_definition TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_referral TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_transition TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_provider_participation TO zc_app;
GRANT SELECT, INSERT ON zc_provider_action TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_medication_coordination TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_refill_request TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_adherence_event TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_monitoring_schedule TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_observation TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_care_gap TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_outreach_request TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_outreach_delivery TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_inbound_message TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_outcome_observation TO zc_app;
GRANT SELECT, INSERT ON zc_report_snapshot TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_ai_recommendation TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_ai_review TO zc_app;
GRANT SELECT, INSERT ON zc_domain_audit TO zc_app;
GRANT SELECT, INSERT ON zc_security_event TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_idempotency_key TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_integration_outbox TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_integration_inbox TO zc_app;
GRANT SELECT, INSERT, UPDATE ON zc_dead_letter TO zc_app;

-- Append-only tables.
REVOKE UPDATE, DELETE, TRUNCATE ON zc_config_history FROM zc_app;
REVOKE UPDATE, DELETE, TRUNCATE ON zc_assessment_response FROM zc_app;
REVOKE UPDATE, DELETE, TRUNCATE ON zc_risk_classification FROM zc_app;
REVOKE UPDATE, DELETE, TRUNCATE ON zc_plan_review FROM zc_app;
REVOKE UPDATE, DELETE, TRUNCATE ON zc_task_assignment_history FROM zc_app;
REVOKE UPDATE, DELETE, TRUNCATE ON zc_provider_action FROM zc_app;
REVOKE UPDATE, DELETE, TRUNCATE ON zc_report_snapshot FROM zc_app;
REVOKE UPDATE, DELETE, TRUNCATE ON zc_domain_audit FROM zc_app;
REVOKE UPDATE, DELETE, TRUNCATE ON zc_security_event FROM zc_app;

-- Id sequences.
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO zc_app;

-- Housekeeping.
GRANT SELECT ON zc_tenant TO zc_housekeeping;
GRANT SELECT, DELETE ON zc_idempotency_key TO zc_housekeeping;

-- -----------------------------------------------------------------------------
-- Sanity checks.
-- -----------------------------------------------------------------------------
DO $do$
DECLARE
  v_bad text;
BEGIN
  -- 1. zc_app owns nothing.
  SELECT string_agg(c.relname, ', ') INTO v_bad
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'public' AND c.relname LIKE 'zc\_%' AND c.relkind = 'r'
     AND pg_has_role('zc_app', c.relowner, 'MEMBER');
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'zc_app owns (or is a member of the owner of): %', v_bad;
  END IF;

  -- 2. zc_app has no DELETE / TRUNCATE anywhere.
  SELECT string_agg(c.relname, ', ') INTO v_bad
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'public' AND c.relname LIKE 'zc\_%' AND c.relkind = 'r'
     AND (has_table_privilege('zc_app', c.oid, 'DELETE') OR has_table_privilege('zc_app', c.oid, 'TRUNCATE'));
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'zc_app must hold no DELETE/TRUNCATE, but has it on: %', v_bad;
  END IF;

  -- 3. zc_app has no UPDATE on append-only tables.
  SELECT string_agg(t, ', ') INTO v_bad
    FROM unnest(ARRAY['zc_config_history', 'zc_assessment_response', 'zc_risk_classification', 'zc_plan_review', 'zc_task_assignment_history', 'zc_provider_action', 'zc_report_snapshot', 'zc_domain_audit', 'zc_security_event'
                ]::text[]) AS t
   WHERE has_table_privilege('zc_app', t, 'UPDATE');
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'zc_app must hold no UPDATE on append-only tables, but has it on: %', v_bad;
  END IF;

  -- 4. RLS enabled, forced and has a policy.
  SELECT string_agg(c.relname, ', ') INTO v_bad
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'public' AND c.relname LIKE 'zc\_%' AND c.relkind = 'r'
     AND c.relname NOT IN ('zc_tenant','zc_security_event')
     AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity
          OR NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid));
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'tables missing enabled+forced RLS or a policy: %', v_bad;
  END IF;
END
$do$;


-- #############################################################################
-- ##  V003__ambiguous_opt_out.sql
-- #############################################################################

-- =============================================================================
-- V003__ambiguous_opt_out.sql
-- =============================================================================
-- Corrected 2026-09-29 (ADR-0004): the reconstruction originally here is replaced
-- by the trial system's real V003, whose vocabulary PRD addendum ADD-001 Part B
-- names. An opt-out word inside a longer message is consent_opt_out_ambiguous:
-- outreach is suppressed at once and a human confirms before any withdrawal.
-- =============================================================================

ALTER TABLE zc_inbound_message
  DROP CONSTRAINT ck_zc_inbound_message_interpreted_as;
ALTER TABLE zc_inbound_message
  ADD CONSTRAINT ck_zc_inbound_message_interpreted_as CHECK (interpreted_as IN (
    'consent_opt_in','consent_opt_out','consent_opt_out_ambiguous','consent_decline',
    'assessment_answer','refill_reply','task_reply','unrecognised'));

-- #############################################################################
-- ##  V004__user_credentials.sql
-- #############################################################################

-- =============================================================================
-- V004__user_credentials.sql -- login credentials
-- =============================================================================
-- No RLS: login happens before the tenant is known.
-- =============================================================================

CREATE TABLE zc_user (
  id                   bigint GENERATED ALWAYS AS IDENTITY,
  tenant_id            bigint NOT NULL,
  org                  bigint, -- NULL for platform_admin
  email                text NOT NULL,
  password_hash        text NOT NULL,
  role                 text NOT NULL,
  status               text NOT NULL DEFAULT 'active',
  display_name         text,
  failed_login_count   integer NOT NULL DEFAULT 0,
  locked_until         timestamptz,
  last_login_at        timestamptz,
  password_changed_at  timestamptz NOT NULL DEFAULT now(),
  created_at           timestamptz NOT NULL DEFAULT now(),
  created_by           text NOT NULL CONSTRAINT ck_zc_user_created_by CHECK (btrim(created_by) <> ''),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  updated_by           text,
  row_version          integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_user_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_user PRIMARY KEY (id),
  CONSTRAINT fk_zc_user_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  -- Org must be in the same tenant.
  CONSTRAINT fk_zc_user_org FOREIGN KEY (tenant_id, org) REFERENCES zc_organisation (tenant_id, id),
  CONSTRAINT ck_zc_user_email CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+$'),
  CONSTRAINT ck_zc_user_role CHECK (btrim(role) <> ''),
  CONSTRAINT ck_zc_user_org_required CHECK (role = 'platform_admin' OR org IS NOT NULL),
  CONSTRAINT ck_zc_user_status CHECK (status IN ('active','disabled','locked')),
  -- Rejects obvious plaintext.
  CONSTRAINT ck_zc_user_password_hash CHECK (length(password_hash) >= 50),
  CONSTRAINT ck_zc_user_failed_logins CHECK (failed_login_count >= 0)
);
COMMENT ON TABLE zc_user IS 'Local login credentials. RLS-exempt by design (04D-style exemption, see zc_security_event).';

-- Email unique across all tenants, case-insensitive.
CREATE UNIQUE INDEX uq_zc_user_email ON zc_user (lower(email));
CREATE INDEX ix_zc_user_tenant ON zc_user (tenant_id);

CREATE TRIGGER trg_zc_user_touch BEFORE UPDATE ON zc_user
  FOR EACH ROW EXECUTE FUNCTION zc_touch_row();

GRANT SELECT, INSERT, UPDATE ON zc_user TO zc_app;
GRANT USAGE ON SEQUENCE zc_user_id_seq TO zc_app;

-- Add login event types.
ALTER TABLE zc_security_event DROP CONSTRAINT ck_zc_security_event_type;
ALTER TABLE zc_security_event ADD CONSTRAINT ck_zc_security_event_type CHECK (event_type IN (
  'authorisation_denied','tenant_violation_attempt','invalid_webhook_signature',
  'break_glass_use','audit_export',
  'authentication_failed','authentication_succeeded','account_locked'));


-- #############################################################################
-- ##  V005__tenant_whatsapp_binding.sql
-- #############################################################################

-- =============================================================================
-- V005__tenant_whatsapp_binding.sql -- WhatsApp number per tenant
-- =============================================================================
-- No RLS: the webhook finds the tenant from phone_number_id.
-- !! Security: access_token should be a secret-store reference, not the token.
-- =============================================================================

CREATE TABLE zc_tenant_whatsapp (
  tenant_id        bigint NOT NULL,
  phone_number_id  text NOT NULL,
  access_token     text NOT NULL,
  active           boolean NOT NULL DEFAULT true,
  created_at       timestamptz NOT NULL DEFAULT now(),
  created_by       text NOT NULL CONSTRAINT ck_zc_tenant_whatsapp_created_by CHECK (btrim(created_by) <> ''),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  updated_by       text,
  row_version      integer NOT NULL DEFAULT 1 CONSTRAINT ck_zc_tenant_whatsapp_row_version CHECK (row_version >= 1),
  CONSTRAINT pk_zc_tenant_whatsapp PRIMARY KEY (tenant_id),
  CONSTRAINT fk_zc_tenant_whatsapp_tenant FOREIGN KEY (tenant_id) REFERENCES zc_tenant (id),
  -- One tenant per number.
  CONSTRAINT uq_zc_tenant_whatsapp_phone_number UNIQUE (phone_number_id),
  CONSTRAINT ck_zc_tenant_whatsapp_phone_number CHECK (btrim(phone_number_id) <> ''),
  CONSTRAINT ck_zc_tenant_whatsapp_access_token CHECK (btrim(access_token) <> '')
);
COMMENT ON COLUMN zc_tenant_whatsapp.access_token IS
  'SECURITY: conflicts with 04D 14 (secrets never in the database). Should hold a secret-store reference, not the token.';

CREATE TRIGGER trg_zc_tenant_whatsapp_touch BEFORE UPDATE ON zc_tenant_whatsapp
  FOR EACH ROW EXECUTE FUNCTION zc_touch_row();

GRANT SELECT, INSERT, UPDATE ON zc_tenant_whatsapp TO zc_app;

-- #############################################################################
-- ##  Done
-- #############################################################################
COMMIT;

\echo 'ZCare database created: 60 tables. Next: set the zc_app password, then run verify/verify_schema.sql.'
