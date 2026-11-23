# Product Import Guide: Controlled Catalog Loading (BE08)

This guide documents the controlled, developer-operated catalog loading framework for Himmat Enterprises. It details the actual source mapping for the client's Finolex price list workbook, developer CLI operations, modes, validation rules, audit tracking, and replay safety.

---

## 1. Client Workbook Inspection & Source Mapping

### 1.1 Source File Characteristics
* **Inspected Workbook**: `Finolex_Combined_Price_List_Billing.xlsx` (located in `data/Finolex_Combined_Price_List_Billing.xlsx`).
* **Source Checksum (SHA-256)**: `270d27309f78ad4299af470538ee56f75e66f9d5b785053b46fd13e33eab30ab`.
* **Sheet Name**: `Price List` (single sheet).
* **Header Row**: Row 1 (1-indexed).
* **Data Rows**: 1,809 rows (Row 2 to 1810).
* **Duplicates in Source**: 0 duplicate composite keys.
* **Missing / Malformed / Zero Prices**: 0 (all 1,809 prices are strictly positive numbers).

### 1.2 Master Category Distribution
The workbook covers 5 major plumbing/electrical categories:
1. `ASTM White`: 477 items
2. `SWR & Drainage`: 456 items
3. `Agri`: 430 items
4. `CPVC`: 386 items
5. `Column Pipe`: 60 items

### 1.3 Column Mapping Specification

| Column | Source Header | Example Value | Target Entity & Field | Transformation / Validation | Required? | Interpretation Notes |
|---|---|---|---|---|---|---|
| **A** | `Sr No` | `1`, `2` | Audit raw values | String representation stored in `CatalogImportRow.rawValues` | Optional | Sequence number from vendor. |
| **B** | `Price List` | `ASTM White`, `CPVC` | `Category.name` | Trimmed, title-case preserved. Checked against `category(name)`. Created if missing. Max length: 120. | **Required** | Represents the master category. Default discounts set to 0.00%. |
| **C** | `Sub Category` | `ASTM Plain Pipe` | `Subcategory.name` | Trimmed. Linked to resolved `Category`. Max length: 160. | **Required** | Represents product family. |
| **D** | `Product Name` | `SCH40 Plain Pipe 6M` | `Product.name` | Trimmed. Linked to resolved `Brand` ("Finolex") and `Subcategory`. Max length: 240. | **Required** | Represents the master product. |
| **E** | `Size` | `1/2"`, `25mm`, `3M` | `ProductVariant.sizeLabel` | Trimmed. Max length: 120. | **Required** | Variant sellable size unit. |
| **F** | `Price (INR)` | `513`, `1002.50` | `ProductVariant.mrpInclTax` | Parsed to `BigDecimal`, 2 decimal scale (`HALF_UP`). Must be $> 0.00$. | **Required** | **Confirmed: MRP per piece, GST-inclusive**. |
| - | - | - | `ProductVariant.sku` | Generated deterministic SKU: `FIN-{PRODUCT_SLUG}-{SIZE_SLUG}-{MD5_HASH}`. Max 100 chars. | **Required** | Globally unique, reproducible across runs. |
| - | - | - | `ProductVariant.pricingMethod` | Constant: `PricingMethod.MRP_DISCOUNT`. | **Required** | Standard retail pricing method for MRP-based goods. |
| - | - | - | `ProductVariant.purchaseCostExclTax` | Set to `null`. | - | **Unknown** in price list. Must not be derived or guessed. |
| - | - | - | `ProductVariant.gstRatePct` | Set to `null`. | - | **Unknown** in price list. Must not be defaulted. |
| - | - | - | `ProductVariant.profitPct` | Set to `null`. | - | **Unknown**. |
| - | - | - | `InventoryBalance.quantityOnHand` | Set to `0`. | **Required** | Initialized atomically 1:1 with variant. System initialization only. |
| - | - | - | `InventoryBalance.openingConfirmed` | Set to `false`. | **Required** | Indicates physical opening stock count is pending. |

---

## 2. Developer Import CLI & Operations

The import runner is developer-operated and **disabled by default during normal application startup**. It does not expose any web endpoints or customer upload forms.

### 2.1 Activation Property
The runner activates **only** when `--app.import.enabled=true` is passed. When missing or false, Spring Boot skips instantiation (`@ConditionalOnProperty(name = "app.import.enabled", havingValue = "true")`).

### 2.2 CLI Execution Commands

#### Mode A: Dry Run (Default & Recommended First Step)
Parses the Excel file, validates every row, inspects existing database records, and outputs a planned actions summary without writing any business data.
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="\
  --app.import.enabled=true \
  --app.import.file=data/Finolex_Combined_Price_List_Billing.xlsx \
  --app.import.mode=DRY_RUN \
  --app.import.operator=saurabh"
```

#### Mode B: Create-Only Mode
Creates valid new catalog records (Brand, Category, Subcategory, Product, Variant, and 0-stock InventoryBalance). If a record already exists with an identical price, it is marked `UNCHANGED`. If a record exists with a conflicting price, it is marked `REJECTED`, and the process halts with status `CONFLICT` without modifying the existing record.
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="\
  --app.import.enabled=true \
  --app.import.file=data/Finolex_Combined_Price_List_Billing.xlsx \
  --app.import.mode=CREATE_ONLY \
  --app.import.operator=saurabh"
```

#### Mode C: Explicit Update Mode
Explicitly updates only the mapped `mrpInclTax` field on existing variants. Concurrency protection ensures no concurrent manager updates are blindly overwritten. Unmapped fields (`purchaseCostExclTax`, `gstRatePct`, `lowStockThreshold`, `isActive`) are strictly preserved.
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="\
  --app.import.enabled=true \
  --app.import.file=data/Finolex_Combined_Price_List_Billing.xlsx \
  --app.import.mode=UPDATE \
  --app.import.operator=saurabh"
```

---

## 3. Safety Guarantees & Constraints

1. **Zero Stock Movements**:
   - The import creates an `InventoryBalance` row with `quantityOnHand = 0` and `openingConfirmed = false`.
   - It **never** posts a `stock_movement` pretending products were physically received.
2. **Zero Financial Ledger Entries**:
   - No customer or supplier ledger entries are created.
3. **Preservation of Manually Maintained Fields**:
   - In `UPDATE` mode, purchase cost, GST rates, category discounts, and active flags maintained by shop personnel are never reset or overwritten.
4. **Formula Injection Prevention**:
   - All string cells starting with `=`, `+`, `-`, or `@` are sanitized with a leading single quote to prevent spreadsheet injection attacks if exported.
5. **No Destructive Rollback**:
   - There is deliberately no "delete import" command. Imported products may be referenced by subsequent purchases or sales. Corrections are applied via reviewed follow-up imports.

---

## 4. Audit & Run Tracking Tables

* **`catalog_import_run`**:
  Tracks every import invocation with run code (`IMP-%05d`), source filename, SHA-256 file checksum, mapping version, operator identity, status (`COMPLETED`, `CONFLICT`, `FAILED`), row counts (total, created, updated, unchanged, rejected), start/completion timestamps, and error summaries.
* **`catalog_import_row`**:
  Line-level audit recording sheet name, row number, source identity, target variant ID, action taken (`CREATED`, `UPDATED`, `UNCHANGED`, `REJECTED`, `DRY_RUN_PLAN`), version transitions, and error reasons.
