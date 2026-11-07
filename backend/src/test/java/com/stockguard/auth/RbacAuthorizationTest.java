package com.stockguard.auth;

import com.stockguard.security.jwt.JwtTokenProvider;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Create Customer user
        User customer = new User(
                "customer.rbac@example.com",
                passwordEncoder.encode("Password123!"),
                "Regular",
                "Customer",
                Role.CUSTOMER
        );
        User savedCustomer = userRepository.save(customer);
        customerToken = jwtTokenProvider.generateTokenFromEmail(
                savedCustomer.getEmail(),
                savedCustomer.getRole(),
                savedCustomer.getId()
        );

        // Create Admin user
        User admin = new User(
                "admin.rbac@example.com",
                passwordEncoder.encode("AdminPassword123!"),
                "System",
                "Admin",
                Role.ADMIN
        );
        User savedAdmin = userRepository.save(admin);
        adminToken = jwtTokenProvider.generateTokenFromEmail(
                savedAdmin.getEmail(),
                savedAdmin.getRole(),
                savedAdmin.getId()
        );
    }

    @Test
    @DisplayName("Anonymous access to customer-only endpoint should return 401 Unauthorized")
    void testAnonymousAccessCustomerEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("Anonymous access to admin-only endpoint should return 401 Unauthorized")
    void testAnonymousAccessAdminEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("CUSTOMER role should successfully access customer-only endpoint (200 OK)")
    void testCustomerCanAccessCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer-only")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.role", is("CUSTOMER")));
    }

    @Test
    @DisplayName("CUSTOMER role accessing admin-only endpoint should return 403 Forbidden")
    void testCustomerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", is("Access denied: you do not have permission to access this resource")));
    }

    @Test
    @DisplayName("ADMIN role should successfully access admin-only endpoint (200 OK)")
    void testAdminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")));
    }

    @Test
    @DisplayName("Both CUSTOMER and ADMIN should access general authenticated endpoint")
    void testBothRolesCanAccessAuthenticatedEndpoint() throws Exception {
        // Customer
        mockMvc.perform(get("/api/v1/test/authenticated")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("CUSTOMER")));

        // Admin
        mockMvc.perform(get("/api/v1/test/authenticated")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("ADMIN")));
    }
}
