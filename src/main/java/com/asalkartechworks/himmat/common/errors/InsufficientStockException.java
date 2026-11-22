package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends AppException {
    public InsufficientStockException(Long variantId, String sku, String productName, int requestedQuantity, int availableQuantity) {
        super("INSUFFICIENT_STOCK",
                String.format("Insufficient stock for variant [id=%d, sku=%s, name=%s]. Requested deduction: %d, available on hand: %d",
                        variantId, sku, productName, requestedQuantity, availableQuantity),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
