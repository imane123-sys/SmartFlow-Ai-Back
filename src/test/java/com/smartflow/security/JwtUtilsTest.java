package com.smartflow.security;

import com.smartflow.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expirationMs = 3600000; // 1h

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", expirationMs);
    }

    @Test
    void generateTokenAndValidateShouldSucceed() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@smartflow.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ADMIN)
                .active(true)
                .authorities(Collections.emptyList())
                .build();

        String token = jwtUtils.generateTokenFromUserPrincipal(userPrincipal);
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));

        String username = jwtUtils.getUsernameFromToken(token);
        assertEquals("test@smartflow.com", username);
    }

    @Test
    void validateTokenWithInvalidSignatureShouldFail() {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHNtYXJ0Zmxvdy5jb20ifQ.invalidSignature";
        assertFalse(jwtUtils.validateToken(invalidToken));
    }

    @Test
    void validateTokenWithMalformedTokenShouldFail() {
        assertFalse(jwtUtils.validateToken("not-a-valid-jwt"));
    }

    @Test
    void validateTokenWithExpiredTokenShouldFail() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1000L); // already expired

        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("expired@smartflow.com")
                .firstName("Expired")
                .lastName("User")
                .role(Role.EMPLOYEE)
                .active(true)
                .authorities(Collections.emptyList())
                .build();

        String token = jwtUtils.generateTokenFromUserPrincipal(userPrincipal);
        assertFalse(jwtUtils.validateToken(token));
    }
}
