package com.bysellens.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ProductRepository products;
    @Mock
    private OutboxEventRepository outbox;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, outbox, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createStoresInitialStockAndWritesProductCreatedToOutbox() throws Exception {
        when(products.existsByCode("BLUSH-01")).thenReturn(false);
        when(products.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
            product.onCreate();
            return product;
        });

        ProductResponse response = service.create(
                new CreateProductRequest(" BLUSH-01 ", "Blush", "Makeup", null,
                        new BigDecimal("0"), new BigDecimal("12.50"), 0),
                UUID.randomUUID(), UUID.randomUUID());

        assertEquals(0, response.initialStock());
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(eventCaptor.capture());
        var payload = new ObjectMapper().findAndRegisterModules().readTree(eventCaptor.getValue().getEventPayload());
        assertEquals("ProductCreated", payload.path("eventType").asText());
        assertEquals(0, payload.path("payload").path("initialStock").asInt());
        assertEquals(response.id().toString(), payload.path("aggregateId").asText());
    }

    @Test
    void patchRejectsInitialStockBecauseItIsImmutable() {
        InventoryException exception = assertThrows(InventoryException.class,
                () -> service.update(UUID.randomUUID(), new ObjectMapper().readTree("{\"initialStock\":9}"),
                        UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(400, exception.getStatus());
        verify(products, never()).save(any());
        verify(outbox, never()).save(any());
    }
}
