# Frontend Reuse Guide: Inventory Management & Stock Movements (BE04A)

This document specifies the UI/UX conventions, component adaptations from the Daddy Soda admin reference, API contracts, and user interaction patterns for Himmat Enterprises' upcoming React/Vite frontend.

---

## 1. Design System & Theming Baseline

The frontend inherits the admin interface design established in BE01:
* **Primary Brand Accent**: Teal (`teal-600` for primary buttons, links, active navigation; `teal-700` for hover).
* **Surface & Cards**: Pure white (`bg-white`) cards with subtle slate borders (`border-slate-200`) and rounded corners (`rounded-lg` / `rounded-xl`).
* **Page Canvas Background**: Slate background (`bg-slate-50` or `bg-slate-100`).
* **Typography & Text**: Slate text hierarchy (`text-slate-900` headings, `text-slate-600` body, `text-slate-400` muted captions).
* **Quantities Display**: Always displayed as whole integer pieces (`15 pcs` or `15`). Boxes and fractional bottle sizes from Soda are **strictly omitted**.

---

## 2. Inventory Balance List (`/inventory`)

### 2.1 Visual Layout & Filters
* **Top Header & Action Bar**:
  * Page title: "Inventory & Stock".
  * Primary Action: "Manual Stock Adjustment" button (`bg-teal-600 hover:bg-teal-700 text-white flex items-center gap-2`).
* **Filter Bar**:
  * **Search input**: Debounced text search matching product name, variant size label, or SKU.
  * **Brand dropdown**: Dynamic options from `GET /api/v1/brands?isActive=true`.
  * **Category & Subcategory dropdowns**: Dynamic options from `GET /api/v1/categories` and subcategories.
  * **Stock Status dropdown**: Options: `All Stock`, `In Stock (> 0)`, `Out of Stock (= 0)`. Maps to `stockStatus` query parameter (`ALL`, `IN_STOCK`, `OUT_OF_STOCK`).
  * **Active toggle**: Checkbox or pill selector to view Active items vs All (including inactive/discontinued).
* **Data Table**:
  * Headers: `Product & Size`, `Brand`, `Category`, `SKU`, `MRP / Price`, `Quantity on Hand`, `Status`, `Actions`.
  * **Quantity on Hand**: Bold whole integer. If `isLowStock == true`, render a subtle warning pill (`bg-amber-50 text-amber-700 border border-amber-200`).
  * **Actions Column**:
    * "Adjust Stock" (opens modal pre-populated with this variant).
    * "View Movements" (opens slide-over drawer or navigates to `/inventory/movements?variantId=:id`).
* **Pagination**: Standard zero-indexed pagination reading `PageResponse<InventoryItemDto>` (`page`, `size`, `totalElements`, `totalPages`).

---

## 5. Supplier Purchases, Payments, Returns & Ledger (BE04B)

### 5.1 Purchase Invoice Creation (`/purchases/new`)
* **Header Form**:
  * **Supplier Selector**: Autocomplete with code, name, and current ledger balance.
  * **Supplier Invoice Number**: Text input for physical vendor invoice number.
  * **Purchase Date**: Defaults to today (`Asia/Kolkata`).
  * **Notes**: Optional text area.
* **Line Item Table**:
  * **Variant Selector**: Search by SKU or product name.
  * **Quantity**: Integer input $\ge 1$.
  * **Price Basis Selector**: Toggle between `GST_INCLUSIVE` and `GST_EXCLUSIVE`.
  * **Entered Unit Price**: Currency input.
  * **Discount %**: Defaults to category purchase discount (e.g. 10%), overridable by user.
  * **GST %**: Dropdown or numeric input (0%, 5%, 12%, 18%, 28%).
  * **Update Product Cost Toggle**: Checkbox ("Update Master Purchase Cost"). Requires GST % if price is inclusive.
  * **Computed Columns**: Net Unit Cost, Gross Unit Cost, Line Total.
* **Summary Footer**:
  * Displays: Subtotal (Excl Tax), Total Discount, Total GST, Total Payable.
  * Optional: **Payment Section** to record immediate payment (Cash/Online) along with inward invoice.
* **Submission**:
  * Sends `Idempotency-Key` header with client UUID.

### 5.2 Purchase Return Drawer / Modal (`/purchases/:id/return`)
* **Header**: Displays Purchase Number, Supplier Name, Invoice Date, Original Payable.
* **Lines Table**:
  * Displays: SKU, Product Name, Original Quantity, Previously Returned, Available Returnable Qty, In-Stock On-Hand Qty.
  * **Return Quantity Input**: Bounded between 1 and `min(Available Returnable, In-Stock)`.
  * **Reason**: Mandatory return explanation.
* **Live Calculation**: Displays total return refund amount. Shows warning if excess payment allocation will be released back to supplier credit.

### 5.3 Supplier Payments & Credit Allocation (`/suppliers/:id/payments`)
* **Payment Modal**:
  * **Total Amount**: Currency input.
  * **Components Split**: Multi-row CASH and ONLINE inputs with reference number.
  * **Allocations Section**: Lists unpaid posted purchases with remaining outstanding balance; allows assigning amounts to each invoice.
  * Any unallocated excess automatically displays as "Supplier Advance Credit".
* **Standalone Credit Allocation**:
  * Select unallocated payment advance and assign to unpaid purchase invoices without moving net ledger balance.

### 5.4 Supplier Ledger & Account Summary (`/suppliers/:id/ledger`)
* **Account Summary Cards**:
  * **Current Balance**: Highlighted badge:
    * Positive: "Shop Owes: ₹X" (`text-rose-700 bg-rose-50`).
    * Negative: "Supplier Holds Credit / Advance: ₹X" (`text-emerald-700 bg-emerald-50`).
    * Zero: "Settled: ₹0.00" (`text-slate-600 bg-slate-100`).
  * **Summary Metrics**: Total Purchases, Total Paid, Total Returned, Total Refunded, Unallocated Credit, Outstanding Payables.
* **Ledger Statement Table**:
  * Date range selector (`startDate`, `endDate`).
  * Opening Balance display row.
  * Columns: `Date`, `Type` (Purchase, Payment, Return, Refund), `Reference #`, `Amount (Debit/Credit)`, `Running Balance`, `Notes`.
  * Closing Balance display row.

---

## 3. Manual Stock Adjustment Modal / Drawer

### 3.1 Requirements & Workflow
* **Permissions**: Available to both `OWNER` and `MANAGER`.
* **Header**: "Manual Stock Adjustment".
* **Reason Field**:
  * Text area, mandatory, minimum 3 characters, maximum 500 characters.
  * Placeholder: "e.g., Physical stock count reconciliation, damaged packaging disposal".
* **Adjustment Lines Table**:
  * Supports adding multiple lines (bounded between 1 and 100 lines).
  * **Variant Selector**: Autocomplete dropdown searching active variants by SKU or name. Disables selecting an already chosen variant (client-side validation preventing duplicate lines).
  * **Direction Toggle**: Switch or segmented button between `IN` (green pill) and `OUT` (amber pill).
  * **Quantity Input**: Numeric input restricted to positive whole integers (`min="1"`, `step="1"`). Floating point numbers are blocked.
  * **Expected Version**: Hidden input storing `variant.version` as loaded from the server.
  * **Live Stock Preview (Informational)**:
    * Displays: `Current: X pcs` $\rightarrow$ `After: Y pcs`.
    * Client-side warning if `OUT` adjustment exceeds `Current` stock. (Server remains the authoritative check).
  * **Add Line button**: Adds an additional variant row.
  * **Remove button**: Removes the row.
* **Submission & Idempotency**:
  * Generate a unique client UUID per submission: `Idempotency-Key: <UUID>`.
  * Submit `POST /api/v1/inventory/adjustments`.
  * If network timeout or retry occurs, resend with the **exact same** `Idempotency-Key` to replay the committed result safely.
* **Success State**:
  * Displays success alert listing updated variants with their new quantities and versions.
  * Refreshes the inventory list in the background.
* **Error Handling & Feedback**:
  * **`INSUFFICIENT_STOCK` (422)**:
    * Highlight the failing variant row in red.
    * Display error banner: "Insufficient stock. Available: X pcs, Requested deduction: Y pcs."
  * **`STALE_VERSION` (409)**:
    * Display conflict alert: "The stock record was modified by another user. Reload before editing."
    * Provide a one-click "Refresh Stock Data" button that updates `expectedVersion` to latest.
  * **`DUPLICATE_VARIANT_LINE` (422)**:
    * Highlight duplicate rows: "Each variant can only appear once per manual adjustment."
  * **`INACTIVE_VARIANT` (422)**:
    * Alert that selected variant or its parent product is inactive.

---

## 4. Stock Movement History (`/inventory/movements`)

### 4.1 Filterable Ledger View
* **Endpoint**: `GET /api/v1/inventory/movements` or `GET /api/v1/inventory/variants/{variantId}/movements`.
* **Filters**:
  * **Variant**: Autocomplete search filter.
  * **Direction**: `All`, `IN`, `OUT`.
  * **Movement Type**: Dropdown (`MANUAL_ADJUSTMENT_IN`, `MANUAL_ADJUSTMENT_OUT`, `PURCHASE_RECEIPT`, `PURCHASE_RETURN`, `SALE`, `CUSTOMER_RETURN`, `REVERSAL`).
  * **Date Range**: Date pickers for `fromDate` and `toDate` (formatted as ISO `YYYY-MM-DD`, interpreted by backend in `Asia/Kolkata` timezone).
* **Ledger Columns**:
  * `Timestamp`: Formatted as `DD MMM YYYY, hh:mm A` (IST).
  * `Variant`: SKU and display name.
  * `Movement Type`: Styled badge (e.g. blue for purchases, purple for sales, slate for adjustments).
  * `Direction`: `+IN` (emerald) or `-OUT` (rose).
  * `Quantity`: Whole pieces (`+10` or `-4`).
  * `Balance Before $\rightarrow$ After`: e.g. `20 $\rightarrow$ 16`.
  * `Reason / Notes`: Adjustment or document remarks.
  * `Reference / Event`: Source entity type and business event ID.
  * `Actor`: User ID or display name.

---

## 5. API Reference Summary

| Method | Endpoint | Description | Key Query / Body Parameters |
|---|---|---|---|
| `GET` | `/api/v1/inventory` | List inventory balances | `brandId`, `categoryId`, `subcategoryId`, `isActive`, `search`, `stockStatus`, `page`, `size`, `sort` |
| `GET` | `/api/v1/inventory/variants/{variantId}` | Get single variant balance | `variantId` (path) |
| `GET` | `/api/v1/inventory/movements` | List immutable movements | `variantId`, `movementType`, `direction`, `sourceEntityType`, `businessEventId`, `fromDate`, `toDate`, `page`, `size` |
| `GET` | `/api/v1/inventory/variants/{variantId}/movements` | Scoped variant movements | `variantId` (path), `movementType`, `direction`, `fromDate`, `toDate`, `page`, `size` |
| `POST` | `/api/v1/inventory/adjustments` | Perform manual adjustments | Body: `{ reason, lines: [{ variantId, direction, quantity, expectedVersion }] }`, Header: `Idempotency-Key` |

---

## 6. Customer Billing, Invoices, Receipts, Advances & Dues UI (BE05)

### 6.1 Create Customer Invoice Screen (`/invoices/new`)
* **Header Form**:
  * **Customer Selector**: Autocomplete with code, name, phone, current balance badge.
  * **Customer Site Selector**: Filtered to sites owned by selected customer; optional for over-the-counter sales.
  * **Invoice Date**: Defaults to today (rejects future dates).
  * **Display Format Toggle**: `NORMAL` vs `GST`. Displays helper note: *Both formats have the exact same payable total; GST format itemizes tax breakdown.*
  * **Notes**: Optional remarks.
* **Line Item Table**:
  * **Variant Selector**: Search by product name, SKU, or size label.
  * **Quantity**: Integer piece input $\ge 1$.
  * **Unit Price**: Auto-populated based on pricing method (MRP minus category discount, Cost-Plus, or Fixed). User override supported with required reason.
  * **Discount %**: Auto-populated from category sales discount, overridable.
  * **Computed Columns**: Unit Net Price, Line Total, GST Rate %, Taxable Amount, Tax Amount.
* **Live Calculation & Preview (`POST /api/v1/invoices/preview`)**:
  * Debounced preview call as lines change.
  * Displays: Gross Total, Total Discount, Taxable Amount, Total GST, Total Payable.
  * Surfaces warning if GST rates are missing on any line.
  * Captures `pricingFingerprint` to send on final invoice creation.
* **Payment & Settlement Options**:
  * **Immediate Payment**: Optional split CASH/ONLINE section to record receipt upon bill generation.
  * **Apply Available Advance**: Checkbox showing available unallocated customer credit with input amount.
* **Submission**:
  * Calls `POST /api/v1/invoices` with `Idempotency-Key`.

### 6.2 Invoice List & Aging Dashboard (`/invoices`)
* **Filters**: Customer, Site, Status (`POSTED`, `CANCELLED`), Settlement Status (`UNPAID`, `PARTIALLY_PAID`, `PAID`), Display Format, Date Range, Overdue Only toggle, Search.
* **Columns**: Invoice #, Date, Customer & Site, Total Payable, Allocated Amount, Outstanding, Status, Settlement Badge, Overdue Tag, Actions.
* **Overdue Badge**: When `isOverdue: true`, renders red warning tag: `"Overdue by X days"`.

### 6.3 Customer Receipts Modal (`/customers/:id/receipts`)
* **Components Split**: Multi-row CASH and ONLINE inputs with reference ID.
* **Allocations Section**: Lists unpaid posted invoices for this customer. User can allocate amounts to specific bills.
* Unallocated excess automatically saved as "Customer Advance Credit".

### 6.4 Customer Dues & Aging Dashboard (`/dues`)
* **KPI Summary Cards**:
  * Total Outstanding Amount across all unpaid posted invoices.
  * Total Overdue Amount across invoices past their 30-day due date.
  * Outstanding Invoice Count & Overdue Invoice Count.
* **Aging Table**: List of due invoices ordered by due date ascending, highlighting overdue bills.

### 6.5 Customer Ledger & Account Summary (`/customers/:id/ledger`)
* **Account Summary Card**:
  * Current Balance (`+₹X` = Customer owes shop, `-₹X` = Customer advance).
  * Metrics: Total Invoiced, Total Receipts, Total Returned, Total Refunded, Unallocated Advance Credit, Outstanding Dues.
* **Ledger Statement**:
  * Opening Balance $\rightarrow$ Chronological entries with signed amounts and running balances $\rightarrow$ Closing Balance.

---

## 7. Bill Corrections, Returns & Refunds (BE06)

### 7.1 Invoice Revision Modal (`/invoices/:id/revise`)
* **Header Form**:
  * Revision Reason input (mandatory textarea).
  * Concurrency guard: sends `expectedVersion` to prevent overwriting concurrent updates.
* **Line Item Table**:
  * Displays existing invoice lines with quantity, net unit price, and line total.
  * Allows increasing quantity (triggers additional stock deduction warning), decreasing quantity (restores stock differences), or setting to 0 / removing line.
  * Validation: Prevents reducing line quantity below already returned quantity (`returnedQuantity`).
  * Price overrides: Optional unit price override with mandatory reason.
* **Preview Diff Pane**:
  * Displays: Previous Total Payable, New Total Payable, Delta Payable (green for decrease, red for increase).
  * Released Allocation Warning: If invoice was paid/partially paid, highlights amount that will be automatically returned to customer receipt credit.
* **Submission**:
  * "Apply Invoice Revision" (`POST /api/v1/invoices/:id/revisions`, with `Idempotency-Key`).

### 7.2 Customer Return Flow (`/invoices/:id/return`)
* **Return Form**:
  * Return Date picker (defaults to today; past/future returns permitted).
  * Return Reason textarea.
  * Valuation Policy Selector: Rendered if any line underwent price revisions (`"Original Invoice Price"` vs `"Latest Revised Price"`).
* **Return Items Selector**:
  * Shows each invoice line, original quantity, already returned quantity, and maximum returnable quantity.
  * Input for `returnQuantity` ($1 \le qty \le remaining$).
  * Displays unit refund price based on resolved valuation policy and calculated refund total.
* **Financial Impact Callout**:
  * For unpaid invoices: "Reduces outstanding customer balance by ₹X".
  * For paid invoices: "Releases ₹X to customer unallocated advance credit".
* **Submission**:
  * "Process Customer Return" (`POST /api/v1/invoices/:id/returns`, with `Idempotency-Key`).

### 7.3 Customer Refund Modal (`/customers/:id/refund`)
* **Available Credit Banner**:
  * Displays available customer advance credit (sum of unallocated receipts and negative ledger balance).
* **Disbursement Form**:
  * Refund Amount: Currency input ($\le$ available customer credit).
  * Split Component Rows: Multi-row split between `CASH` and `ONLINE` (with UPI/bank transaction reference).
  * Reason textarea.
* **Submission**:
  * "Disburse Refund" (`POST /api/v1/customers/:id/refunds`, with `Idempotency-Key`). Updates customer balance to ₹0.00.
