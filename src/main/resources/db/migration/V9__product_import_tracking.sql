-- V9: Product catalog import tracking infrastructure for BE08

CREATE SEQUENCE IF NOT EXISTS seq_import_run_number START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS catalog_import_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_code VARCHAR(50) NOT NULL UNIQUE,
    source_filename VARCHAR(255) NOT NULL,
    source_file_checksum VARCHAR(64) NOT NULL,
    mapping_version VARCHAR(50) NOT NULL,
    import_mode VARCHAR(30) NOT NULL,
    operator_identity VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    created_rows INT NOT NULL DEFAULT 0,
    updated_rows INT NOT NULL DEFAULT 0,
    unchanged_rows INT NOT NULL DEFAULT 0,
    rejected_rows INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_summary TEXT,
    CONSTRAINT chk_import_mode CHECK (import_mode IN ('DRY_RUN', 'CREATE_ONLY', 'UPDATE')),
    CONSTRAINT chk_import_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED', 'CONFLICT'))
);

CREATE TABLE IF NOT EXISTS catalog_import_row (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_run_id BIGINT NOT NULL REFERENCES catalog_import_run(id) ON DELETE CASCADE,
    sheet_name VARCHAR(100) NOT NULL,
    row_number INT NOT NULL,
    source_identity VARCHAR(255) NOT NULL,
    target_variant_id BIGINT REFERENCES product_variant(id) ON DELETE SET NULL,
    action_taken VARCHAR(30) NOT NULL,
    previous_version BIGINT,
    resulting_version BIGINT,
    error_message TEXT,
    CONSTRAINT chk_import_action CHECK (action_taken IN ('CREATED', 'UPDATED', 'UNCHANGED', 'REJECTED', 'DRY_RUN_PLAN'))
);

CREATE INDEX IF NOT EXISTS idx_import_row_run_id ON catalog_import_row(import_run_id);
CREATE INDEX IF NOT EXISTS idx_import_run_checksum_mode ON catalog_import_run(source_file_checksum, import_mode);
