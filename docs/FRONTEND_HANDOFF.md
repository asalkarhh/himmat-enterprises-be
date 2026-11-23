# Frontend Handoff Specification (BE01 – BE08)

This document is the authoritative integration contract for the upcoming React/Vite frontend team, establishing API envelopes, authentication lifecycles, data type serialization, concurrency patterns, and design system tokens adapted from Daddy Soda Admin.

---

## 1. Authentication & Session Contract

* **Base URL**: `/api/v1`
* **Auth Scheme**: Stateless Bearer JWT via HTTP Header: `Authorization: Bearer <token>`
* **Login Endpoint**: `POST /api/v1/auth/login`
  * Request: `{ "username": "...", "password": "..." }`
  * Response: `{ "data": { "token": "...", "user": { "id": "1", "username": "...", "displayName": "...", "role": "OWNER" } } }`
* **Roles**:
  * `OWNER`: Full administrative, operational, and financial profit report access.
  * `MANAGER`: Full operational access (inventory, sales, purchases, payments, returns, operational reports); profit reports and dashboard profit cards return `403 Forbidden` or `null`.

---

## 2. API Envelopes & Serialization Rules

### 2.1 Envelopes
* **Success Envelope (`ApiResponse<T>`)**:
  ```json
  {
    "data": { ... },
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
  ```
* **Paginated Response (`PageResponse<T>`)**:
  ```json
  {
    "data": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "requestId": "..."
  }
  ```
* **Error Envelope (`ErrorResponse`)**:
  ```json
  {
    "error": {
      "code": "INSUFFICIENT_STOCK",
      "message": "Insufficient stock for variant [id=10, sku=APC-1L]. Requested: 15, on hand: 10",
      "fields": []
    },
    "requestId": "..."
  }
  ```

### 2.2 Critical Data Type Rules
1. **BIGINT IDs as Strings**: All database primary keys (`id`, `variantId`, `customerId`, `invoiceId`, etc.) are serialized as JSON strings (e.g. `"id": "105"`) to prevent IEEE-754 64-bit integer precision loss in JavaScript browsers.
2. **Money Values as Strings**: All monetary amounts (`totalPayableAmount`, `allocatedAmount`, `outstandingAmount`, `mrpInclTax`, etc.) are serialized as fixed 2-decimal strings (e.g. `"1500.00"`). Parse using a decimal math library (e.g., `decimal.js` or `big.js`) or format directly with rupee currency formatter (`₹1,500.00`).
3. **Quantities as Whole Integers**: Quantities are whole integer pieces (`Integer`). Boxes, liquid decimal litres, and bottle fractions from Soda are **omitted**.

---

## 3. Concurrency, Idempotency & Optimistic Locking

### 3.1 Idempotency Key
Every state-changing financial POST request (`/invoices`, `/invoices/{id}/revisions`, `/invoices/{id}/returns`, `/customers/{id}/receipts`, `/customers/{id}/refunds`, `/purchases`, `/purchases/{id}/returns`, `/suppliers/{id}/payments`, `/suppliers/{id}/refunds`, `/expenses`) MUST send a unique client-generated UUID in the header:
```http
Idempotency-Key: 7b2d56c8-6c84-48d8-9fa4-706509653a2b
```
If a network timeout occurs, re-submitting with the identical `Idempotency-Key` and payload will safely replay the original response without duplicate side effects.

### 3.2 Optimistic Versioning
Entities carry a `version` integer:
* When updating master records or applying invoice revisions, pass `expectedVersion: entity.version`.
* If another user updated the record in the meantime, the server returns `409 CONFLICT` with error code `STALE_VERSION`. Prompt the user to reload the latest record.

---

## 4. Key Business Workflows for UI Screens

### 4.1 Immediate Sales Billing (`/billing/new`)
1. **No Sales Order / Quotation**: Billing is direct and immediate.
2. **Pricing Preview**: As user enters items, call `POST /api/v1/invoices/preview` to obtain calculated subtotal, GST breakdown, total payable, and a cryptographic `pricingFingerprint`.
3. **Invoice Creation**: Submit lines along with `pricingFingerprint`.
4. **Display Formats**: Toggle between `NORMAL` (simplified POS bill) and `GST` (detailed tax invoice). Note: payable total is identical.

### 4.2 Customer Dues & Receipts (`/customers/:id/receipts`)
* **Split Components**: Support entering split payments across `CASH` and `ONLINE` (with bank/UPI reference).
* **Automatic Advance Accumulation**: If receipt amount exceeds allocated invoices, the remainder automatically becomes "Customer Advance Credit" available for future bills.
* **Customer Ledger Sign**:
  * Positive (+) = Customer owes shop (Receivable).
  * Negative (-) = Customer holds advance credit (Payable to customer).

### 4.3 Invoice Revisions & Returns
* **Revisions**: Call `/api/v1/invoices/{id}/revisions/preview` to show the user the delta payable before submitting. Original invoice number and date are preserved.
* **Returns**: Display original quantity, returned quantity, and returnable balance. If an item underwent price revisions, provide a policy dropdown: `"Original Invoice Price"` vs `"Latest Revised Price"`.

### 4.4 Dashboard & Owner Profit
* When `GET /api/v1/dashboard/summary` returns `profitSummary == null` (Manager user), hide the profit card completely.
* When `profitSummary != null` (Owner user), display Net Revenue, COGS, Gross Profit, Operating Expenses, Net Profit, and Coverage %.

### 4.5 PDF Document Downloads
All PDF endpoints stream `application/pdf` bytes directly:
* Sales Invoices: `GET /api/v1/invoices/{id}/pdf?format=NORMAL` & `format=GST`
* Revisions: `GET /api/v1/invoices/{id}/revisions/{revId}/pdf`
* Customer Receipts: `GET /api/v1/customer-receipts/{id}/pdf`
* Customer Returns: `GET /api/v1/customer-returns/{id}/pdf`
* Customer Refunds: `GET /api/v1/customer-refunds/{id}/pdf`
* Customer Ledger: `GET /api/v1/customers/{id}/ledger/pdf`
* Purchases: `GET /api/v1/purchases/{id}/pdf`
* Purchase Returns: `GET /api/v1/purchases/returns/{id}/pdf`
* Supplier Payments: `GET /api/v1/supplier-payments/{id}/pdf`
* Supplier Ledger: `GET /api/v1/suppliers/{id}/ledger/pdf`
* Expenses: `GET /api/v1/expenses/{id}/pdf`

---

## 5. UI Design Tokens & Reference Reuse (Daddy Soda)

* **Primary Accent**: `teal-600` (`#0d9488`), Hover: `teal-700` (`#0f766e`).
* **Canvas Background**: `slate-50` (`#f8fafc`).
* **Card Surfaces**: Pure white `bg-white`, borders `border-slate-200`, corners `rounded-xl`.
* **Headings**: `slate-900` (`#0f172a`), font-weight `font-semibold`.
* **Muted Text**: `slate-500` (`#64748b`).
* **Financial Badges**:
  * Overdue / Payable Due: `bg-rose-50 text-rose-700 border-rose-200`
  * Settled / Paid / In-Stock: `bg-emerald-50 text-emerald-700 border-emerald-200`
  * Advance / Credit: `bg-sky-50 text-sky-700 border-sky-200`
  * Low Stock Alert: `bg-amber-50 text-amber-700 border-amber-200`
