package com.asalkartechworks.himmat.identity.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Centralized application permissions and role-to-permission mappings.
 */
public final class Permission {

    // User Administration (Owner only by proposed default)
    public static final String USER_READ = "USER:READ";
    public static final String USER_WRITE = "USER:WRITE";

    // Shop Settings (Owner & Manager)
    public static final String SETTINGS_READ = "SETTINGS:READ";
    public static final String SETTINGS_WRITE = "SETTINGS:WRITE";

    // Catalog & Pricing (Owner & Manager)
    public static final String CATALOG_READ = "CATALOG:READ";
    public static final String CATALOG_WRITE = "CATALOG:WRITE";

    // Inventory & Stock (Owner & Manager)
    public static final String INVENTORY_READ = "INVENTORY:READ";
    public static final String INVENTORY_WRITE = "INVENTORY:WRITE";

    // Purchases & Inward Goods (Owner & Manager)
    public static final String PURCHASE_READ = "PURCHASE:READ";
    public static final String PURCHASE_WRITE = "PURCHASE:WRITE";

    // Sales & Billing (Owner & Manager)
    public static final String SALES_READ = "SALES:READ";
    public static final String SALES_WRITE = "SALES:WRITE";

    // Receipts, Credits & Refunds (Owner & Manager)
    public static final String SETTLEMENT_READ = "SETTLEMENT:READ";
    public static final String SETTLEMENT_WRITE = "SETTLEMENT:WRITE";

    // Expenses (Owner & Manager)
    public static final String EXPENSE_READ = "EXPENSE:READ";
    public static final String EXPENSE_WRITE = "EXPENSE:WRITE";

    // Parties: Customers & Suppliers (Owner & Manager)
    public static final String CUSTOMER_READ = "CUSTOMER:READ";
    public static final String CUSTOMER_WRITE = "CUSTOMER:WRITE";
    public static final String SUPPLIER_READ = "SUPPLIER:READ";
    public static final String SUPPLIER_WRITE = "SUPPLIER:WRITE";

    // Reports
    public static final String REPORT_OPERATIONAL = "REPORT:OPERATIONAL";
    public static final String REPORT_PROFIT = "REPORT:PROFIT"; // Owner only

    private static final Set<String> ALL_PERMISSIONS = Set.of(
            USER_READ, USER_WRITE,
            SETTINGS_READ, SETTINGS_WRITE,
            CATALOG_READ, CATALOG_WRITE,
            INVENTORY_READ, INVENTORY_WRITE,
            PURCHASE_READ, PURCHASE_WRITE,
            SALES_READ, SALES_WRITE,
            SETTLEMENT_READ, SETTLEMENT_WRITE,
            EXPENSE_READ, EXPENSE_WRITE,
            CUSTOMER_READ, CUSTOMER_WRITE,
            SUPPLIER_READ, SUPPLIER_WRITE,
            REPORT_OPERATIONAL, REPORT_PROFIT
    );

    private static final Set<String> MANAGER_PERMISSIONS = Set.of(
            SETTINGS_READ, SETTINGS_WRITE,
            CATALOG_READ, CATALOG_WRITE,
            INVENTORY_READ, INVENTORY_WRITE,
            PURCHASE_READ, PURCHASE_WRITE,
            SALES_READ, SALES_WRITE,
            SETTLEMENT_READ, SETTLEMENT_WRITE,
            EXPENSE_READ, EXPENSE_WRITE,
            CUSTOMER_READ, CUSTOMER_WRITE,
            SUPPLIER_READ, SUPPLIER_WRITE,
            REPORT_OPERATIONAL
    );

    private Permission() {
    }

    public static Set<String> getPermissionsForRole(UserRole role) {
        if (role == null) {
            return Collections.emptySet();
        }
        return switch (role) {
            case OWNER -> new LinkedHashSet<>(ALL_PERMISSIONS);
            case MANAGER -> new LinkedHashSet<>(MANAGER_PERMISSIONS);
        };
    }
}
