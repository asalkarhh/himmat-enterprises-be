-- Migration: V6__customer_invoices_and_ledger.sql
-- Description: Immediate customer billing, receipts, advances, allocations, and customer ledger

-- 1. Sequences for human-readable codes
CREATE SEQUENCE IF NOT EXISTS seq_invoice_number START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_customer_receipt_number START WITH 1 INCREMENT BY 1;

-- 2. Sales Invoice Header
CREATE TABLE IF NOT EXISTS sales_invoice (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    customer_site_id BIGINT NULL REFERENCES customer_site(id),
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    display_format VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'POSTED',
    settlement_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    customer_name VARCHAR(200) NOT NULL,
    customer_phone VARCHAR(30) NULL,
    customer_gstin VARCHAR(20) NULL,
    customer_address TEXT NULL,
    site_name VARCHAR(200) NULL,
    site_address TEXT NULL,
    subtotal_excl_tax NUMERIC(18, 2) NULL,
    total_discount_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    total_taxable_amount NUMERIC(18, 2) NULL,
    total_gst_amount NUMERIC(18, 2) NULL,
    total_payable_amount NUMERIC(18, 2) NOT NULL,
    total_allocated_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    tax_breakdown_available BOOLEAN NOT NULL DEFAULT FALSE,
    pricing_fingerprint VARCHAR(128) NULL,
    notes TEXT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_sales_invoice_payable CHECK (total_payable_amount >= 0),
    CONSTRAINT chk_sales_invoice_allocated CHECK (total_allocated_amount >= 0 AND total_allocated_amount <= total_payable_amount),
    CONSTRAINT chk_sales_invoice_display_format CHECK (display_format IN ('NORMAL', 'GST')),
    CONSTRAINT chk_sales_invoice_status CHECK (status IN ('POSTED', 'CANCELLED')),
    CONSTRAINT chk_sales_invoice_settlement CHECK (settlement_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID'))
);

CREATE INDEX IF NOT EXISTS idx_sales_invoice_customer_id ON sales_invoice (customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_site_id ON sales_invoice (customer_site_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_date ON sales_invoice (invoice_date);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_due_date ON sales_invoice (due_date);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_settlement ON sales_invoice (settlement_status);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_business_event_id ON sales_invoice (business_event_id);

-- 3. Sales Invoice Lines
CREATE TABLE IF NOT EXISTS sales_invoice_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES sales_invoice(id) ON DELETE CASCADE,
    variant_id BIGINT NOT NULL REFERENCES product_variant(id),
    line_index INTEGER NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    size_label VARCHAR(120) NOT NULL,
    pricing_method VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    base_unit_price NUMERIC(18, 4) NOT NULL,
    discount_pct NUMERIC(7, 4) NOT NULL DEFAULT 0.0000,
    discount_source VARCHAR(50) NOT NULL DEFAULT 'NONE',
    unit_net_price NUMERIC(18, 4) NOT NULL,
    line_total NUMERIC(18, 2) NOT NULL,
    is_price_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason TEXT NULL,
    gst_rate_pct NUMERIC(7, 4) NULL,
    taxable_amount NUMERIC(18, 2) NULL,
    tax_amount NUMERIC(18, 2) NULL,
    tax_breakdown_available BOOLEAN NOT NULL DEFAULT FALSE,
    purchase_cost_excl_tax NUMERIC(18, 4) NULL,
    cost_available BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_sales_line_qty CHECK (quantity > 0),
    CONSTRAINT chk_sales_line_discount CHECK (discount_pct >= 0 AND discount_pct <= 100),
    CONSTRAINT chk_sales_line_total CHECK (line_total >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sales_invoice_line_invoice_id ON sales_invoice_line (invoice_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_line_variant_id ON sales_invoice_line (variant_id);

-- 4. Customer Receipts
CREATE TABLE IF NOT EXISTS customer_receipt (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    receipt_date DATE NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL,
    unallocated_amount NUMERIC(18, 2) NOT NULL,
    notes TEXT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_customer_receipt_amount CHECK (total_amount > 0),
    CONSTRAINT chk_customer_receipt_unallocated CHECK (unallocated_amount >= 0 AND unallocated_amount <= total_amount)
);

CREATE INDEX IF NOT EXISTS idx_customer_receipt_customer_id ON customer_receipt (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_receipt_date ON customer_receipt (receipt_date);

-- 5. Customer Receipt Components (CASH, ONLINE split)
CREATE TABLE IF NOT EXISTS customer_receipt_component (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_receipt_id BIGINT NOT NULL REFERENCES customer_receipt(id) ON DELETE CASCADE,
    method VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    online_reference VARCHAR(100) NULL,
    CONSTRAINT chk_receipt_comp_method CHECK (method IN ('CASH', 'ONLINE')),
    CONSTRAINT chk_receipt_comp_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_receipt_comp_receipt_id ON customer_receipt_component (customer_receipt_id);

-- 6. Receipt Allocation (Matches customer receipts/advances to sales invoices)
CREATE TABLE IF NOT EXISTS receipt_allocation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_receipt_id BIGINT NOT NULL REFERENCES customer_receipt(id),
    invoice_id BIGINT NOT NULL REFERENCES sales_invoice(id),
    allocated_amount NUMERIC(18, 2) NOT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_receipt_alloc_amount CHECK (allocated_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_receipt_alloc_receipt_id ON receipt_allocation (customer_receipt_id);
CREATE INDEX IF NOT EXISTS idx_receipt_alloc_invoice_id ON receipt_allocation (invoice_id);

-- 7. Immutable Customer Ledger
CREATE TABLE IF NOT EXISTS customer_ledger_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    running_balance NUMERIC(18, 2) NOT NULL,
    effective_date DATE NOT NULL,
    notes TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_customer_ledger_source_type CHECK (source_type IN (
        'INVOICE',
        'RECEIPT',
        'CUSTOMER_RETURN',
        'REFUND'
    ))
);

CREATE INDEX IF NOT EXISTS idx_customer_ledger_customer_id ON customer_ledger_entry (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_ledger_effective_date ON customer_ledger_entry (customer_id, effective_date);
CREATE INDEX IF NOT EXISTS idx_customer_ledger_source ON customer_ledger_entry (source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_customer_ledger_business_event_id ON customer_ledger_entry (business_event_id);
