-- Migration: V1__init_auth_and_settings.sql
-- Description: Core schema foundation for Himmat Enterprises: app_user, shop_settings, and business_event

-- 1. Application Users
CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    password_hash TEXT NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    role VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT chk_app_user_role CHECK (role IN ('OWNER', 'MANAGER')),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT fk_app_user_created_by FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT fk_app_user_updated_by FOREIGN KEY (updated_by) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_app_user_role ON app_user(role);
CREATE INDEX IF NOT EXISTS idx_app_user_is_active ON app_user(is_active);

-- 2. Shop Settings (Singleton id=1)
CREATE TABLE IF NOT EXISTS shop_settings (
    id BIGINT PRIMARY KEY DEFAULT 1,
    name VARCHAR(200) NOT NULL,
    address TEXT NULL,
    phone VARCHAR(30) NULL,
    email VARCHAR(150) NULL,
    gstin VARCHAR(20) NULL,
    state_code VARCHAR(10) NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    credit_days INTEGER NOT NULL DEFAULT 30,
    invoice_prefix VARCHAR(20) NOT NULL DEFAULT 'HE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT chk_shop_settings_singleton CHECK (id = 1),
    CONSTRAINT fk_shop_settings_created_by FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT fk_shop_settings_updated_by FOREIGN KEY (updated_by) REFERENCES app_user(id)
);

-- 3. Business Event (Audit and idempotency baseline)
CREATE TABLE IF NOT EXISTS business_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    actor_id BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    reverses_event_id BIGINT NULL,
    reason TEXT NULL,
    audit_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_business_event_request_key UNIQUE (request_key),
    CONSTRAINT fk_business_event_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT fk_business_event_reverses FOREIGN KEY (reverses_event_id) REFERENCES business_event(id)
);

CREATE INDEX IF NOT EXISTS idx_business_event_actor ON business_event(actor_id);
CREATE INDEX IF NOT EXISTS idx_business_event_occurred ON business_event(occurred_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_business_event_reverses ON business_event(reverses_event_id) WHERE reverses_event_id IS NOT NULL;
