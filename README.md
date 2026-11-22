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

---

## 2. API Endpoints (BE04A)

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/v1/inventory` | List inventory balances with filters | Owner, Manager |
| `GET` | `/api/v1/inventory/variants/{id}` | Get inventory balance for variant | Owner, Manager |
| `GET` | `/api/v1/inventory/movements` | List stock movements across variants | Owner, Manager |
| `GET` | `/api/v1/inventory/variants/{id}/movements` | List stock movements for single variant | Owner, Manager |
| `POST` | `/api/v1/inventory/adjustments` | Perform manual stock adjustments | Owner, Manager |

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
