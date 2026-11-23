# Backend Acceptance Checklist: End-to-End Verification (BE08)

This document provides the exhaustive acceptance verification matrix for the Himmat Enterprises backend, covering business workflows, financial reconciliation, inventory safety, and concurrency guarantees across all modules (BE01 through BE08).

All checks are automated and continuously verified against PostgreSQL 16 via `EndToEndBackendAcceptanceTest.java` and module test suites.

---

## 1. End-to-End Scenario Verification Matrix

| Step | Scenario Description | Expected Outcome | Automated Check | Actual Result | Status |
|---|---|---|---|---|---|
| **A** | **Foundation** | Bootstrap test Owner, create Manager, initialize singleton Shop Settings. | Owner & Manager tokens generated; shop settings singleton `id=1` persisted. | User and settings verification in `setUp()`. | **Verified** |
| **B** | **Catalog & Import** | Import synthetic MRP product with no cost/GST. Initialize zero stock. Create second variant with known cost/tax for margins. | Variant created with `MRP_DISCOUNT`, `mrpInclTax = 500.00`, cost `null`, GST `null`. 1:1 `InventoryBalance` created with `quantityOnHand = 0`, `openingConfirmed = false`. | `testEndToEndBackendAcceptanceScenario` Step B assertions. | **Verified** |
| **C** | **Parties & Sites** | Create contractor customer with 2 sites (Site A, Site B). Create 1 supplier. | Unique codes (`CUST-E2E01`, `SUP-E2E01`). Both sites linked to same customer. | Customer, site, and supplier repo assertions. | **Verified** |
| **D** | **Inward Purchase** | Purchase 50 units of known cost item + 20 units of imported item. Record partial CASH/ONLINE supplier payment (₹10,000). | Stock increases atomically (50, 20). Supplier ledger entry posted (+payable). Payment allocated. Outstanding matches `totalPayable - 10000.00`. | `POST /api/v1/purchases`, `POST /api/v1/suppliers/{id}/payments`, `GET /api/v1/suppliers/{id}/account-summary`. | **Verified** |
| **E** | **Immediate Billing (Site A)** | Direct POS invoice for Site A (10 units @ ₹300 = ₹3,000). Record partial mixed customer receipt (₹2,000). | Stock deducted (50 $\rightarrow$ 40). No sales order needed. Customer ledger entry posted (+₹3,000). Receipt allocated (-₹2,000). Customer balance = ₹1,000. | `POST /api/v1/invoices`, `POST /api/v1/customers/{id}/receipts`, `GET /api/v1/customers/{id}/account`. | **Verified** |
| **F** | **Other Site Billing (Site B)** | Separate invoice for Site B (5 units @ ₹500 = ₹2,500). Customer funds remain shared. | Stock deducted (20 $\rightarrow$ 15). One site per invoice. Customer total balance = ₹1,000 + ₹2,500 = ₹3,500. No separate site wallets. | `POST /api/v1/invoices`, customer balance assertion. | **Verified** |
| **G** | **Invoice Revision** | Unambiguous revision on Invoice A adding 2 units (10 $\rightarrow$ 12 units). | Stock deducted by delta 2 units (40 $\rightarrow$ 38). Delta payable = +₹600. Original `invoiceDate` and `dueDate` strictly preserved. | `POST /api/v1/invoices/{id}/revisions`, stock and date assertions. | **Verified** |
| **H** | **Customer Return** | Return 2 units from Invoice A. | Stock restored (38 $\rightarrow$ 40). Line return count updated. Invoice effective payable reduced. Customer credit posted. | `POST /api/v1/invoices/{id}/returns`, stock and returned quantity check. | **Verified** |
| **I** | **Supplier Return & Refund** | Return 5 units to supplier against purchase. Record ₹500 supplier refund. | Stock deducted (40 $\rightarrow$ 35). Purchase payable reduced. Supplier refund consumed without touching inventory. | `POST /api/v1/purchases/{id}/returns`, `POST /api/v1/suppliers/{id}/refunds`. | **Verified** |
| **J** | **Customer Advance Refund** | Customer overpays dues generating unallocated advance credit; disburse ₹200 cash refund. | Advance credit disbursed. No double-consumption of funds. Customer ledger updated (+₹200). | `POST /api/v1/customers/{id}/receipts`, `POST /api/v1/customers/{id}/refunds`. | **Verified** |
| **K** | **Reporting & Profit Scope** | Verify Dashboard summary, Cash movements report, and Owner profit report. | Dashboard aggregates sales, collections, payables. Cash report captures exact cash/online movements. Owner gets profit report; Manager gets **403 Forbidden**. Unknown-cost sales segregated and coverage % reported. | `GET /api/v1/dashboard/summary`, `GET /api/v1/reports/cash-movements`, `GET /api/v1/reports/profit`. | **Verified** |
| **L** | **Printable Documents** | Request PDFs for Invoices (Normal & GST), Revisions, Receipts, Returns, Refunds, Purchases, and Ledgers. | All endpoints return HTTP 200 OK with `Content-Type: application/pdf` and valid `%PDF-1.` magic header bytes. | `GET /api/v1/**/pdf` requests across 9 document types. | **Verified** |

---

## 2. Critical Invariant & Failure Checks

### 2.1 Inventory Invariants
* **1:1 Balance Constraint**: Every variant has exactly one `inventory_balance` row. Verified via database foreign key and primary key sharing.
* **Non-Negative Stock**: Database check constraint `chk_inventory_balance_qty (quantity_on_hand >= 0)` actively blocks negative stock. Attempting to oversell fails with `422 INSUFFICIENT_STOCK`.
* **Mathematical Movement Balance**: Database constraint `chk_stock_movement_arithmetic` enforces `quantity_after = quantity_before ± quantity`.

### 2.2 Financial & Ledger Invariants
* **No Unbacked Allocations**: Total allocations cannot exceed payment amount or invoice effective payable.
* **No Cross-Customer Contamination**: Allocations and refunds strictly validate party ownership. Applying funds across customers is rejected with `422 UNPROCESSABLE_ENTITY`.
* **Zero Double Consumption**: Customer refund verifies available unallocated credit before disbursement.
* **Ledger Balance Formula**: $RunningBalance = TotalCharges - TotalCredits$. Reconciled continuously on every append.

### 2.3 Concurrency & Idempotency Invariants
* **Deadlock-Free Row-Locking**: Multi-variant operations sort variant IDs ascending before locking (`StockPostingService`).
* **Optimistic Version Guard**: Submitting a stale version for invoice revisions or manual adjustments immediately throws `409 CONFLICT`.
* **Durable Idempotency**: Submitting duplicate `Idempotency-Key` headers replays the exact committed business response without duplicate side effects.
* **Import Concurrency**: Conflicting variant prices in `CREATE_ONLY` mode halt import with status `CONFLICT` without overwriting existing data.
