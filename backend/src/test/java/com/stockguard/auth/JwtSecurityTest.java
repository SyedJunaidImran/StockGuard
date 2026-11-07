package com.stockguard.auth;

import com.stockguard.security.jwt.JwtTokenProvider;
import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${stockguard.jwt.secret}")
    private String jwtSecret;

    private User testUser;
    private String validToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = new User(
                "jwt.tester@example.com",
                passwordEncoder.encode("Password123!"),
                "Jwt",
                "Tester",
                Role.CUSTOMER
        );
        testUser = userRepository.save(testUser);
        validToken = jwtTokenProvider.generateTokenFromEmail(testUser.getEmail(), testUser.getRole(), testUser.getId());
    }

    @Test
    @DisplayName("Valid JWT token should allow access to protected endpoint")
    void testValidJwtAllowsAccess() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is("jwt.tester@example.com")));
    }

    @Test
    @DisplayName("Expired JWT token should be rejected with 401 Unauthorized")
    void testExpiredJwtRejected() throws Exception {
        // Generate an expired token using the same signing key
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);

        String expiredToken = Jwts.builder()
                .subject(testUser.getEmail())
                .claim("userId", testUser.getId())
                .claim("role", testUser.getRole().name())
                .issuedAt(Date.from(past.minus(1, ChronoUnit.HOURS)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("Malformed JWT token string should be rejected with 401 Unauthorized")
    void testMalformedJwtRejected() throws Exception {
        String malformedToken = "not.a.valid.jwt.structure.token";

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + malformedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("JWT signed with untrusted/different secret key should be rejected with 401 Unauthorized")
    void testInvalidSignatureJwtRejected() throws Exception {
        // Token signed with completely different key
        SecretKey rogueKey = Keys.hmacShaKeyFor(
                "an_unauthorized_external_key_that_is_at_least_256_bits_long!".getBytes(StandardCharsets.UTF_8)
        );

        String forgedToken = Jwts.builder()
                .subject(testUser.getEmail())
                .claim("userId", testUser.getId())
                .claim("role", "ADMIN") // Attacker attempting role elevation with forged signature
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(rogueKey)
                .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("Protected endpoint accessed without Authorization header should return 401 Unauthorized")
    void testAnonymousAccessToProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Full authentication is required to access this resource")));
    }
}
