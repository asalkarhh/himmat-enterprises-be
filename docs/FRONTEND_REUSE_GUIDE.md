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
