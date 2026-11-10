package com.bysellens.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String code,
        String name,
        String category,
        String shade,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        Integer initialStock,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("deleted_at") Instant deletedAt) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(), product.getCode(), product.getName(), product.getCategory(),
                product.getShade(), product.getPurchasePrice(), product.getSalePrice(),
                product.getInitialStock(), product.getCreatedAt(), product.getUpdatedAt(),
                product.getDeletedAt());
    }
}
