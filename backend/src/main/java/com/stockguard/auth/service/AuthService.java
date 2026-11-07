package com.stockguard.auth.service;

import com.stockguard.auth.dto.AuthResponse;
import com.stockguard.auth.dto.LoginRequest;
import com.stockguard.auth.dto.RegisterRequest;
import com.stockguard.auth.dto.UserResponse;
import com.stockguard.common.exception.AccountDisabledException;
import com.stockguard.common.exception.InvalidCredentialsException;
import com.stockguard.common.exception.ResourceNotFoundException;
import com.stockguard.common.exception.UserAlreadyExistsException;
import com.stockguard.security.UserPrincipal;
import com.stockguard.security.jwt.JwtTokenProvider;
import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service encapsulating authentication, user registration, credential verification,
 * and security token issuance.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Registers a new user account.
     * Enforces unique email check, BCrypt password hashing, and strictly sets role to CUSTOMER.
     * Safely handles concurrent duplicate registrations using database constraints.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("Registration rejected: email {} already exists", normalizedEmail);
            throw new UserAlreadyExistsException("An account with email '" + normalizedEmail + "' already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        String firstName = request.getEffectiveFirstName();
        String lastName = request.getEffectiveLastName();

        // PUBLIC REGISTRATION RULE: Always assign CUSTOMER role.
        // Never allow client-supplied ADMIN role assignment.
        User user = new User(
                normalizedEmail,
                hashedPassword,
                firstName,
                lastName,
                Role.CUSTOMER
        );

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate registration detected for email {}: {}", normalizedEmail, ex.getMessage());
            throw new UserAlreadyExistsException("An account with email '" + normalizedEmail + "' already exists");
        }

        log.info("Successfully registered new customer with id {} and email {}", savedUser.getId(), savedUser.getEmail());

        String jwt = tokenProvider.generateTokenFromEmail(
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getId()
        );

        return new AuthResponse(
                jwt,
                "Bearer",
                tokenProvider.getExpirationMs(),
                UserResponse.fromEntity(savedUser)
        );
    }

    /**
     * Authenticates existing user credentials and generates a signed JWT token.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            User user = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

            if (!user.isActive()) {
                log.warn("Authentication rejected: account {} is deactivated", normalizedEmail);
                throw new AccountDisabledException("Your account has been deactivated. Please contact support.");
            }

            String jwt = tokenProvider.generateTokenFromEmail(
                    user.getEmail(),
                    user.getRole(),
                    user.getId()
            );

            log.info("User {} successfully authenticated with role {}", user.getEmail(), user.getRole());

            return new AuthResponse(
                    jwt,
                    "Bearer",
                    tokenProvider.getExpirationMs(),
                    UserResponse.fromEntity(user)
            );
        } catch (BadCredentialsException ex) {
            log.warn("Authentication failed for email {}: invalid credentials", normalizedEmail);
            throw new InvalidCredentialsException("Invalid email or password");
        } catch (DisabledException ex) {
            log.warn("Authentication failed for email {}: account disabled", normalizedEmail);
            throw new AccountDisabledException("Your account has been deactivated. Please contact support.");
        } catch (AuthenticationException ex) {
            log.warn("Authentication failed for email {}: {}", normalizedEmail, ex.getMessage());
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }

    /**
     * Retrieves the profile of the currently authenticated principal.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UserPrincipal principal) {
        if (principal == null) {
            throw new InvalidCredentialsException("Unauthenticated user context");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        return UserResponse.fromEntity(user);
    }
}
