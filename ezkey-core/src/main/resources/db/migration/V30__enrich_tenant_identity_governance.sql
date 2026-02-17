--
-- V30: Enrich tenant with organizational identity, contact, and governance fields
--
-- SOC 2 Controls: CC2.1 (Communication), CC6.1 (Logical Access),
--                 CC6.3 (Access Removal), CC7.2 (Change Management)
--
-- All new columns are nullable for backward compatibility with existing tenants.
--

-- ============================================================
-- 1. Organizational Identity
-- ============================================================

ALTER TABLE ezkey_tenant
  ADD COLUMN organization_name VARCHAR(255);
COMMENT ON COLUMN ezkey_tenant.organization_name
  IS 'Legal name of the organization (distinct from tenant_name)';

ALTER TABLE ezkey_tenant
  ADD COLUMN organization_domain VARCHAR(255);
COMMENT ON COLUMN ezkey_tenant.organization_domain
  IS 'Primary domain of the organization (e.g. acme.com)';

ALTER TABLE ezkey_tenant
  ADD COLUMN country_code VARCHAR(2);
COMMENT ON COLUMN ezkey_tenant.country_code
  IS 'ISO 3166-1 alpha-2 country code for legal jurisdiction';

ALTER TABLE ezkey_tenant
  ADD COLUMN timezone VARCHAR(50);
COMMENT ON COLUMN ezkey_tenant.timezone
  IS 'IANA timezone identifier (e.g. America/Montreal)';

-- ============================================================
-- 2. Operational Contact
-- ============================================================

ALTER TABLE ezkey_tenant
  ADD COLUMN primary_contact_name VARCHAR(255);
COMMENT ON COLUMN ezkey_tenant.primary_contact_name
  IS 'Name of the primary technical contact for this tenant';

ALTER TABLE ezkey_tenant
  ADD COLUMN primary_contact_email VARCHAR(255);
COMMENT ON COLUMN ezkey_tenant.primary_contact_email
  IS 'Email of the primary technical contact for incidents and notifications';

-- ============================================================
-- 3. Governance and Lifecycle
-- ============================================================

ALTER TABLE ezkey_tenant
  ADD COLUMN updated_at TIMESTAMPTZ;
COMMENT ON COLUMN ezkey_tenant.updated_at
  IS 'Timestamp of the last modification to this tenant record';

ALTER TABLE ezkey_tenant
  ADD COLUMN updated_by_admin_id INT
    REFERENCES ezkey_admin(admin_id);
COMMENT ON COLUMN ezkey_tenant.updated_by_admin_id
  IS 'Admin who last modified this tenant record (SOC 2 CC7.2)';

ALTER TABLE ezkey_tenant
  ADD COLUMN deactivated_at TIMESTAMPTZ;
COMMENT ON COLUMN ezkey_tenant.deactivated_at
  IS 'Timestamp when the tenant was deactivated (SOC 2 CC6.3)';

ALTER TABLE ezkey_tenant
  ADD COLUMN deactivated_by_admin_id INT
    REFERENCES ezkey_admin(admin_id);
COMMENT ON COLUMN ezkey_tenant.deactivated_by_admin_id
  IS 'Admin who deactivated this tenant (SOC 2 CC6.3)';

-- ============================================================
-- 4. Indexes
-- ============================================================

CREATE INDEX idx_tenant_organization_domain
  ON ezkey_tenant (organization_domain)
  WHERE organization_domain IS NOT NULL;

CREATE INDEX idx_tenant_country_code
  ON ezkey_tenant (country_code)
  WHERE country_code IS NOT NULL;

-- ============================================================
-- 5. Constraints
-- ============================================================

ALTER TABLE ezkey_tenant
  ADD CONSTRAINT chk_tenant_country_code
  CHECK (country_code ~ '^[A-Z]{2}$');

ALTER TABLE ezkey_tenant
  ADD CONSTRAINT chk_tenant_contact_email
  CHECK (primary_contact_email ~ '^[^@]+@[^@]+\.[^@]+$');
