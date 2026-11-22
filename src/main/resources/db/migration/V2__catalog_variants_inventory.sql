-- Migration: V2__catalog_variants_inventory.sql
-- Description: Product Catalog, Sellable Variants, and Inventory Balance schema

-- 1. Brand Table
CREATE TABLE IF NOT EXISTS brand (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_brand_normalized_name ON brand (LOWER(TRIM(name)));
CREATE INDEX IF NOT EXISTS idx_brand_is_active ON brand (is_active);

-- 2. Category Table
CREATE TABLE IF NOT EXISTS category (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    purchase_discount_pct NUMERIC(7,4) NOT NULL DEFAULT 0.0000,
    sales_discount_pct NUMERIC(7,4) NOT NULL DEFAULT 0.0000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_category_purchase_discount CHECK (purchase_discount_pct >= 0 AND purchase_discount_pct <= 100),
    CONSTRAINT chk_category_sales_discount CHECK (sales_discount_pct >= 0 AND sales_discount_pct <= 100)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_category_normalized_name ON category (LOWER(TRIM(name)));
CREATE INDEX IF NOT EXISTS idx_category_is_active ON category (is_active);

-- 3. Subcategory Table
CREATE TABLE IF NOT EXISTS subcategory (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES category(id),
    name VARCHAR(160) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subcategory_category_name ON subcategory (category_id, LOWER(TRIM(name)));
CREATE INDEX IF NOT EXISTS idx_subcategory_category_id ON subcategory (category_id);
CREATE INDEX IF NOT EXISTS idx_subcategory_is_active ON subcategory (is_active);

-- 4. Product Family Table
CREATE TABLE IF NOT EXISTS product (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    brand_id BIGINT NOT NULL REFERENCES brand(id),
    subcategory_id BIGINT NOT NULL REFERENCES subcategory(id),
    name VARCHAR(240) NOT NULL,
    description TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_brand_subcat_name ON product (brand_id, subcategory_id, LOWER(TRIM(name)));
CREATE INDEX IF NOT EXISTS idx_product_brand_id ON product (brand_id);
CREATE INDEX IF NOT EXISTS idx_product_subcategory_id ON product (subcategory_id);
CREATE INDEX IF NOT EXISTS idx_product_is_active ON product (is_active);

-- 5. Product Variant Table (Sellable size/capacity variants)
CREATE TABLE IF NOT EXISTS product_variant (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id),
    sku VARCHAR(100) NOT NULL,
    size_label VARCHAR(120) NOT NULL,
    pricing_method VARCHAR(30) NOT NULL,
    mrp_incl_tax NUMERIC(18,2) NULL,
    fixed_price_incl_tax NUMERIC(18,2) NULL,
    purchase_cost_excl_tax NUMERIC(18,4) NULL,
    profit_pct NUMERIC(7,4) NULL,
    gst_rate_pct NUMERIC(7,4) NULL,
    hsn_code VARCHAR(20) NULL,
    low_stock_threshold INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_variant_pricing_method CHECK (pricing_method IN ('MRP_DISCOUNT', 'COST_PLUS', 'FIXED')),
    CONSTRAINT chk_variant_mrp_incl_tax CHECK (mrp_incl_tax IS NULL OR mrp_incl_tax >= 0),
    CONSTRAINT chk_variant_fixed_price CHECK (fixed_price_incl_tax IS NULL OR fixed_price_incl_tax >= 0),
    CONSTRAINT chk_variant_purchase_cost CHECK (purchase_cost_excl_tax IS NULL OR purchase_cost_excl_tax >= 0),
    CONSTRAINT chk_variant_profit_pct CHECK (profit_pct IS NULL OR profit_pct >= 0),
    CONSTRAINT chk_variant_gst_rate CHECK (gst_rate_pct IS NULL OR (gst_rate_pct >= 0 AND gst_rate_pct <= 100)),
    CONSTRAINT chk_variant_low_stock CHECK (low_stock_threshold >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_variant_sku ON product_variant (sku);
CREATE UNIQUE INDEX IF NOT EXISTS uq_product_variant_product_size ON product_variant (product_id, LOWER(TRIM(size_label)));
CREATE INDEX IF NOT EXISTS idx_product_variant_product_id ON product_variant (product_id);
CREATE INDEX IF NOT EXISTS idx_product_variant_is_active ON product_variant (is_active);

-- 6. Inventory Balance Table (1-to-1 with variant, created atomically)
CREATE TABLE IF NOT EXISTS inventory_balance (
    variant_id BIGINT PRIMARY KEY REFERENCES product_variant(id),
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    opening_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_inventory_balance_non_negative CHECK (quantity_on_hand >= 0)
);
