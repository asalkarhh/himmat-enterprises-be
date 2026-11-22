-- Migration: V5__supplier_purchases_and_ledger.sql
-- Description: Supplier purchases, inward goods, supplier payments, payment allocations, purchase returns, refunds, and supplier ledger

-- 1. Sequences for human-readable codes
CREATE SEQUENCE IF NOT EXISTS seq_purchase_number START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_supplier_payment_number START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_purchase_return_number START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_supplier_refund_number START WITH 1 INCREMENT BY 1;

-- 2. Purchase Header
CREATE TABLE IF NOT EXISTS purchase (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_number VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    supplier_invoice_number VARCHAR(100) NULL,
    purchase_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'POSTED',
    supplier_name VARCHAR(200) NOT NULL,
    supplier_contact VARCHAR(30) NULL,
    supplier_gstin VARCHAR(20) NULL,
    subtotal_excl_tax NUMERIC(18, 2) NOT NULL,
    total_discount_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    total_gst_amount NUMERIC(18, 2) NULL,
    total_payable_amount NUMERIC(18, 2) NOT NULL,
    total_allocated_amount NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    notes TEXT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_purchase_status CHECK (status IN ('POSTED', 'CANCELLED')),
    CONSTRAINT chk_purchase_subtotal CHECK (subtotal_excl_tax >= 0),
    CONSTRAINT chk_purchase_payable CHECK (total_payable_amount >= 0),
    CONSTRAINT chk_purchase_allocated CHECK (total_allocated_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_purchase_supplier_id ON purchase (supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_date ON purchase (purchase_date);
CREATE INDEX IF NOT EXISTS idx_purchase_status ON purchase (status);
CREATE INDEX IF NOT EXISTS idx_purchase_business_event_id ON purchase (business_event_id);

-- 3. Purchase Lines
CREATE TABLE IF NOT EXISTS purchase_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id BIGINT NOT NULL REFERENCES purchase(id) ON DELETE CASCADE,
    variant_id BIGINT NOT NULL REFERENCES product_variant(id),
    line_index INTEGER NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    size_label VARCHAR(120) NOT NULL,
    quantity INTEGER NOT NULL,
    returned_quantity INTEGER NOT NULL DEFAULT 0,
    price_basis VARCHAR(30) NOT NULL,
    entered_unit_price NUMERIC(18, 2) NOT NULL,
    discount_pct NUMERIC(7, 4) NOT NULL DEFAULT 0.0000,
    gst_rate_pct NUMERIC(7, 4) NULL,
    net_unit_cost NUMERIC(18, 4) NULL,
    gross_unit_cost NUMERIC(18, 4) NOT NULL,
    line_total NUMERIC(18, 2) NOT NULL,
    update_product_cost BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_purchase_line_qty CHECK (quantity > 0),
    CONSTRAINT chk_purchase_line_returned_qty CHECK (returned_quantity >= 0 AND returned_quantity <= quantity),
    CONSTRAINT chk_purchase_line_price_basis CHECK (price_basis IN ('GST_INCLUSIVE', 'GST_EXCLUSIVE')),
    CONSTRAINT chk_purchase_line_discount CHECK (discount_pct >= 0 AND discount_pct <= 100),
    CONSTRAINT chk_purchase_line_line_total CHECK (line_total >= 0)
);

CREATE INDEX IF NOT EXISTS idx_purchase_line_purchase_id ON purchase_line (purchase_id);
CREATE INDEX IF NOT EXISTS idx_purchase_line_variant_id ON purchase_line (variant_id);

-- 4. Supplier Payments
CREATE TABLE IF NOT EXISTS supplier_payment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_number VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    payment_date DATE NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL,
    unallocated_amount NUMERIC(18, 2) NOT NULL,
    notes TEXT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_supplier_payment_amount CHECK (total_amount > 0),
    CONSTRAINT chk_supplier_payment_unallocated CHECK (unallocated_amount >= 0 AND unallocated_amount <= total_amount)
);

CREATE INDEX IF NOT EXISTS idx_supplier_payment_supplier_id ON supplier_payment (supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplier_payment_date ON supplier_payment (payment_date);

-- 5. Supplier Refunds (Money received back from supplier to shop)
CREATE TABLE IF NOT EXISTS supplier_refund (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    refund_number VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    refund_date DATE NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL,
    reason TEXT NOT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_supplier_refund_amount CHECK (total_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_supplier_refund_supplier_id ON supplier_refund (supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplier_refund_date ON supplier_refund (refund_date);

-- 6. Payment Components (CASH, ONLINE split for payments and refunds)
CREATE TABLE IF NOT EXISTS payment_component (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_payment_id BIGINT NULL REFERENCES supplier_payment(id) ON DELETE CASCADE,
    supplier_refund_id BIGINT NULL REFERENCES supplier_refund(id) ON DELETE CASCADE,
    method VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    online_reference VARCHAR(100) NULL,
    CONSTRAINT chk_payment_component_method CHECK (method IN ('CASH', 'ONLINE')),
    CONSTRAINT chk_payment_component_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_component_owner CHECK (
        (supplier_payment_id IS NOT NULL AND supplier_refund_id IS NULL) OR
        (supplier_payment_id IS NULL AND supplier_refund_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_payment_component_payment ON payment_component (supplier_payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_component_refund ON payment_component (supplier_refund_id);

-- 7. Payment Allocation (Matches payment against purchase invoices)
CREATE TABLE IF NOT EXISTS payment_allocation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_payment_id BIGINT NOT NULL REFERENCES supplier_payment(id),
    purchase_id BIGINT NOT NULL REFERENCES purchase(id),
    allocated_amount NUMERIC(18, 2) NOT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_payment_allocation_amount CHECK (allocated_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_allocation_payment ON payment_allocation (supplier_payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_allocation_purchase ON payment_allocation (purchase_id);

-- 8. Purchase Returns
CREATE TABLE IF NOT EXISTS purchase_return (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_number VARCHAR(30) NOT NULL UNIQUE,
    purchase_id BIGINT NOT NULL REFERENCES purchase(id),
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    return_date DATE NOT NULL,
    total_return_amount NUMERIC(18, 2) NOT NULL,
    reason TEXT NOT NULL,
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_purchase_return_amount CHECK (total_return_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_purchase_return_purchase_id ON purchase_return (purchase_id);
CREATE INDEX IF NOT EXISTS idx_purchase_return_supplier_id ON purchase_return (supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_return_date ON purchase_return (return_date);

-- 9. Purchase Return Lines
CREATE TABLE IF NOT EXISTS purchase_return_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_return_id BIGINT NOT NULL REFERENCES purchase_return(id) ON DELETE CASCADE,
    purchase_line_id BIGINT NOT NULL REFERENCES purchase_line(id),
    variant_id BIGINT NOT NULL REFERENCES product_variant(id),
    returned_quantity INTEGER NOT NULL,
    unit_cost NUMERIC(18, 4) NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL,
    CONSTRAINT chk_purchase_return_line_qty CHECK (returned_quantity > 0),
    CONSTRAINT chk_purchase_return_line_amount CHECK (total_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_purchase_return_line_return_id ON purchase_return_line (purchase_return_id);
CREATE INDEX IF NOT EXISTS idx_purchase_return_line_variant_id ON purchase_return_line (variant_id);

-- 10. Immutable Supplier Ledger
CREATE TABLE IF NOT EXISTS supplier_ledger_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    running_balance NUMERIC(18, 2) NOT NULL,
    effective_date DATE NOT NULL,
    notes TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_supplier_ledger_source_type CHECK (source_type IN (
        'PURCHASE',
        'PAYMENT',
        'PURCHASE_RETURN',
        'REFUND'
    ))
);

CREATE INDEX IF NOT EXISTS idx_supplier_ledger_supplier_id ON supplier_ledger_entry (supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplier_ledger_effective_date ON supplier_ledger_entry (supplier_id, effective_date);
CREATE INDEX IF NOT EXISTS idx_supplier_ledger_source ON supplier_ledger_entry (source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_supplier_ledger_business_event_id ON supplier_ledger_entry (business_event_id);
