package com.stockguard.security;

import com.stockguard.security.jwt.JwtTokenProvider;
import com.stockguard.user.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("JwtTokenProvider should generate valid token and correctly parse claims")
    void testGenerateAndParseClaims() {
        String email = "sarah.connor@example.com";
        Role role = Role.ADMIN;
        Long userId = 42L;

        String token = jwtTokenProvider.generateTokenFromEmail(email, role, userId);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(email, jwtTokenProvider.getEmailFromToken(token));
        assertEquals(role, jwtTokenProvider.getRoleFromToken(token));
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("JwtTokenProvider should validate and reject null or empty tokens")
    void testValidateNullOrEmptyToken() {
        assertFalse(jwtTokenProvider.validateToken(null));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken("   "));
    }

    @Test
    @DisplayName("JwtTokenProvider should reject corrupted or altered tokens")
    void testValidateTamperedToken() {
        String token = jwtTokenProvider.generateTokenFromEmail("test@example.com", Role.CUSTOMER, 1L);
        // Alter payload
        String tampered = token.substring(0, token.length() - 5) + "ABCDE";

        assertFalse(jwtTokenProvider.validateToken(tampered));
    }
}
