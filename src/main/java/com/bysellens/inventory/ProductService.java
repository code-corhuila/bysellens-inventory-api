package com.bysellens.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private static final Set<String> PATCH_FIELDS = Set.of(
            "code", "name", "category", "shade", "purchasePrice", "salePrice");

    private final ProductRepository products;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public ProductService(ProductRepository products, OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.products = products;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ProductResponse create(CreateProductRequest request, UUID userId, UUID correlationId) {
        String code = request.code().trim();
        if (products.existsByCode(code)) {
            throw InventoryException.duplicateCode();
        }

        Product product = new Product();
        product.setCode(code);
        product.setName(request.name().trim());
        product.setCategory(request.category().trim());
        product.setShade(request.shade());
        product.setPurchasePrice(request.purchasePrice());
        product.setSalePrice(request.salePrice());
        product.setInitialStock(request.initialStock());
        product = products.save(product);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", product.getId());
        payload.put("code", product.getCode());
        payload.put("name", product.getName());
        payload.put("category", product.getCategory());
        payload.put("shade", product.getShade());
        payload.put("purchasePrice", product.getPurchasePrice());
        payload.put("salePrice", product.getSalePrice());
        payload.put("initialStock", product.getInitialStock());
        saveEvent("ProductCreated", "inventory.product-created", product, payload, userId, correlationId);
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductPageResponse list(int page, int limit) {
        Sort sort = Sort.by(Sort.Order.asc("code"), Sort.Order.asc("id"));
        Page<Product> result = products.findByDeletedAtIsNull(PageRequest.of(page - 1, limit, sort));
        return new ProductPageResponse(
                result.getContent().stream().map(ProductResponse::from).toList(),
                new ProductPageResponse.Meta(page, limit, result.getTotalElements(), result.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(findActive(id));
    }

    @Transactional
    public ProductResponse update(UUID id, JsonNode body, UUID userId, UUID correlationId) {
        if (body == null || !body.isObject() || body.isEmpty()) {
            throw InventoryException.badRequest("At least one mutable product field is required.");
        }
        body.fieldNames().forEachRemaining(field -> {
            if (!PATCH_FIELDS.contains(field)) {
                throw InventoryException.badRequest("Field '%s' is not allowed in a product update.".formatted(field));
            }
        });

        Product product = findActive(id);
        if (body.has("code")) {
            String code = requiredText(body, "code");
            if (products.existsByCodeAndIdNot(code, id)) {
                throw InventoryException.duplicateCode();
            }
            product.setCode(code);
        }
        if (body.has("name"))
            product.setName(requiredText(body, "name"));
        if (body.has("category"))
            product.setCategory(requiredText(body, "category"));
        if (body.has("shade")) {
            JsonNode shade = body.get("shade");
            if (shade == null || shade.isNull()) {
                product.setShade(null);
            } else if (shade.isTextual()) {
                product.setShade(shade.textValue());
            } else {
                throw InventoryException.badRequest("Field 'shade' must be a string or null.");
            }
        }
        if (body.has("purchasePrice"))
            product.setPurchasePrice(money(body, "purchasePrice", false));
        if (body.has("salePrice"))
            product.setSalePrice(money(body, "salePrice", true));

        product = products.save(product);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", product.getId());
        payload.put("name", product.getName());
        payload.put("category", product.getCategory());
        payload.put("shade", product.getShade());
        payload.put("purchasePrice", product.getPurchasePrice());
        payload.put("salePrice", product.getSalePrice());
        saveEvent("ProductUpdated", "inventory.product-updated", product, payload, userId, correlationId);
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = findActive(id);
        product.setDeletedAt(Instant.now());
        products.save(product);
    }

    private Product findActive(UUID id) {
        return products.findByIdAndDeletedAtIsNull(id).orElseThrow(InventoryException::notFound);
    }

    private String requiredText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw InventoryException.badRequest("Field '%s' must be a non-blank string.".formatted(field));
        }
        return value.textValue().trim();
    }

    private BigDecimal money(JsonNode body, String field, boolean strictlyPositive) {
        JsonNode value = body.get(field);
        if (value == null || !value.isNumber()) {
            throw InventoryException.badRequest("Field '%s' must be a number.".formatted(field));
        }
        BigDecimal amount = value.decimalValue();
        if (amount.signum() < 0 || (strictlyPositive && amount.signum() == 0)) {
            throw InventoryException.badRequest("Field '%s' is outside its allowed range.".formatted(field));
        }
        return amount;
    }

    private void saveEvent(String type, String routingKey, Product product, Map<String, Object> payload,
            UUID userId, UUID correlationId) {
        ProductEventEnvelope envelope = new ProductEventEnvelope(
                UUID.randomUUID(), type, product.getId(), "Product", Instant.now(), 1, payload,
                new ProductEventEnvelope.Metadata(correlationId, null, userId));
        try {
            outbox.save(new OutboxEvent(envelope.eventId(), type, routingKey,
                    objectMapper.writeValueAsString(envelope)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize product event.", exception);
        } catch (DataAccessException exception) {
            throw new InventoryException(503, "SERVICE_UNAVAILABLE", "Event persistence is unavailable.");
        }
    }
}
