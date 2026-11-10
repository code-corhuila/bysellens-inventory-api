package com.bysellens.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtDecoderTest {
    private static KeyPair keyPair;
    private static JwtEncoder encoder;
    private static JwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        RSAKey signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        decoder = new SecurityConfig().jwtDecoder(publicKey, "bysellens-auth", "bysellens-api");
    }

    @Test
    void acceptsValidRs256TokenWithIssuerAndAudience() {
        String token = token("bysellens-auth", List.of("bysellens-api"), SignatureAlgorithm.RS256);
        assertEquals("bysellens-auth", decoder.decode(token).getClaimAsString("iss"));
    }

    @Test
    void rejectsWrongIssuer() {
        assertThrows(JwtException.class,
                () -> decoder.decode(token("other-issuer", List.of("bysellens-api"), SignatureAlgorithm.RS256)));
    }

    @Test
    void rejectsWrongAudience() {
        assertThrows(JwtException.class,
                () -> decoder.decode(token("bysellens-auth", List.of("other-audience"), SignatureAlgorithm.RS256)));
    }

    @Test
    void rejectsWrongSignatureAlgorithm() {
        assertThrows(JwtException.class,
                () -> decoder.decode(token("bysellens-auth", List.of("bysellens-api"), SignatureAlgorithm.RS512)));
    }

    private String token(String issuer, List<String> audience, SignatureAlgorithm algorithm) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("2d7a80c1-5a0e-4e16-a99b-b12f6cfa0012")
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .claim("role", "ADMIN")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }
}
