-- Migration: V4__stock_movements_and_inventory.sql
-- Description: Stock Movement history and inventory constraints

-- 1. Create stock_movement table
CREATE TABLE IF NOT EXISTS stock_movement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES product_variant(id),
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    movement_type VARCHAR(50) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    source_entity_type VARCHAR(50) NOT NULL,
    source_entity_id BIGINT NULL,
    source_line_id BIGINT NULL,
    source_effect_key VARCHAR(120) NULL,
    reason TEXT NULL,
    actor_id BIGINT NOT NULL REFERENCES app_user(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_stock_movement_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_stock_movement_quantity CHECK (quantity > 0),
    CONSTRAINT chk_stock_movement_quantity_before CHECK (quantity_before >= 0),
    CONSTRAINT chk_stock_movement_quantity_after CHECK (quantity_after >= 0),
    CONSTRAINT chk_stock_movement_arithmetic CHECK (
        (direction = 'IN' AND quantity_after = quantity_before + quantity) OR
        (direction = 'OUT' AND quantity_after = quantity_before - quantity)
    ),
    CONSTRAINT chk_stock_movement_type CHECK (movement_type IN (
        'MANUAL_ADJUSTMENT_IN',
        'MANUAL_ADJUSTMENT_OUT',
        'PURCHASE_RECEIPT',
        'PURCHASE_RETURN',
        'SALE',
        'CUSTOMER_RETURN',
        'REVERSAL'
    ))
);

-- Unique index for idempotent source effects if provided
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_movement_source_effect 
    ON stock_movement (source_effect_key) 
    WHERE source_effect_key IS NOT NULL;

-- Query performance indexes
CREATE INDEX IF NOT EXISTS idx_stock_movement_variant_id ON stock_movement (variant_id);
CREATE INDEX IF NOT EXISTS idx_stock_movement_business_event_id ON stock_movement (business_event_id);
CREATE INDEX IF NOT EXISTS idx_stock_movement_occurred_at ON stock_movement (occurred_at);
CREATE INDEX IF NOT EXISTS idx_stock_movement_type ON stock_movement (movement_type);
CREATE INDEX IF NOT EXISTS idx_stock_movement_direction ON stock_movement (direction);
CREATE INDEX IF NOT EXISTS idx_stock_movement_source ON stock_movement (source_entity_type, source_entity_id);

-- 2. Reconciliation safety: Ensure any variant without an inventory balance receives one with 0
INSERT INTO inventory_balance (variant_id, quantity_on_hand, opening_confirmed, version, created_at, updated_at, updated_by)
SELECT pv.id, 0, FALSE, 0, pv.created_at, pv.updated_at, pv.created_by
FROM product_variant pv
WHERE NOT EXISTS (
    SELECT 1 FROM inventory_balance ib WHERE ib.variant_id = pv.id
);
