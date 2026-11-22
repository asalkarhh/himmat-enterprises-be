# Himmat Enterprises - Backend Application

A resilient, transaction-safe backend modular monolith for Himmat Enterprises (paint and hardware retail shop).

## 1. Modules Status

* **BE01: Foundation, Authentication & Security** (Complete)
  * Spring Security with JWT Bearer tokens.
  * Role-based access control (`OWNER`, `MANAGER`).
  * Shop settings singleton management.
* **BE02: Product Catalog & Pricing** (Complete)
  * Brand, Category, Subcategory, Product, and sellable Product Variants.
  * 1-to-1 inventory balance initialization.
  * Pricing calculation engine (`MRP_DISCOUNT`, `COST_PLUS`, `FIXED`).
  * Decimal money rounding policies (`MoneyPolicy`).
* **BE03: Customer & Supplier Masters** (Complete)
  * Customer master management with type classification (`RETAIL`, `CONTRACTOR`, `BUILDER`).
  * Multi-site tracking for contractors and builders (`customer_site`).
  * Supplier master management.
* **BE04A: Inventory Management & Stock Movements** (Complete)
  * Single shop, whole integer piece quantities (`Integer`).
  * Non-negative stock enforced at service and database levels (`CHECK quantity_on_hand >= 0`).
  * Immutable `stock_movement` audit history with mathematical verification constraints.
  * Deadlock-free row-level locking (sorted ascending variant IDs).
  * Manual stock adjustment endpoint (`POST /api/v1/inventory/adjustments`) with optimistic versioning.
  * Durable idempotency via `BusinessTransactionExecutor` and advisory locks.
  * Read endpoints for inventory balances and movement ledgers with Asia/Kolkata date range filtering.
* **BE04B: Supplier Purchases, Payments, Returns & Ledger** (Complete)
  * Inward purchase invoices with integer piece quantities and pricing basis (`GST_INCLUSIVE`, `GST_EXCLUSIVE`).
  * Category discount % defaulting and opt-in variant cost update (`updateProductCost = true`).
  * Atomic stock receipt via `StockPostingService` (`PURCHASE_RECEIPT`, `direction = IN`).
  * Supplier payments with CASH/ONLINE splits and payment allocation tracking against invoices.
  * Advance payment credit tracking and standalone allocation application.
  * Purchase returns reducing inventory (`PURCHASE_RETURN`, `direction = OUT`), checking cumulative line return limits, enforcing non-negative stock, and releasing excess payment allocations back to supplier credit.
  * Supplier cash/online refunds consuming unallocated credit without touching stock.
  * Immutable supplier ledger with running balances and signed accounting conventions:
    * Positive (+) = Shop owes supplier (Payable / Liability)
    * Negative (-) = Supplier owes shop or holds advance (Credit / Asset)
  * Supplier account summaries and ledger statements with opening/closing balances across date ranges.
* **BE05: Customer Invoices, Immediate Billing, Receipts, Advances & Customer Ledger** (Complete)
  * Direct point-of-sale customer invoicing without sales orders or quotations.
  * Customer and optional site resolution with ownership validation.
  * Authoritative GST-inclusive pricing with preview endpoint (`POST /api/v1/invoices/preview`) and concurrency fingerprint verification.
  * NORMAL and GST display formats with identical payable totals.
  * Strict non-negative stock enforcement; atomic stock deduction (`StockMovementType.SALE`, `direction = OUT`).
  * Inward customer receipts with CASH/ONLINE split and advance credit accumulation.
  * Advance credit application to invoices without duplicating ledger effects.
  * Due dates (`invoiceDate + 30 days`) and overdue calculation (`isOverdue`, `daysOverdue`).
  * Customer dues search and dues summary KPI calculation.
  * Immutable customer ledger with running balances:
    * Positive (+) = Customer owes shop (Receivable / Charge)
    * Negative (-) = Shop owes customer or customer holds advance (Receipt / Credit)
  * Customer account summaries and statement with opening/closing balances across date ranges.
* **BE06: Bill Revisions, Customer Returns & Customer Refunds** (Complete)
  * Authoritative versioned bill corrections (`sales_invoice_revision`) preserving original invoice identity, invoice number, invoice date, and due date.
  * Line modifications, additions, and removals with stock delta postings:
    * Delta > 0: stock OUT (`SALE`).
    * Delta < 0: stock IN (`REVERSAL`).
    * Delta = 0: 0 stock movement.
  * Releasing excess payment allocations back to customer receipts when payable is reduced below allocated payments.
  * Customer returns accepted at any time without arbitrary age restrictions.
  * Returns enforce remaining returnable piece limits ($quantity - returnedQuantity$).
  * Explicit valuation policy handling: historical price-edited lines require explicit policy (`ORIGINAL_INVOICE_PRICE` vs `LATEST_REVISED_PRICE`).
  * Customer refunds disburse unallocated customer advance credit via CASH/ONLINE without touching stock.
  * Full customer ledger reconciliation: Invoices (+), Receipts (-), Returns (-), Refunds (+).

---

## 2. API Endpoints (BE04A & BE04B)

### Inventory & Adjustments (BE04A)
| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/v1/inventory` | List inventory balances with filters | Owner, Manager |
| `GET` | `/api/v1/inventory/variants/{id}` | Get inventory balance for variant | Owner, Manager |
| `GET` | `/api/v1/inventory/movements` | List stock movements across variants | Owner, Manager |
| `GET` | `/api/v1/inventory/variants/{id}/movements` | List stock movements for single variant | Owner, Manager |
| `POST` | `/api/v1/inventory/adjustments` | Perform manual stock adjustments | Owner, Manager |

### Supplier Purchases & Returns (BE04B)
| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/v1/purchases` | Create inward purchase invoice, receive stock, post ledger | Owner, Manager |
| `GET` | `/api/v1/purchases/{id}` | Get purchase details with lines and allocation status | Owner, Manager |
| `GET` | `/api/v1/purchases` | Search purchases with supplier, status, date filters | Owner, Manager |
| `POST` | `/api/v1/purchases/{id}/returns` | Return goods to supplier against purchase, reduce stock | Owner, Manager |
| `GET` | `/api/v1/purchases/{id}/returns` | List returns for specific purchase | Owner, Manager |
| `GET` | `/api/v1/purchase-returns/{id}` | Get purchase return details | Owner, Manager |
| `GET` | `/api/v1/purchase-returns` | Search purchase returns across suppliers | Owner, Manager |

### Supplier Financials & Ledger (BE04B)
| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/v1/suppliers/{id}/payments` | Record supplier payment (CASH, ONLINE) & allocate | Owner, Manager |
| `GET` | `/api/v1/suppliers/{id}/payments` | List payments made to supplier | Owner, Manager |
| `POST` | `/api/v1/suppliers/{id}/allocations` | Apply unallocated payment credit to purchases | Owner, Manager |
| `POST` | `/api/v1/suppliers/{id}/refunds` | Record refund received from supplier (consumes credit) | Owner, Manager |
| `GET` | `/api/v1/suppliers/refunds/{id}` | Get supplier refund details | Owner, Manager |
| `GET` | `/api/v1/suppliers/{id}/account-summary` | Get supplier account totals and current balance | Owner, Manager |
| `GET` | `/api/v1/suppliers/{id}/ledger` | Get supplier ledger statement with opening/closing balances | Owner, Manager |

### Customer Invoices & Billing (BE05)
| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/v1/invoices/preview` | Preview invoice calculations, taxes, fingerprint | Owner, Manager |
| `POST` | `/api/v1/invoices` | Create posted customer sales bill, reduce stock, post ledger | Owner, Manager |
| `GET` | `/api/v1/invoices/{id}` | Get complete invoice details, lines, allocations | Owner, Manager |
| `GET` | `/api/v1/invoices` | Search invoices with customer, site, status, date filters | Owner, Manager |

### Customer Receipts, Dues & Ledger (BE05)
| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/v1/customers/{id}/receipts` | Record customer receipt (CASH, ONLINE) & allocate | Owner, Manager |
| `GET` | `/api/v1/customers/{id}/receipts` | List receipts for customer | Owner, Manager |
| `POST` | `/api/v1/customers/{id}/allocations` | Apply unallocated customer credit to invoices | Owner, Manager |
| `GET` | `/api/v1/dues` | Search unpaid invoices with overdue filters | Owner, Manager |
| `GET` | `/api/v1/dues/summary` | Get outstanding & overdue KPI aggregates | Owner, Manager |
| `GET` | `/api/v1/customers/{id}/account-summary` | Get customer financial summary | Owner, Manager |
| `GET` | `/api/v1/customers/{id}/ledger` | Get customer ledger statement with date filtering | Owner, Manager |

### Invoice Revisions, Returns & Refunds (BE06)
| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/v1/invoices/{id}/revisions/preview` | Preview line differences, new totals, and released allocation | Owner, Manager |
| `POST` | `/api/v1/invoices/{id}/revisions` | Apply authoritative versioned revision to sales invoice | Owner, Manager |
| `GET` | `/api/v1/invoices/{id}/revisions` | List historical revision snapshots for invoice | Owner, Manager |
| `GET` | `/api/v1/invoices/{id}/revisions/{revId}` | Get specific revision details and snapshot payload | Owner, Manager |
| `POST` | `/api/v1/invoices/{id}/returns/preview` | Preview return valuation, amounts, and released allocation | Owner, Manager |
| `POST` | `/api/v1/invoices/{id}/returns` | Process customer return, restore stock, post ledger credit | Owner, Manager |
| `GET` | `/api/v1/invoices/{id}/returns` | List customer returns for invoice | Owner, Manager |
| `GET` | `/api/v1/customer-returns/{id}` | Get customer return details by ID | Owner, Manager |
| `GET` | `/api/v1/customer-returns` | Search customer returns across customers & dates | Owner, Manager |
| `POST` | `/api/v1/customers/{id}/refunds` | Disburse refund from unallocated customer credit (CASH/ONLINE) | Owner, Manager |
| `GET` | `/api/v1/customers/{id}/refunds` | List customer refunds for customer | Owner, Manager |
| `GET` | `/api/v1/customer-refunds/{id}` | Get customer refund details by ID | Owner, Manager |
| `GET` | `/api/v1/customer-refunds` | Search all customer refunds across dates | Owner, Manager |
| `POST` | `/api/v1/customers/{id}/receipts` | Record customer receipt (CASH, ONLINE) & allocate | Owner, Manager |
| `GET` | `/api/v1/customers/{id}/receipts` | List receipts for specific customer | Owner, Manager |
| `GET` | `/api/v1/customer-receipts/{id}` | Get customer receipt details | Owner, Manager |
| `POST` | `/api/v1/customers/{id}/allocations` | Apply unallocated customer credit to invoices | Owner, Manager |
| `GET` | `/api/v1/dues` | Search due and overdue invoices | Owner, Manager |
| `GET` | `/api/v1/dues/summary` | Get total outstanding, total overdue, and invoice counts | Owner, Manager |
| `GET` | `/api/v1/customers/{id}/account` | Get customer account totals, credit, and current balance | Owner, Manager |
| `GET` | `/api/v1/customers/{id}/ledger` | Get customer ledger statement with opening/closing balances | Owner, Manager |

---

## 3. Getting Started

### Prerequisites
* Java 21 LTS
* PostgreSQL 16 running on localhost:5432 (databases `himmat_dev` and `himmat_test`)

### Run Tests
```bash
./mvnw test
```

### Run Application
```bash
./mvnw spring-boot:run
```

API documentation (Swagger UI) is accessible at `http://localhost:8080/swagger-ui.html`.
