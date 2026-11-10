package com.bysellens.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/inventory/products")
@Validated
public class InventoryController {
    private final ProductService products;

    public InventoryController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        ProductResponse created = products.create(request, userId(jwt), parseCorrelationId(correlationId));
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public ProductPageResponse list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return products.list(page, limit);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return products.get(id);
    }

    @PatchMapping("/{id}")
    public ProductResponse update(
            @PathVariable UUID id,
            @RequestBody JsonNode body,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return products.update(id, body, userId(jwt), parseCorrelationId(correlationId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        products.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw InventoryException.badRequest("The authenticated subject must be an administrator UUID.");
        }
    }

    private UUID parseCorrelationId(String value) {
        if (value == null || value.isBlank())
            return UUID.randomUUID();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID();
        }
    }
}
