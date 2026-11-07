package com.stockguard.auth;

import com.stockguard.auth.config.UserAccountInitializer;
import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments args;

    private UserAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new UserAccountInitializer(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "demoCustomerEmail", "customer@stockguard.io");
        ReflectionTestUtils.setField(initializer, "demoCustomerPassword", "Customer123!");
        ReflectionTestUtils.setField(initializer, "demoCustomerEnabled", true);
    }

    @Test
    @DisplayName("Seeds demo customer account when it does not exist")
    void testSeedsDemoCustomerWhenMissing() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(false);
        when(passwordEncoder.encode("Customer123!")).thenReturn("hashed-customer-pass");

        initializer.run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("customer@stockguard.io", saved.getEmail());
        assertEquals("hashed-customer-pass", saved.getPasswordHash());
        assertEquals(Role.CUSTOMER, saved.getRole());
        assertTrue(saved.isActive());
    }

    @Test
    @DisplayName("Does not overwrite demo customer account when it already exists")
    void testPreservesExistingDemoCustomer() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(true);

        initializer.run(args);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Provisions new admin account when admin environment variables are configured")
    void testProvisionsNewAdminFromEnvironment() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "superadmin@stockguard.io");
        ReflectionTestUtils.setField(initializer, "adminPassword", "SuperSecret123!");
        ReflectionTestUtils.setField(initializer, "adminFirstName", "Lead");
        ReflectionTestUtils.setField(initializer, "adminLastName", "Operator");

        when(userRepository.findByEmailIgnoreCase("superadmin@stockguard.io")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SuperSecret123!")).thenReturn("hashed-admin-pass");

        initializer.run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedAdmin = captor.getValue();
        assertEquals("superadmin@stockguard.io", savedAdmin.getEmail());
        assertEquals("hashed-admin-pass", savedAdmin.getPasswordHash());
        assertEquals("Lead", savedAdmin.getFirstName());
        assertEquals("Operator", savedAdmin.getLastName());
        assertEquals(Role.ADMIN, savedAdmin.getRole());
        assertTrue(savedAdmin.isActive());
    }

    @Test
    @DisplayName("Rotates password for existing ADMIN user securely using BCrypt")
    void testRotatesPasswordForExistingAdmin() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@stockguard.io");
        ReflectionTestUtils.setField(initializer, "adminPassword", "RotatedSecret123!");
        ReflectionTestUtils.setField(initializer, "adminFirstName", "UpdatedFirst");
        ReflectionTestUtils.setField(initializer, "adminLastName", "UpdatedLast");

        User existingAdmin = new User("admin@stockguard.io", "old-hash", "OldFirst", "OldLast", Role.ADMIN);
        when(userRepository.findByEmailIgnoreCase("admin@stockguard.io")).thenReturn(Optional.of(existingAdmin));
        when(passwordEncoder.encode("RotatedSecret123!")).thenReturn("new-rotated-hash");

        initializer.run(args);

        verify(userRepository).save(existingAdmin);
        assertEquals("new-rotated-hash", existingAdmin.getPasswordHash());
        assertEquals(Role.ADMIN, existingAdmin.getRole());
        assertEquals("UpdatedFirst", existingAdmin.getFirstName());
        assertEquals("UpdatedLast", existingAdmin.getLastName());
        assertTrue(existingAdmin.isActive());
    }

    @Test
    @DisplayName("Fails startup safely if STOCKGUARD_ADMIN_EMAIL matches an existing CUSTOMER account")
    void testFailsWhenEmailMatchesExistingCustomer() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "customer.vip@stockguard.io");
        ReflectionTestUtils.setField(initializer, "adminPassword", "TryToElevate123!");

        User existingCustomer = new User("customer.vip@stockguard.io", "cust-hash", "Vip", "Customer", Role.CUSTOMER);
        when(userRepository.findByEmailIgnoreCase("customer.vip@stockguard.io")).thenReturn(Optional.of(existingCustomer));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> initializer.run(args));
        assertTrue(ex.getMessage().contains("Security violation: Configured STOCKGUARD_ADMIN_EMAIL matches an existing CUSTOMER account"));
        verify(userRepository, never()).save(existingCustomer);
    }

    @Test
    @DisplayName("Fails startup safely if only STOCKGUARD_ADMIN_EMAIL is configured")
    void testFailsWhenOnlyEmailConfigured() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@stockguard.io");
        ReflectionTestUtils.setField(initializer, "adminPassword", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> initializer.run(args));
        assertTrue(ex.getMessage().contains("Invalid administrator configuration: Both STOCKGUARD_ADMIN_EMAIL and STOCKGUARD_ADMIN_PASSWORD must be provided together"));
    }

    @Test
    @DisplayName("Fails startup safely if only STOCKGUARD_ADMIN_PASSWORD is configured")
    void testFailsWhenOnlyPasswordConfigured() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "");
        ReflectionTestUtils.setField(initializer, "adminPassword", "OnlyPasswordProvided123!");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> initializer.run(args));
        assertTrue(ex.getMessage().contains("Invalid administrator configuration: Both STOCKGUARD_ADMIN_EMAIL and STOCKGUARD_ADMIN_PASSWORD must be provided together"));
    }

    @Test
    @DisplayName("Does not touch admin accounts when environment variables are omitted")
    void testDoesNothingWhenAdminEnvNotProvided() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "");
        ReflectionTestUtils.setField(initializer, "adminPassword", "");

        initializer.run(args);

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}
