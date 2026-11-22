package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class InventoryBalanceMissingException extends AppException {
    public InventoryBalanceMissingException(Long variantId) {
        super("INVENTORY_BALANCE_MISSING", "Inventory balance record is missing for variant ID: " + variantId, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
