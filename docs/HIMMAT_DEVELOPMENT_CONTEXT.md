# Himmat Enterprises: Development Context & System Architecture

## 1. Project Overview & Technology Stack

Himmat Enterprises is a single-location paint and hardware shop in India.
The system is built as a maintainable, resilient Spring Boot modular monolith designed for transactional integrity, strict auditability, and concurrency safety.

* **Language**: Java 21 LTS
* **Framework**: Spring Boot 3.3.5
* **Persistence & ORM**: Spring Data JPA / Hibernate 6.5.3
* **Database**: PostgreSQL 16 with Flyway migrations
* **Security**: Spring Security with stateless JWT Bearer authentication
* **API Conventions**: RESTful with custom envelopes (`ApiResponse`, `PageResponse`, `ErrorResponse`), zero-based pagination, and BIGINT IDs serialized as JSON strings to avoid 64-bit integer truncation in browsers.
* **Timezone & Auditing**: All persistent timestamps stored in UTC (`TIMESTAMPTZ`); business date filters interpreted in `Asia/Kolkata` (`BusinessClock.BUSINESS_ZONE`).

---

## 2. Completed Modules Status

| Module | Scope | Status | Migrations |
|---|---|---|---|
| **BE01** | Foundation, Auth, Roles (`OWNER`, `MANAGER`), Users, Shop Settings | **Complete & Verified** | `V1__init_auth_and_settings.sql` |
| **BE02** | Product Catalog, Variants, Pricing Methods, Inventory Balance 1:1, Master Idempotency | **Complete & Verified** | `V2__catalog_variants_inventory.sql` |
| **BE03** | Customer, Customer Sites, Supplier Masters, Site Resolution | **Complete & Verified** | `V3__party_masters_and_sites.sql` |
| **BE04A** | Inventory Management, Stock Movements, Manual Adjustments, Shared Transaction Foundation | **Complete & Verified** | `V4__stock_movements_and_inventory.sql` |
| **BE04B** | Supplier Purchases, Inward Goods, Supplier Payments, Purchase Returns | *Pending* | Planned next |
| **BE05** | Sales Invoices, Immediate Billing, Customer Payments | *Pending* | Planned |
| **BE06** | Bill Revisions, Customer Returns, Refunds | *Pending* | Planned |

---

## 3. Database Schema & Flyway Migrations

* **`V1__init_auth_and_settings.sql`**:
  * `app_user`: System users with hashed passwords, active status, roles.
  * `shop_settings`: Singleton configuration (`id=1`).
  * `business_event`: Durable audit ledger and idempotency record (`request_key`, `request_hash`, `audit_payload`).
* **`V2__catalog_variants_inventory.sql`**:
  * `brand`, `category`, `subcategory`, `product`: Catalog hierarchy.
  * `product_variant`: Sellable unit (SKU, size label, pricing method, MRP, cost, tax).
  * `inventory_balance`: 1-to-1 table with variant (`quantity_on_hand`, `opening_confirmed`, `version`), enforcing `CHECK (quantity_on_hand >= 0)`.
* **`V3__party_masters_and_sites.sql`**:
  * `customer`: Customer details, code sequence (`CUST-%05d`), customer type (`RETAIL`, `CONTRACTOR`, `BUILDER`).
  * `customer_site`: Multi-site tracking for contractors/builders. Unique name per customer.
  * `supplier`: Vendor details, code sequence (`SUPP-%05d`).
* **`V4__stock_movements_and_inventory.sql`**:
  * `stock_movement`: Immutable audit ledger for every piece of stock movement (`variant_id`, `business_event_id`, `movement_type`, `direction`, `quantity`, `quantity_before`, `quantity_after`, `source_entity_type`, `source_entity_id`, `source_effect_key`, `reason`, `actor_id`).
  * Database check constraints:
    * `chk_stock_movement_direction`: `IN` or `OUT`.
    * `chk_stock_movement_quantity`: `quantity > 0`.
    * `chk_stock_movement_arithmetic`: Math verification ensuring `quantity_after = quantity_before ± quantity`.
  * Unique index `uq_stock_movement_source_effect` preventing duplicate processing of the same source effect.

---

## 4. Concurrency, Locking & Transaction Boundaries

### 4.1 Deadlock-Free Lock Hierarchy
To ensure zero deadlocks across complex transactions that touch multiple resources, all services across all modules MUST acquire locks in the following strict order:
1. **Party Balances**: Customer or Supplier accounts (sorted by Party ID ascending).
2. **Business Documents**: Document headers and lines (Purchase invoice, Sales bill).
3. **Inventory Balances**: `inventory_balance` rows (sorted strictly by `variant_id` ascending).
4. **Appends**: Insert `stock_movement`, ledger entries, and audit logs.

### 4.2 Row-Level Locking in `StockPostingService`
Multi-variant operations extract distinct variant IDs, sort them in ascending order (`sortedVariantIds = variantIds.stream().distinct().sorted().toList()`), and execute:
```sql
SELECT ib FROM InventoryBalance ib
JOIN FETCH ib.variant v
JOIN FETCH v.product p
WHERE ib.variantId IN :variantIds
ORDER BY ib.variantId ASC
FOR UPDATE
```
Because every transaction in the application locks variant rows in ascending order, circular lock wait conditions are mathematically eliminated.

### 4.3 Running Balance & Sequential Validation
Within `StockPostingService`:
* Balances are updated sequentially against the locked in-memory running balance.
* If any line causes stock to drop below 0, an `InsufficientStockException` is thrown immediately.
* Netting across lines is strictly prohibited from masking an invalid intermediate negative state.
* If any line fails or any exception is thrown, the entire database transaction rolls back, committing zero balance changes and zero movement records.

---

## 5. Idempotency & Transaction Architecture

Himmat Enterprises uses durable PostgreSQL transaction-scoped advisory locks combined with the `business_event` table via `BusinessTransactionExecutor`:
1. Client submits `Idempotency-Key: <UUID>` header.
2. `BusinessTransactionExecutor` acquires a transaction-scoped advisory lock: `SELECT pg_advisory_xact_lock(hashtext(:sanitizedKey))`.
3. If an existing `business_event` with the same `request_key` exists:
   * Hashes match: Exactly replays the committed response payload without re-executing logic or checking stale version.
   * Hashes differ: Throws `409 Conflict` (`IDEMPOTENCY_CONFLICT`).
4. If not found:
   * Inserts `business_event` with `audit_payload = "{}"` within the current transaction.
   * Calls the transactional lambda (passing the saved `business_event.id`).
   * Services perform atomic document updates, row locks, balance modifications, and `stock_movement` appends referencing `business_event_id`.
   * Serializes the result DTO into `business_event.audit_payload`.
   * Commits the entire transaction atomically. If an exception occurs, everything rolls back.

---

## 6. Integration Contract for Future Modules (BE04B, BE05, BE06)

Future modules implementing supplier purchases, customer billing, and returns must integrate with the BE04A stock foundation as follows:

### 6.1 Transaction Participation
* Must use `@Transactional(propagation = Propagation.REQUIRED)`.
* Calls `stockPostingService.postMovements(StockPostingRequest)` within that transaction (`Propagation.MANDATORY` enforces this).

### 6.2 Movement Types & Direction
* **BE04B (Supplier Purchases & Returns)**:
  * Inward goods receipt: `StockMovementType.PURCHASE_RECEIPT`, `direction = IN`.
  * Return to supplier: `StockMovementType.PURCHASE_RETURN`, `direction = OUT`.
  * Source entity type: `"PURCHASE_INVOICE"`.
* **BE05 (Immediate Billing & Sales)**:
  * Retail / contractor sale: `StockMovementType.SALE`, `direction = OUT`.
  * Source entity type: `"SALES_INVOICE"`.
* **BE06 (Bill Revisions, Customer Returns & Refunds)**:
  * Customer return: `StockMovementType.CUSTOMER_RETURN`, `direction = IN`.
  * Item replacement / exchange / correction: `StockMovementType.REVERSAL` or `SALE` / `CUSTOMER_RETURN`.
  * Source entity type: `"RETURN_ORDER"` or `"SALES_INVOICE_REVISION"`.

### 6.3 Historical Variant Resolution
* For new sales and new purchase orders: `allowInactiveVariants = false`. Discontinued or inactive variants cannot be purchased or sold.
* For returns, revisions, or reversals of past invoices: set `allowInactiveVariants = true`. This allows returning a discontinued item that was legitimately purchased in the past without allowing it to be selected for new sales.

### 6.4 Negative Stock Invariant
* Stock deductions in BE04B, BE05, and BE06 will throw `INSUFFICIENT_STOCK` (422) if requested deduction exceeds locked available quantity on hand.
* Reversals or returns do not bypass this rule. Negative stock is strictly prohibited across all roles.
