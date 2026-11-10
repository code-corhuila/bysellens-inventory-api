package com.bysellens.inventory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InventoryApiPostgresTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String PUBLIC_KEY = createPublicKey();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> postgres.getJdbcUrl());
        registry.add("DB_USER", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
        registry.add("JWT_PUBLIC_KEY_BASE64", () -> PUBLIC_KEY);
        registry.add("INVENTORY_EVENTS_PUBLISHER_ENABLED", () -> "false");
    }

    @Test
    void createsListsUpdatesAndSoftDeletesProductsWithoutChangingInitialStock() throws Exception {
        String code = "IT-" + UUID.randomUUID();
        MvcResult created = mvc.perform(post("/api/v1/inventory/products")
                .with(admin())
                .contentType("application/json")
                .content("""
                        {"code":"%s","name":"Blush","category":"Makeup","shade":"Rose",
                         "purchasePrice":0,"salePrice":12.50,"initialStock":4}
                        """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.initialStock").value(4))
                .andExpect(jsonPath("$.deleted_at").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();

        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/v1/inventory/products/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialStock").value(4));
        mvc.perform(get("/api/v1/inventory/products").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20));

        mvc.perform(patch("/api/v1/inventory/products/{id}", id)
                .with(admin()).contentType("application/json")
                .content("{\"salePrice\":15.25,\"shade\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salePrice").value(15.25))
                .andExpect(jsonPath("$.shade").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.initialStock").value(4));
        mvc.perform(patch("/api/v1/inventory/products/{id}", id)
                .with(admin()).contentType("application/json")
                .content("{\"initialStock\":99}"))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT event_payload -> 'payload' ->> 'initialStock' FROM outbox_events WHERE event_type = 'ProductCreated' AND event_payload -> 'payload' ->> 'code' = ?",
                String.class, code)).isEqualTo("4");
        mvc.perform(delete("/api/v1/inventory/products/{id}", id).with(admin()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/inventory/products/{id}", id).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateCodesAndInvalidCreateFields() throws Exception {
        String code = "IT-" + UUID.randomUUID();
        String valid = "{\"code\":\"%s\",\"name\":\"Blush\",\"category\":\"Makeup\",\"purchasePrice\":0,\"salePrice\":1,\"initialStock\":0}"
                .formatted(code);
        mvc.perform(post("/api/v1/inventory/products").with(admin()).contentType("application/json").content(valid))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/inventory/products").with(admin()).contentType("application/json").content(valid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PRODUCT_CODE_EXISTS"));
        mvc.perform(post("/api/v1/inventory/products").with(admin()).contentType("application/json")
                .content("{\"code\":\"X\",\"name\":\"Y\",\"category\":\"Z\",\"purchasePrice\":0,\"salePrice\":1}"))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("2d7a80c1-5a0e-4e16-a99b-b12f6cfa0012"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String createPublicKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
