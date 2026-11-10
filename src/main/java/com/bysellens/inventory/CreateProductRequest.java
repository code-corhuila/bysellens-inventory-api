package com.bysellens.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String category,
        String shade,
        @NotNull @DecimalMin("0") BigDecimal purchasePrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal salePrice,
        @NotNull @PositiveOrZero Integer initialStock) {
}
