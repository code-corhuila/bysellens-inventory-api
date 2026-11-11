package com.bysellens.inventory;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class })
class InventorySecurityTest {
    private static final String PUBLIC_KEY = createPublicKey();

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ProductService products;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key-base64", () -> PUBLIC_KEY);
    }

    @BeforeEach
    void setUp() {
        when(products.list(1, 20)).thenReturn(new ProductPageResponse(
                List.of(), new ProductPageResponse.Meta(1, 20, 0, 0)));
    }

    @Test
    void rejectsRequestsWithoutBearerToken() throws Exception {
        mvc.perform(get("/api/v1/inventory/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAuthenticatedRequestsWithoutAdminRole() throws Exception {
        mvc.perform(get("/api/v1/inventory/products")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminRequests() throws Exception {
        mvc.perform(get("/api/v1/inventory/products")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    private static String createPublicKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
