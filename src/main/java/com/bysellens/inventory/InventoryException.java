package com.bysellens.inventory;

import java.util.List;

public class InventoryException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<ApiError.FieldDetail> details;

    public InventoryException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public InventoryException(int status, String code, String message, List<ApiError.FieldDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ApiError.FieldDetail> getDetails() {
        return details;
    }

    public static InventoryException badRequest(String message) {
        return new InventoryException(400, "VALIDATION_ERROR", message);
    }

    public static InventoryException notFound() {
        return new InventoryException(404, "NOT_FOUND", "Product not found.");
    }

    public static InventoryException duplicateCode() {
        return new InventoryException(409, "PRODUCT_CODE_EXISTS", "A product with this code already exists.");
    }
}
