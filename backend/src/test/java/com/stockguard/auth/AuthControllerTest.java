package com.stockguard.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockguard.auth.dto.LoginRequest;
import com.stockguard.auth.dto.RegisterRequest;
import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should successfully register a new customer and return JWT")
    void testSuccessfulRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("alice.customer@example.com");
        request.setPassword("SecurePassword123!");
        request.setName("Alice Cooper");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("successfully")))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.user.email", is("alice.customer@example.com")))
                .andExpect(jsonPath("$.data.user.firstName", is("Alice")))
                .andExpect(jsonPath("$.data.user.lastName", is("Cooper")))
                .andExpect(jsonPath("$.data.user.role", is("CUSTOMER")))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

        // Verify database persistence and BCrypt hashing
        Optional<User> savedUser = userRepository.findByEmailIgnoreCase("alice.customer@example.com");
        assertTrue(savedUser.isPresent(), "User should be persisted in database");
        assertNotEquals("SecurePassword123!", savedUser.get().getPasswordHash(), "Password must not be stored in plaintext");
        assertTrue(passwordEncoder.matches("SecurePassword123!", savedUser.get().getPasswordHash()), "BCrypt password verification should succeed");
        assertEquals(Role.CUSTOMER, savedUser.get().getRole(), "Role must be CUSTOMER");
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should strictly ignore client-supplied ADMIN role and assign CUSTOMER")
    void testRegisterCannotElevateToAdmin() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("hacker@example.com");
        request.setPassword("HackerPass123!");
        request.setName("Malicious User");
        request.setRole("ADMIN"); // Attacker attempts to assign ADMIN role

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role", is("CUSTOMER")));

        // Verify DB record
        User user = userRepository.findByEmailIgnoreCase("hacker@example.com").orElseThrow();
        assertEquals(Role.CUSTOMER, user.getRole(), "Role must remain CUSTOMER regardless of client payload");
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should reject duplicate email registrations with 409 Conflict")
    void testDuplicateEmailRejection() throws Exception {
        RegisterRequest initialRequest = new RegisterRequest();
        initialRequest.setEmail("bob.unique@example.com");
        initialRequest.setPassword("Password123!");
        initialRequest.setName("Bob Smith");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isCreated());

        // Attempt second registration with same email
        RegisterRequest duplicateRequest = new RegisterRequest();
        duplicateRequest.setEmail("bob.unique@example.com");
        duplicateRequest.setPassword("DifferentPassword999!");
        duplicateRequest.setName("Bob Clone");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should authenticate valid user credentials and return JWT")
    void testSuccessfulLogin() throws Exception {
        // Register user
        User user = new User(
                "charlie@example.com",
                passwordEncoder.encode("ValidSecret123!"),
                "Charlie",
                "Brown",
                Role.CUSTOMER
        );
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("charlie@example.com", "ValidSecret123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is("charlie@example.com")))
                .andExpect(jsonPath("$.data.user.role", is("CUSTOMER")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with wrong password should return 401 Unauthorized")
    void testLoginWithWrongPassword() throws Exception {
        User user = new User(
                "david@example.com",
                passwordEncoder.encode("CorrectPassword123!"),
                "David",
                "Miller",
                Role.CUSTOMER
        );
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("david@example.com", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with non-existent email should return 401 Unauthorized")
    void testLoginWithNonExistentEmail() throws Exception {
        LoginRequest loginRequest = new LoginRequest("unknown@example.com", "SomePassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me should return authenticated user profile")
    void testGetCurrentUserProfile() throws Exception {
        // Register and get token
        RegisterRequest registerRequest = new RegisterRequest("profile.test@example.com", "StrongPassword123!", "Profile", "User");
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = registerResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).path("data").path("token").asText();

        // Access /api/v1/auth/me with Bearer token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is("profile.test@example.com")))
                .andExpect(jsonPath("$.data.firstName", is("Profile")))
                .andExpect(jsonPath("$.data.lastName", is("User")))
                .andExpect(jsonPath("$.data.role", is("CUSTOMER")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me without token should return 401 Unauthorized")
    void testGetCurrentUserUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with invalid data should return 400 Bad Request")
    void testRegisterWithInvalidData() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setEmail("not-an-email");
        invalidRequest.setPassword("short"); // under 8 chars

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.validationErrors.email", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.password", notNullValue()));
    }
}
