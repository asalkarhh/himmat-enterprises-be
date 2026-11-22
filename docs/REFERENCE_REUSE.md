# Daddy Soda Reference Adaptation & Technical Reuse Analysis (BE04A)

This document tracks how patterns from the Daddy Soda reference repository (`Daddy_Soda_BE` and `Daddy_Soda_FE`) were evaluated, adapted, or deliberately rejected for Himmat Enterprises.

---

## 1. Backend Adaptations from Daddy Soda

### 1.1 Pessimistic Row-Level Locking
* **Soda Pattern**: Used `findByProductIdWithLock` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` in `InventoryStockRepository` to serialize concurrent adjustments.
* **Himmat Adaptation**:
  * Extended to variant-level multi-row locking in `InventoryBalanceRepository.findAllByVariantIdInOrderByIdAscWithLock`.
  * Enforced **deterministic ascending sorting** of variant IDs before acquiring locks to guarantee zero deadlocks across concurrent multi-item transactions.

### 1.2 Immutable Movement Audit Trail
* **Soda Pattern**: Appended records to `stock_movements` with `quantity_in`, `quantity_out`, and `balance_after`.
* **Himmat Adaptation**:
  * Implemented `stock_movement` with explicit `direction` (`IN`, `OUT`), positive integer `quantity`, `quantity_before`, and `quantity_after`.
  * Added database-level CHECK constraints (`chk_stock_movement_arithmetic`) guaranteeing mathematical consistency at the PostgreSQL storage level.
  * Added `business_event_id` foreign key binding every stock movement directly to an auditable idempotent business event.

---

## 2. Deliberate Deviations from Daddy Soda

| Feature | Daddy Soda Reference | Himmat Enterprises Implementation | Reason for Deviation |
|---|---|---|---|
| **Quantity Unit** | Fractional `BigDecimal` (precision 14, scale 3) for liquid litres | Whole integer pieces (`Integer`) | Hardware and paint products are stocked and sold in discrete whole units. Fractional pieces are physically impossible. |
| **Float Coercion** | Jackson default (coerced `2.5` to `2` silently) | `DeserializationFeature.ACCEPT_FLOAT_AS_INT = false` | Prevents accidental silent rounding of non-integer quantities in API requests. |
| **Inventory Level** | Product-level stock (`InventoryStock` linked to `Product`) | Variant-level stock (`InventoryBalance` linked to `ProductVariant`) | Paint products share brand/formulation but differ in sellable container sizes (1L, 4L, 10L, 20L). Each variant has separate stock. |
| **Balance Creation** | Lazy on-demand creation in `increaseStock()` | Eager 1:1 atomic creation on variant creation | Prevents integrity holes during sales or deductions. Missing balance is treated as a severe system integrity error (`INVENTORY_BALANCE_MISSING`). |
| **Identifier Types** | UUID keys (`UUID.randomUUID()`) | BIGINT identity sequences (`GENERATED ALWAYS AS IDENTITY`) | Better B-tree index locality and lower storage overhead in PostgreSQL. Serialized as strings in JSON to prevent browser IEEE 754 precision loss. |
| **Multi-Tenancy** | Franchise, Outlet, Machine, Warehouse | Single shop, single stock location | Himmat Enterprises is an independent single-location enterprise. Multi-outlet abstractions add unnecessary complexity. |
| **API Envelope** | `{ success: true, data: ..., error: ... }` | Separate `{ data: ..., requestId: ... }` vs `{ error: ..., requestId: ... }` | Aligns with Himmat's approved version 1.1 API contract. |
| **Purchase Cost Basis** | Hardcoded/derived from sale price | Explicit basis (`GST_INCLUSIVE` or `GST_EXCLUSIVE`) with category default discounts | Accurately calculates GST and net purchase costs without guessing or deriving from selling price. |
| **Purchase Rounding** | Whole-rupee rounding | `MoneyPolicy` standard 2 decimals (HALF_UP) | Whole-rupee rounding causes accounting discrepancies on large commercial invoices. |
| **Supplier Ledger** | Non-existent or implicit invoice tracking | Immutable double-signed `supplier_ledger_entry` with running balance | Complete auditability of payables, payments, returns, and refunds. Positive = shop owes supplier; Negative = supplier owes shop. |
