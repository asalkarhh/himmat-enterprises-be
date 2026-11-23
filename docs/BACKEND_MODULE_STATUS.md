# Backend Module Implementation Status (BE01 – BE08)

This document records the exact implementation status of all Himmat Enterprises backend modules based on codebase inspection, Flyway migrations, API endpoints, and continuous test verification.

---

## Summary Matrix

| Module | Scope | Status | Migrations | Automated Tests | Test Status |
|---|---|---|---|---|---|
| **BE01** | Foundation, Auth, Roles (`OWNER`, `MANAGER`), Users, Shop Settings | **Complete & Verified** | `V1__init_auth_and_settings.sql` | 15 tests | **Passing** |
| **BE02** | Product Catalog, Variants, Pricing Methods, Inventory Balance 1:1, Master Idempotency | **Complete & Verified** | `V2__catalog_variants_inventory.sql` | 18 tests | **Passing** |
| **BE03** | Customer, Customer Sites, Supplier Masters, Multi-Site Resolution | **Complete & Verified** | `V3__party_masters_and_sites.sql` | 12 tests | **Passing** |
| **BE04A** | Inventory Management, Stock Movements, Manual Adjustments, Transaction Foundation | **Complete & Verified** | `V4__stock_movements_and_inventory.sql` | 11 tests | **Passing** |
| **BE04B** | Supplier Purchases, Inward Goods, Supplier Payments, Returns, Refunds, Supplier Ledger | **Complete & Verified** | `V5__supplier_purchases_and_ledger.sql` | 12 tests | **Passing** |
| **BE05** | Sales Invoices, Immediate Billing, Customer Receipts, Advances, Customer Dues & Ledger | **Complete & Verified** | `V6__customer_invoices_and_ledger.sql` | 12 tests | **Passing** |
| **BE06** | Versioned Bill Revisions, Customer Returns, Refunds, Ledger & Stock Reconciliation | **Complete & Verified** | `V7__invoice_revisions_returns_refunds.sql` | 11 tests | **Passing** |
| **BE07** | Operational Expenses, Dashboard Summaries, Reports, Owner Profit, OpenPDF Documents | **Complete & Verified** | `V8__operational_expenses.sql` | 26 tests | **Passing** |
| **BE08** | Controlled Product Import (CLI), Finolex Mapping, End-to-End Acceptance Scenario | **Complete & Verified** | `V9__product_import_tracking.sql` | 8 tests | **Passing** |

**Total Suite**: **102 tests running on PostgreSQL 16 with 0 failures and 0 errors**.

---

## Detailed Module Breakdown

### BE01: Backend Foundation, Authentication, Security & Settings
* **Package**: `com.asalkartechworks.himmat.identity`, `security`, `settings`, `common`
* **Features**:
  * Spring Security with stateless Bearer JWT token filter (`JwtAuthenticationFilter`).
  * Role-based access control (`ROLE_OWNER`, `ROLE_MANAGER`).
  * Singleton shop settings (`id=1`) with address, GSTIN, and contact details.
  * Standardized envelopes: `ApiResponse<T>`, `PageResponse<T>`, `ErrorResponse`.
  * Global exception handling (`GlobalExceptionHandler`) emitting stable error codes (`VALIDATION_FAILED`, `NOT_FOUND`, `CONFLICT`, etc.) and request IDs.

### BE02: Catalog Hierarchy, Variants & Pricing
* **Package**: `com.asalkartechworks.himmat.catalog`, `pricing`
* **Features**:
  * 4-level catalog hierarchy: `Brand` $\rightarrow$ `Category` $\rightarrow$ `Subcategory` $\rightarrow$ `Product`.
  * Sellable units: `ProductVariant` with size label, unique SKU, pricing method (`MRP_DISCOUNT`, `FIXED`, `COST_PLUS`).
  * Atomic 1:1 `InventoryBalance` creation on variant creation with `quantityOnHand = 0`.
  * `MoneyPolicy` standardizing 2 decimal rounding (`HALF_UP`) and unit rates.

### BE03: Customer, Multi-Site & Supplier Masters
* **Package**: `com.asalkartechworks.himmat.party`
* **Features**:
  * `Customer` master with auto-sequenced code (`CUST-%05d`) and types (`RETAIL`, `CONTRACTOR`, `BUILDER`).
  * `CustomerSite` multi-site tracking for contractors with unique site names per customer.
  * `Supplier` master with code (`SUPP-%05d`), contact, phone, and GSTIN.
  * `CustomerSiteResolver` ensuring sites strictly belong to the target customer.

### BE04A: Inventory Management & Stock Movements
* **Package**: `com.asalkartechworks.himmat.inventory`
* **Features**:
  * Discrete whole-piece quantities (`Integer`); liquid litre box conversions omitted.
  * Non-negative stock enforced at database level (`quantity_on_hand >= 0`).
  * Immutable `StockMovement` ledger with math verification (`quantity_after = quantity_before ± quantity`).
  * Deadlock-free row locking: variant IDs sorted ascending before acquiring locks.
  * Manual stock adjustment endpoint with optimistic version concurrency.

### BE04B: Supplier Purchases, Payments, Returns & Ledger
* **Package**: `com.asalkartechworks.himmat.purchase`
* **Features**:
  * Inward purchase invoices with price basis (`GST_INCLUSIVE`, `GST_EXCLUSIVE`).
  * Supplier payments with split CASH/ONLINE components and invoice allocation.
  * Purchase returns decreasing stock and releasing excess payment allocations.
  * Supplier cash/online refunds consuming unallocated credit.
  * Double-signed immutable supplier ledger: Positive (+) = Shop owes supplier; Negative (-) = Supplier owes shop.

### BE05: Customer Invoicing, Receipts, Advances & Dues
* **Package**: `com.asalkartechworks.himmat.billing`
* **Features**:
  * Immediate POS sales invoice generation without sales orders or quotations.
  * NORMAL and GST invoice display formats with identical payable totals.
  * Customer receipts with split CASH/ONLINE components.
  * Advance credit accumulation and allocation without duplicate ledger entries.
  * Overdue tracking based on original invoice date + 30 days.
  * Immutable customer ledger: Positive (+) = Customer owes shop; Negative (-) = Customer advance credit.

### BE06: Versioned Bill Revisions, Customer Returns & Refunds
* **Package**: `com.asalkartechworks.himmat.billing`
* **Features**:
  * Immutable revision snapshots (`sales_invoice_revision`) preserving original invoice number, date, and due date.
  * Stock delta adjustments: `SALE` on positive delta, `REVERSAL` on negative delta.
  * Automatic release of excess payment allocations back to customer advance credit.
  * Customer returns accepted at any time without arbitrary time limits.
  * Strict historical valuation policy handling (`ORIGINAL_INVOICE_PRICE` vs `LATEST_REVISED_PRICE`).
  * Customer refunds disbursing verifiable unallocated credit via CASH/ONLINE.

### BE07: Expenses, Dashboards, Reports, Owner Profit & PDFs
* **Package**: `com.asalkartechworks.himmat.expense`, `reporting`, `document`
* **Features**:
  * Operational expense register (`EXP-%05d`) with categories and CASH/ONLINE splits.
  * Soft reversals with mandatory reason, preserving audit history without fabricating cash receipts.
  * Executive dashboard summary (`GET /api/v1/dashboard/summary`) with sales, collections, payables, and role-aware profit metrics.
  * Dual-basis sales reporting: `ACTIVITY` (default) vs `DOCUMENT`.
  * Owner-only profit report (`@PreAuthorize("hasAuthority('REPORT:PROFIT') or hasRole('OWNER')")`) calculating gross/net profit from immutable line cost snapshots (`purchaseCostExclTax`), segregating unknown-cost items.
  * Server-side OpenPDF document rendering for 12 business documents.

### BE08: Product Catalog Import & Acceptance Scenario
* **Package**: `com.asalkartechworks.himmat.catalog.imports`, `acceptance`
* **Features**:
  * Controlled, developer-operated CLI runner (`ProductImportRunner`), disabled by default during normal startup (`--app.import.enabled=true`).
  * Configurable mapping for client's Finolex price list (`Finolex_Combined_Price_List_Billing.xlsx`).
  * 3 modes: `DRY_RUN`, `CREATE_ONLY`, and `UPDATE`.
  * Deterministic composite key matching and SKU generation.
  * 1:1 `InventoryBalance` initialization to 0 with `openingConfirmed = false`. Zero stock movements or financial entries.
  * Comprehensive `EndToEndBackendAcceptanceTest` verifying full lifecycle and invariants.
