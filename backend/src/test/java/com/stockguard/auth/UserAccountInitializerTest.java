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
    @DisplayName("Updates existing user to admin and updates password when matching email is found")
    void testUpdatesExistingUserToAdmin() {
        when(userRepository.existsByEmailIgnoreCase("customer@stockguard.io")).thenReturn(true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "existing@stockguard.io");
        ReflectionTestUtils.setField(initializer, "adminPassword", "RotatedSecret123!");
        ReflectionTestUtils.setField(initializer, "adminFirstName", "UpdatedFirst");
        ReflectionTestUtils.setField(initializer, "adminLastName", "UpdatedLast");

        User existingUser = new User("existing@stockguard.io", "old-hash", "OldFirst", "OldLast", Role.CUSTOMER);
        when(userRepository.findByEmailIgnoreCase("existing@stockguard.io")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("RotatedSecret123!")).thenReturn("new-rotated-hash");

        initializer.run(args);

        verify(userRepository).save(existingUser);
        assertEquals("new-rotated-hash", existingUser.getPasswordHash());
        assertEquals(Role.ADMIN, existingUser.getRole());
        assertEquals("UpdatedFirst", existingUser.getFirstName());
        assertEquals("UpdatedLast", existingUser.getLastName());
        assertTrue(existingUser.isActive());
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

