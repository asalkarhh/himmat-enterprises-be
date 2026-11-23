# Pending Client Decisions & Operational Clarifications

This document captures business, financial, and catalog decisions that remain pending clarification from the business owners of Himmat Enterprises. These items have been handled conservatively and safely in the backend code without fabricating assumptions.

---

## 1. Physical Opening Stock Quantities & Verification Workflow

* **Current Status in Backend**:
  * Products imported via the controlled catalog import or created via master endpoints receive an `inventory_balance` row initialized to `quantityOnHand = 0` and `openingConfirmed = false`.
  * Zero stock movements are fabricated.
* **Pending Decision**:
  * How will the physical count of on-hand inventory at the shop be conducted and loaded?
  * Will the client provide a physical stock take spreadsheet with piece counts per SKU, or will staff enter initial quantities via the manual adjustment screen (`POST /api/v1/inventory/adjustments`)?
  * Should opening stock confirmation be locked after an initial 14-day transition window?

---

## 2. Missing Purchase Costs & GST Tax Slabs in Vendor Price Lists

* **Current Status in Backend**:
  * The Finolex price list (`Finolex_Combined_Price_List_Billing.xlsx`) provides only MRP per piece.
  * Inward purchase costs (`purchaseCostExclTax`) and GST rate percentages (`gstRatePct`) are left `null` (unknown) rather than guessed.
  * In Owner Profit reports, sales of products with unknown cost are segregated into `salesValueExcludedUnknownCost`, and a `costCoveragePct` is calculated.
* **Pending Decision**:
  * When does the client wish to populate purchase costs and GST slabs?
  * Option A: Naturally over time as inward purchase invoices (`POST /api/v1/purchases`) are recorded with `updateProductCost = true`.
  * Option B: Supplement the price list with a master purchase discount matrix (e.g. 35% discount off MRP for Finolex fittings, 18% GST).

---

## 3. Ambiguous Return Valuation Policy After Price Corrections

* **Current Status in Backend**:
  * If a product's price was revised on an invoice and the customer subsequently returns pieces, the API requires the operator to select a valuation policy:
    * `ORIGINAL_INVOICE_PRICE`: Refunds based on the price when the bill was originally generated.
    * `LATEST_REVISED_PRICE`: Refunds based on the revised unit price.
  * If omitted on a price-revised line, the API halts with `422 RETURN_VALUATION_POLICY_REQUIRED`.
* **Pending Decision**:
  * Does the shop prefer an automatic default policy (e.g., always refund at the lowest paid price, or always latest revised price) to avoid prompting cashiers during returns?

---

## 4. Physical Print Layouts & Hardware Configuration

* **Current Status in Backend**:
  * The backend generates server-side OpenPDF documents for both `NORMAL` (simplified POS layout) and `GST` (formal tax invoice) formats with persisted shop settings (name, address, GSTIN, contact numbers).
* **Pending Decision**:
  * What physical printer hardware is installed at the counter (e.g. 3-inch 80mm ESC/POS thermal receipt printer, continuous dot-matrix billing printer, or standard A4 laser printer)?
  * Should invoices include pre-printed bank account details (IFSC, Account Number) and UPI QR codes for customer payments?

---

## 5. Counter Cash Drawer Shifts & Reconciliation (Future Roadmap)

* **Current Status in Backend**:
  * Cash inflows (receipts, supplier refunds) and outflows (supplier payments, customer refunds, operational expenses) are recorded with exact payment methods (`CASH` vs `ONLINE`).
* **Pending Decision**:
  * Does the shop require formal cashier shift opening/closing float declarations (e.g., opening float ₹2,000 in drawer, closing physical cash count, variance reporting)?
