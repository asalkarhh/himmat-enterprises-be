-- Migration: V7__invoice_revisions_returns_refunds.sql
-- Description: Invoice revisions, customer returns, customer refunds, and audit snapshots

-- 1. Sequences for human-readable codes
CREATE SEQUENCE IF NOT EXISTS seq_customer_return_number START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_customer_refund_number START WITH 1 INCREMENT BY 1;

-- 2. Extend sales_invoice table
ALTER TABLE sales_invoice 
    ADD COLUMN IF NOT EXISTS original_payable_amount NUMERIC(18, 2) NULL,
    ADD COLUMN IF NOT EXISTS total_returned_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS revision_count INTEGER NOT NULL DEFAULT 0;

-- Backfill original_payable_amount from total_payable_amount for existing records
UPDATE sales_invoice 
SET original_payable_amount = total_payable_amount 
WHERE original_payable_amount IS NULL;

ALTER TABLE sales_invoice 
    ALTER COLUMN original_payable_amount SET NOT NULL;

-- 3. Extend sales_invoice_line table
ALTER TABLE sales_invoice_line 
    ADD COLUMN IF NOT EXISTS returned_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_active_in_current_revision BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT chk_sales_line_returned CHECK (returned_quantity >= 0 AND returned_quantity <= quantity);

-- 4. Sales Invoice Revision Header
CREATE TABLE IF NOT EXISTS sales_invoice_revision (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES sales_invoice(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    reason TEXT NOT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    actor_id BIGINT NOT NULL REFERENCES app_user(id),
    previous_subtotal_excl_tax NUMERIC(18, 2) NULL,
    previous_total_discount NUMERIC(18, 2) NOT NULL,
    previous_total_taxable NUMERIC(18, 2) NULL,
    previous_total_gst NUMERIC(18, 2) NULL,
    previous_total_payable NUMERIC(18, 2) NOT NULL,
    new_subtotal_excl_tax NUMERIC(18, 2) NULL,
    new_total_discount NUMERIC(18, 2) NOT NULL,
    new_total_taxable NUMERIC(18, 2) NULL,
    new_total_gst NUMERIC(18, 2) NULL,
    new_total_payable NUMERIC(18, 2) NOT NULL,
    delta_payable_amount NUMERIC(18, 2) NOT NULL,
    released_allocation_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    snapshot_payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_sales_invoice_revision UNIQUE (invoice_id, revision_number)
);

CREATE INDEX IF NOT EXISTS idx_invoice_revision_invoice_id ON sales_invoice_revision (invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_revision_business_event_id ON sales_invoice_revision (business_event_id);

-- 5. Sales Invoice Revision Line Detail
CREATE TABLE IF NOT EXISTS sales_invoice_revision_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    revision_id BIGINT NOT NULL REFERENCES sales_invoice_revision(id) ON DELETE CASCADE,
    invoice_line_id BIGINT NOT NULL REFERENCES sales_invoice_line(id),
    variant_id BIGINT NOT NULL REFERENCES product_variant(id),
    line_index INTEGER NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    previous_quantity INTEGER NULL,
    new_quantity INTEGER NULL,
    delta_quantity INTEGER NOT NULL DEFAULT 0,
    previous_unit_net_price NUMERIC(18, 4) NULL,
    new_unit_net_price NUMERIC(18, 4) NULL,
    previous_line_total NUMERIC(18, 2) NULL,
    new_line_total NUMERIC(18, 2) NULL,
    delta_line_total NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    is_price_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason TEXT NULL,
    CONSTRAINT chk_revision_line_change_type CHECK (change_type IN ('UNCHANGED', 'MODIFIED', 'ADDED', 'REMOVED'))
);

CREATE INDEX IF NOT EXISTS idx_revision_line_revision_id ON sales_invoice_revision_line (revision_id);
CREATE INDEX IF NOT EXISTS idx_revision_line_invoice_line_id ON sales_invoice_revision_line (invoice_line_id);

-- 6. Customer Returns Header
CREATE TABLE IF NOT EXISTS customer_return (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_number VARCHAR(30) NOT NULL UNIQUE,
    invoice_id BIGINT NOT NULL REFERENCES sales_invoice(id),
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    return_date DATE NOT NULL,
    total_return_amount NUMERIC(18, 2) NOT NULL,
    released_allocation_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    reason TEXT NOT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_customer_return_amount CHECK (total_return_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_customer_return_invoice_id ON customer_return (invoice_id);
CREATE INDEX IF NOT EXISTS idx_customer_return_customer_id ON customer_return (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_return_date ON customer_return (return_date);

-- 7. Customer Return Lines
CREATE TABLE IF NOT EXISTS customer_return_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_return_id BIGINT NOT NULL REFERENCES customer_return(id) ON DELETE CASCADE,
    invoice_line_id BIGINT NOT NULL REFERENCES sales_invoice_line(id),
    variant_id BIGINT NOT NULL REFERENCES product_variant(id),
    returned_quantity INTEGER NOT NULL,
    unit_price NUMERIC(18, 4) NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL,
    valuation_basis VARCHAR(60) NOT NULL,
    gst_rate_pct NUMERIC(7, 4) NULL,
    taxable_amount NUMERIC(18, 2) NULL,
    tax_amount NUMERIC(18, 2) NULL,
    CONSTRAINT chk_customer_return_line_qty CHECK (returned_quantity > 0),
    CONSTRAINT chk_customer_return_line_amount CHECK (total_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_customer_return_line_return_id ON customer_return_line (customer_return_id);
CREATE INDEX IF NOT EXISTS idx_customer_return_line_invoice_line_id ON customer_return_line (invoice_line_id);
CREATE INDEX IF NOT EXISTS idx_customer_return_line_variant_id ON customer_return_line (variant_id);

-- 8. Customer Refunds
CREATE TABLE IF NOT EXISTS customer_refund (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    refund_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    refund_date DATE NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL,
    reason TEXT NOT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_customer_refund_amount CHECK (total_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_customer_refund_customer_id ON customer_refund (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_refund_date ON customer_refund (refund_date);

-- 9. Customer Refund Components (CASH, ONLINE split)
CREATE TABLE IF NOT EXISTS customer_refund_component (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_refund_id BIGINT NOT NULL REFERENCES customer_refund(id) ON DELETE CASCADE,
    method VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    online_reference VARCHAR(100) NULL,
    CONSTRAINT chk_customer_refund_comp_method CHECK (method IN ('CASH', 'ONLINE')),
    CONSTRAINT chk_customer_refund_comp_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_customer_refund_comp_refund_id ON customer_refund_component (customer_refund_id);

-- 10. Customer Refund Sources (Traces which receipt / credit funds were refunded)
CREATE TABLE IF NOT EXISTS customer_refund_source (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_refund_id BIGINT NOT NULL REFERENCES customer_refund(id) ON DELETE CASCADE,
    customer_receipt_id BIGINT NOT NULL REFERENCES customer_receipt(id),
    amount NUMERIC(18, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_customer_refund_source_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_customer_refund_source_refund_id ON customer_refund_source (customer_refund_id);
CREATE INDEX IF NOT EXISTS idx_customer_refund_source_receipt_id ON customer_refund_source (customer_receipt_id);
