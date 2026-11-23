-- Migration: V8__operational_expenses.sql
-- Description: Operational expenses register, split payment components, and audit fields

-- 1. Sequence for human-readable expense number (EXP-00001)
CREATE SEQUENCE IF NOT EXISTS seq_expense_number START WITH 1 INCREMENT BY 1;

-- 2. Operational Expense Header
CREATE TABLE IF NOT EXISTS operational_expense (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    expense_number VARCHAR(30) NOT NULL UNIQUE,
    expense_date DATE NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'POSTED',
    external_reference VARCHAR(100) NULL,
    notes TEXT NULL,
    reversal_reason TEXT NULL,
    reversal_business_date DATE NULL,
    reversed_at TIMESTAMPTZ NULL,
    reversed_by BIGINT NULL REFERENCES app_user(id),
    business_event_id BIGINT NOT NULL REFERENCES business_event(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NULL REFERENCES app_user(id),
    updated_by BIGINT NULL REFERENCES app_user(id),
    CONSTRAINT chk_expense_amount CHECK (amount > 0),
    CONSTRAINT chk_expense_category CHECK (category IN ('RENT', 'UTILITIES', 'SALARY', 'TRANSPORT', 'SHOP_OPERATIONS', 'OTHER')),
    CONSTRAINT chk_expense_status CHECK (status IN ('POSTED', 'REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_expense_date ON operational_expense(expense_date);
CREATE INDEX IF NOT EXISTS idx_expense_category ON operational_expense(category);
CREATE INDEX IF NOT EXISTS idx_expense_status ON operational_expense(status);
CREATE INDEX IF NOT EXISTS idx_expense_business_event_id ON operational_expense(business_event_id);

-- 3. Operational Expense Payment Components (CASH, ONLINE split)
CREATE TABLE IF NOT EXISTS operational_expense_component (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    expense_id BIGINT NOT NULL REFERENCES operational_expense(id) ON DELETE CASCADE,
    method VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    online_reference VARCHAR(100) NULL,
    CONSTRAINT chk_expense_comp_method CHECK (method IN ('CASH', 'ONLINE')),
    CONSTRAINT chk_expense_comp_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_expense_comp_expense_id ON operational_expense_component(expense_id);
