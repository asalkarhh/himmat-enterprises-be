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
| **BE04B** | Supplier Purchases, Inward Goods, Supplier Payments, Purchase Returns, Supplier Refunds, Ledger | **Complete & Verified** | `V5__supplier_purchases_and_ledger.sql` |
| **BE05** | Sales Invoices, Immediate Billing, Customer Receipts, Advances, Dues, Customer Ledger | **Complete & Verified** | `V6__customer_invoices_and_ledger.sql` |
| **BE06** | Bill Revisions, Customer Returns, Refunds | **Complete & Verified** | `V7__invoice_revisions_returns_refunds.sql` |
| **BE07** | Operational Expenses, Dashboard, Business Reports, Owner Profit, Printable PDFs | **Complete & Verified** | `V8__operational_expenses.sql` |
| **BE08** | Counter Register, Shift Management, End-of-Day Closures | *Pending* | Planned |

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
* **`V5__supplier_purchases_and_ledger.sql`**:
  * `purchase`: Purchase invoice header (`PO-%05d`, supplier snapshots, subtotal, discount, GST, total payable, allocated amount, version, audit).
  * `purchase_line`: Purchase lines (variant, line index, product snapshots, whole piece qty, price basis, unit price, discount %, GST %, net/gross cost, total, update product cost flag).
  * `supplier_payment`: Standalone or immediate supplier payments (`SPAY-%05d`, date, total amount, unallocated credit, audit).
  * `payment_component`: Cash/Online split for payments and refunds.
  * `payment_allocation`: Payment-to-purchase allocation mapping.
  * `purchase_return`: Purchase returns to supplier (`PR-%05d`, return date, return amount, reason, audit).
  * `purchase_return_line`: Return lines referencing original purchase lines, returned quantity, unit cost, and line total.
  * `supplier_refund`: Cash/bank refunds from supplier to shop (`SRF-%05d`, date, amount, reason, audit).
  * `supplier_ledger_entry`: Immutable supplier financial ledger with running balances.

---

## 4. Concurrency, Locking & Transaction Boundaries

### 4.1 Deadlock-Free Lock Hierarchy
To ensure zero deadlocks across complex transactions that touch multiple resources, all services across all modules MUST acquire locks in the following strict order:
1. **Party Balances**: Customer or Supplier accounts (e.g. `supplierRepository.findByIdWithLock(supplierId)`).
2. **Business Documents**: Document headers and lines (Purchase invoices, Payment allocations, Purchase returns).
3. **Inventory Balances**: `inventory_balance` rows (sorted strictly by `variant_id` ascending via `StockPostingService`).
4. **Appends**: Insert `supplier_ledger_entry`, `stock_movement`, and audit records.

---

## 5. Supplier Financial Model & Ledger Sign Convention

The supplier ledger uses an explicit, unambiguous signed financial model:
* **Positive (+)** = Shop owes supplier (Payable / Liability)
* **Negative (-)** = Supplier owes shop or holds shop advance (Credit / Asset)

### Canonical Workflow:
1. **Purchase ₹1,000**:
   * Stock increases atomically (+qty).
   * Ledger entry: `sourceType = PURCHASE`, `amount = +1000.00`.
   * Running balance: `+1000.00` (Shop owes ₹1,000).
2. **Payment ₹600**:
   * Cash/Online split recorded. Allocated to purchase.
   * Ledger entry: `sourceType = PAYMENT`, `amount = -600.00`.
   * Running balance: `+400.00` (Shop owes ₹400).
3. **Purchase Return ₹500**:
   * Stock decreases atomically (-qty).
   * Cumulative return checked against original line quantity.
   * Net remaining payable is 1000 - 500 = 500. Excess payment allocation (600 - 500 = 100) is released back to payment's `unallocatedAmount`!
   * Ledger entry: `sourceType = PURCHASE_RETURN`, `amount = -500.00`.
   * Running balance: `400 - 500 = -100.00` (Supplier owes shop ₹100 or holds shop advance).
4. **Supplier Refund IN ₹100**:
   * Supplier returns ₹100 in cash/bank to the shop.
   * Consumes available supplier credit without touching inventory.
   * Ledger entry: `sourceType = REFUND`, `amount = +100.00`.
   * Running balance: `-100 + 100 = 0.00` (Account fully settled).

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

---

## 7. Customer Billing, Advances & Customer Ledger Sign Convention (BE05)

The customer ledger uses an explicit, unambiguous signed financial model:
* **Positive (+)** = Customer owes shop (Receivable / Debit / Charge)
* **Negative (-)** = Shop owes customer or customer holds advance (Receipt / Credit / Advance)

### Canonical Workflow:
1. **Sales Invoice ₹1,900**:
   * Immediate counter bill created without prerequisite sales order or quotation.
   * Authoritative pricing re-evaluated via `PricingService` with GST-inclusive pricing.
   * NORMAL and GST display formats produce identical payable totals.
   * Stock decreases atomically (-qty, `StockMovementType.SALE`, `direction = OUT`, `sourceEntityType = "SALES_INVOICE"`).
   * Ledger entry: `sourceType = INVOICE`, `amount = +1900.00`.
   * Running balance: `+1900.00` (Customer owes shop ₹1,900).
   * Due date set to `invoiceDate + 30 calendar days`.

2. **Customer Receipt ₹2,000 (with ₹1,900 allocated, ₹100 advance)**:
   * Multi-row CASH and ONLINE payment split recorded.
   * ₹1,900 allocated to invoice (Invoice status becomes `PAID`, `outstanding = 0.00`).
   * ₹100 unallocated amount retained as customer advance credit.
   * Ledger entry: `sourceType = RECEIPT`, `amount = -2000.00`.
   * Running balance: `1900 - 2000 = -100.00` (Customer holds ₹100 advance credit with the shop).

3. **Subsequent Credit Bill with Advance Application**:
   * Customer purchases ₹500 on credit.
   * Applies ₹100 advance credit:
     - Deducts ₹100 from unallocated receipt credit (receipt credit becomes ₹0.00).
     - Adds ₹100 to invoice allocated amount (invoice settlement status becomes `PARTIALLY_PAID`, `outstanding = 400.00`).
     - **CRITICAL**: Internal allocation between receipt advance and invoice document does NOT change net money owed, so no redundant ledger entry is created.
   * Invoice ledger entry: `sourceType = INVOICE`, `amount = +500.00`.
   * Running balance: `-100 + 500 = +400.00` (Customer owes shop ₹400).

4. **Aging & Overdue Tracking**:
   * An invoice is overdue when `status == POSTED`, `outstandingAmount > 0`, and `today > dueDate` (`dueDate = invoiceDate + 30 days`).
   * `daysOverdue = ChronoUnit.DAYS.between(dueDate, today)`.
   * Dues search and dues summary (`GET /api/v1/dues` and `GET /api/v1/dues/summary`) compute totals across all matching invoices.

---

## 8. Versioned Bill Revisions, Customer Returns & Refunds (BE06)

BE06 establishes authoritative versioned corrections, customer returns, customer refunds, and comprehensive ledger/stock/allocation reconciliation.

### 8.1 Database Extensions (Flyway V7)
* `sales_invoice`: Added `original_payable_amount` (backfilled, NOT NULL), `total_returned_amount` (DEFAULT 0.00), `revision_count` (DEFAULT 0).
* `sales_invoice_line`: Added `returned_quantity` (DEFAULT 0), `is_active_in_current_revision` (DEFAULT TRUE), and check constraint `chk_sales_line_returned CHECK (returned_quantity >= 0 AND returned_quantity <= quantity)`.
* `sales_invoice_revision`: Versioned snapshot header tracking previous vs new totals, delta payable, released allocations, and full JSON payload snapshot.
* `sales_invoice_revision_line`: Line-level revision audit detail tracking previous vs new quantities, net prices, line totals, deltas, and change types (`UNCHANGED`, `MODIFIED`, `ADDED`, `REMOVED`).
* `customer_return` & `customer_return_line`: Return records tracking returned quantities, unit price, valuation basis, and tax breakdowns. Numbered sequentially (`CR-%05d`).
* `customer_refund`, `customer_refund_component`, `customer_refund_source`: Disbursement of unallocated customer advance credit via CASH or ONLINE payment methods, tracing back to source customer receipts. Numbered sequentially (`CRF-%05d`).

### 8.2 Invoice Revisions
* **Identity Preservation**: Original `invoiceNumber`, `invoiceDate`, and `dueDate` (`invoiceDate + 30 days`) are strictly preserved.
* **Customer Immutability**: Cross-customer invoice reassignment is prohibited.
* **Stock Delta Effects**:
  * Positive delta ($newQty > oldQty$): Deducts additional stock (`StockMovementType.SALE`, `direction = OUT`, variant must be active).
  * Negative delta ($newQty < oldQty$): Restores stock to inventory (`StockMovementType.REVERSAL`, `direction = IN`, allows historical/deactivated variants).
  * Zero delta (price-only correction): 0 stock movements.
  * Line quantity cannot be reduced below already returned items ($newQty \ge returnedQuantity$).
* **Financial & Excess Allocation Release**:
  * Effective payable: $effectivePayable = totalPayableAmount - totalReturnedAmount$.
  * If revision reduces payable below already allocated payments, excess allocation is released back to `CustomerReceipt.unallocatedAmount` in reverse order (`id DESC`).
  * Ledger entry posted with signed amount = `deltaPayableAmount`.

### 8.3 Customer Returns & Valuation Policy
* **Age Invariance**: Returns are accepted legitimately after a month or longer without arbitrary time limits.
* **Non-Negative Invariant**: Returned quantity cannot exceed remaining returnable quantity ($quantity - returnedQuantity$).
* **Stock Inward**: Stock is restored to inventory (`StockMovementType.CUSTOMER_RETURN`, `direction = IN`, `allowInactiveVariants = true`).
* **Valuation Basis Rules**:
  * *Unambiguous (Not Price-Revised)*: Return valued at original line price: `valuationBasis = "ORIGINAL_INVOICE_PRICE"`, unit price = `line.unitNetPrice`.
  * *Ambiguous (Historical Price Revision)*: If a line underwent price revisions, returns without explicit policy fail with `422 RETURN_VALUATION_POLICY_REQUIRED`. When provided with `"ORIGINAL_INVOICE_PRICE"` or `"LATEST_REVISED_PRICE"`, unit price is snapshotted accordingly.
  * Catalog prices are NEVER used to value historical customer returns.
* **Customer Ledger**: Posted as `sourceType = CUSTOMER_RETURN`, `amount = -totalReturnAmount` (credit to customer).
* **Allocation Release**: Excess allocations released back to receipt credit if return reduces effective payable below allocated amount.

### 8.4 Customer Refunds
* **Advance Credit Payout**: Disburses unallocated customer advance credit via CASH, ONLINE, or mixed split.
* **Zero Stock Impact**: Refunds are purely monetary disbursements and do not touch stock.
* **Source Tracking**: Deducts from unallocated customer receipts and logs `CustomerRefundSource` records.
* **Customer Ledger**: Posted as `sourceType = REFUND`, `amount = +totalAmount` (increases running balance towards 0.00).

### 8.5 Ledger Reconciliation Example
$$\begin{array}{lcccc}
\textbf{Transaction} & \textbf{Type} & \textbf{Amount} & \textbf{Running Balance} & \textbf{Effective Shop Due} \\
\hline
\text{Initial Bill} & \text{INVOICE} & +1,000.00 & +1,000.00 & \text{Customer owes ₹1,000} \\
\text{Payment} & \text{RECEIPT} & -600.00 & +400.00 & \text{Customer owes ₹400} \\
\text{Return} & \text{CUSTOMER\_RETURN} & -500.00 & -100.00 & \text{Shop holds ₹100 credit} \\
\text{Disbursement} & \text{REFUND} & +100.00 & 0.00 & \text{Reconciled to ₹0.00}
\end{array}$$

---

## 9. Operational Expenses, Dashboards, Business Reports, Owner Profit & PDF Documents (BE07)

BE07 establishes the operational expense register, business intelligence dashboard, operational reports, Owner-only profit analysis, and printable PDF document generation across the entire enterprise.

### 9.1 Database Schema (Flyway V8)
* `seq_expense_number`: Independent BIGINT sequence for sequential expense identifiers (`EXP-%05d`).
* `operational_expense`: Master operational expense record:
  * Number sequence (`EXP-%05d`), date, category (`RENT`, `UTILITIES`, `SALARY`, `TRANSPORT`, `SHOP_OPERATIONS`, `OTHER`), total amount.
  * Status (`ACTIVE`, `REVERSED`), reversal metadata (`reversed_at`, `reversed_by`, `reversal_reason`).
  * Concurrency and audit tracking (`version`, `created_at`, `created_by`, `updated_at`, `updated_by`).
  * Check constraints: `chk_expense_amount (amount > 0)`, `chk_expense_status (status IN ('ACTIVE', 'REVERSED'))`.
* `operational_expense_component`: Split payment modes for each expense (`CASH`, `ONLINE` with reference):
  * Check constraints: `chk_expense_comp_amount (amount > 0)`, `chk_expense_comp_method (method IN ('CASH', 'ONLINE'))`.
  * Index: `idx_expense_component_expense_id`.

### 9.2 Operational Expenses & Soft Bookkeeping Reversals
* **Registration**: Expenses support multi-row cash and online payment splits. Component sum must strictly equal the expense amount.
* **Soft Reversals**: Expenses can be reversed for bookkeeping corrections with an explicit reason.
  * Reversals update status to `REVERSED` with an audit timestamp and actor ID.
  * Reversing an expense **does NOT fabricate a fake cash receipt**.
  * Reversal immediately excludes the expense from period operating totals, profit deductions, and cash movement outflows.
  * Concurrency guard: Reversals acquire pessimistic write locks (`findByIdWithLock`) and verify optimistic version numbers.

### 9.3 Dashboard Summary (`GET /api/v1/dashboard/summary`)
Provides real-time aggregated KPIs across any specified date range (defaulting to current calendar month in `Asia/Kolkata`):
* **Sales Activity**: Gross sales, net revision delta, customer returns credit, net sales, invoice count.
* **Collections**: Customer receipts, customer refunds, net collections, receipt count.
* **Receivables & Dues**: Total customer outstanding, overdue receivables (past 30 days), count of customers with active dues.
* **Purchases & Payables**: Period purchases, purchase returns, net purchases, supplier payments, supplier refunds, total supplier payables outstanding, count of suppliers with dues.
* **Operating Expenses**: Active period expenses and expense count.
* **Inventory Health**: Total catalog variants, zero-stock variant count, low-stock variant count (quantity $\le$ threshold).
* **Owner-Only Profit Metrics**: Net revenue, COGS, gross profit, gross margin %, operating expenses, net profit, coverage %, and unknown-cost sales. **Strictly omitted for non-Owner users**.

### 9.4 Business & Operational Reports
* **Sales Report (`GET /api/v1/reports/sales`)**:
  * `ACTIVITY` basis (default): Aggregates sales invoices, revision deltas, and returns occurring strictly within the date window. Matches real economic activity.
  * `DOCUMENT` basis: Aggregates documents whose original document date falls within the window.
  * Supports filtering by `customerId`, tax breakdown (taxable vs GST), and line-item details.
* **Purchase Report (`GET /api/v1/reports/purchases`)**:
  * Inward purchases, purchase returns, supplier payments, and supplier refunds within period.
  * Computes net inventory additions and net cash outflows to suppliers.
* **Inventory Valuation Report (`GET /api/v1/reports/inventory`)**:
  * Stock on hand, inventory valuation at cost, inventory valuation at selling price, and reorder alerts.
  * Filters for `zeroStockOnly`, `lowStockOnly`, `brandId`, and `categoryId`.
* **Cash Movement Report (`GET /api/v1/reports/cash-movements`)**:
  * Real money flows only: Receipts IN (+), Supplier Refunds IN (+), Supplier Payments OUT (-), Customer Refunds OUT (-), Active Expenses OUT (-).
  * Breaks down every movement by `CASH` vs `ONLINE`.

### 9.5 Owner Profit & Product Margins (`GET /api/v1/reports/profit`)
* **Strict Role Security**: Enforced via `@PreAuthorize("hasAuthority('REPORT:PROFIT') or hasRole('OWNER')")`. Non-owner users receive `403 Forbidden`.
* **Historical Cost Snapshot Integrity**:
  * COGS is computed **strictly from the immutable line purchase cost snapshot** (`purchaseCostExclTax`) captured at the moment of invoice creation.
  * Current catalog purchase costs or updated supplier prices are **never used** to rewrite historical profit margins.
* **Unknown Cost Handling**:
  * Lines with unknown or null historical cost are segregated into `salesValueExcludedUnknownCost`.
  * Computes `costCoveragePct` ($knownCostSales / totalSales \times 100$) so the Owner can assess margin confidence.
* **Net Profit**: Computed as $knownGrossProfit - totalOperatingExpenses$.

### 9.6 Printable PDF Documents (`/api/v1/documents/**/pdf`)
All PDF generation is performed server-side using OpenPDF 2.0.3 with consistent layout, enterprise typography, page numbers, and persisted shop settings:
1. `GET /api/v1/documents/invoices/{id}/pdf?format=NORMAL`: Clean POS thermal/A4 invoice without GST breakdown.
2. `GET /api/v1/documents/invoices/{id}/pdf?format=GST`: Tax invoice with GSTIN, HSN/SAC codes, CGST, SGST, IGST breakdown.
3. `GET /api/v1/documents/invoices/{id}/revisions/{revId}/pdf`: Versioned invoice revision statement with line changes.
4. `GET /api/v1/documents/customer-receipts/{id}/pdf`: Customer payment receipt voucher.
5. `GET /api/v1/documents/customer-returns/{id}/pdf`: Customer credit note / return voucher.
6. `GET /api/v1/documents/customer-refunds/{id}/pdf`: Customer refund disbursement voucher.
7. `GET /api/v1/documents/customers/{id}/ledger/pdf`: Customer statement of accounts with running balance.
8. `GET /api/v1/documents/purchases/{id}/pdf`: Supplier purchase order / inward invoice record.
9. `GET /api/v1/documents/purchase-returns/{id}/pdf`: Supplier debit note / purchase return voucher.
10. `GET /api/v1/documents/supplier-payments/{id}/pdf`: Supplier payment advice voucher.
11. `GET /api/v1/documents/suppliers/{id}/ledger/pdf`: Supplier statement of accounts with running balance.
12. `GET /api/v1/documents/expenses/{id}/pdf`: Operational expense payment voucher.

---

## 10. Integration Contract for BE08 (Counter Register & Shift Closures)

BE08 will build on the completed financial and cash movement architecture:
1. Counter opening float and shift registration.
2. Real-time cash drawer tracking (cash sales, receipts, payments, refunds, expenses).
3. End-of-day physical cash count reconciliation and shift closure reports.
4. Cash discrepancy auditing (shortages / overages).
