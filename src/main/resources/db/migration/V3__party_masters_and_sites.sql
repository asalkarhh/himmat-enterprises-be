-- Migration: V3__party_masters_and_sites.sql
-- Description: Customer, Customer Site, and Supplier master data schema

-- 1. Sequences for Code Generation
CREATE SEQUENCE IF NOT EXISTS seq_customer_code START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_supplier_code START WITH 1 INCREMENT BY 1;

-- 2. Customer Table
CREATE TABLE IF NOT EXISTS customer (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_code VARCHAR(30) NOT NULL UNIQUE,
    customer_type VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    phone VARCHAR(30) NULL,
    email VARCHAR(150) NULL,
    gstin VARCHAR(20) NULL,
    address_line1 TEXT NULL,
    address_line2 TEXT NULL,
    city VARCHAR(100) NULL,
    state VARCHAR(100) NULL,
    postal_code VARCHAR(20) NULL,
    notes TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_customer_type CHECK (customer_type IN ('RETAIL', 'CONTRACTOR', 'BUILDER'))
);

CREATE INDEX IF NOT EXISTS idx_customer_code ON customer (customer_code);
CREATE INDEX IF NOT EXISTS idx_customer_name ON customer (normalized_name);
CREATE INDEX IF NOT EXISTS idx_customer_phone ON customer (phone);
CREATE INDEX IF NOT EXISTS idx_customer_is_active ON customer (is_active);
CREATE INDEX IF NOT EXISTS idx_customer_type ON customer (customer_type);

-- 3. Customer Site Table
CREATE TABLE IF NOT EXISTS customer_site (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    site_name VARCHAR(200) NOT NULL,
    normalized_site_name VARCHAR(200) NOT NULL,
    address_line1 TEXT NULL,
    address_line2 TEXT NULL,
    city VARCHAR(100) NULL,
    state VARCHAR(100) NULL,
    postal_code VARCHAR(20) NULL,
    contact_person VARCHAR(150) NULL,
    contact_phone VARCHAR(30) NULL,
    notes TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT uq_customer_site_name UNIQUE (customer_id, normalized_site_name)
);

CREATE INDEX IF NOT EXISTS idx_customer_site_customer_id ON customer_site (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_site_is_active ON customer_site (is_active);

-- 4. Supplier Table
CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(150) NULL,
    phone VARCHAR(30) NULL,
    email VARCHAR(150) NULL,
    gstin VARCHAR(20) NULL,
    address_line1 TEXT NULL,
    address_line2 TEXT NULL,
    city VARCHAR(100) NULL,
    state VARCHAR(100) NULL,
    postal_code VARCHAR(20) NULL,
    notes TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_supplier_code ON supplier (supplier_code);
CREATE INDEX IF NOT EXISTS idx_supplier_name ON supplier (normalized_name);
CREATE INDEX IF NOT EXISTS idx_supplier_phone ON supplier (phone);
CREATE INDEX IF NOT EXISTS idx_supplier_is_active ON supplier (is_active);
