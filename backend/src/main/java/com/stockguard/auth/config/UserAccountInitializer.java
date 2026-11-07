package com.stockguard.auth.config;

import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * ApplicationRunner for secure user account initialization and maintenance.
 * <ul>
 *   <li>Idempotently seeds the pre-configured CUSTOMER demo account for evaluation/recruiter testing.</li>
 *   <li>Securely provisions or updates the administrator account if configured via
 *       server-side environment variables, without exposing credentials in frontend code or logs.</li>
 *   <li>Fails startup if incomplete administrator environment variables are configured.</li>
 *   <li>Prevents silent elevation of existing CUSTOMER accounts to ADMIN.</li>
 *   <li>Preserves existing database accounts and never resets demo credentials across restarts.</li>
 * </ul>
 */
@Component
@Order(5)
public class UserAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${stockguard.admin.email:}")
    private String adminEmail;

    @Value("${stockguard.admin.password:}")
    private String adminPassword;

    @Value("${stockguard.admin.first-name:System}")
    private String adminFirstName;

    @Value("${stockguard.admin.last-name:Admin}")
    private String adminLastName;

    @Value("${stockguard.demo.customer.email:customer@stockguard.io}")
    private String demoCustomerEmail;

    @Value("${stockguard.demo.customer.password:Customer123!}")
    private String demoCustomerPassword;

    @Value("${stockguard.demo.customer.enabled:true}")
    private boolean demoCustomerEnabled;

    public UserAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initializeDemoCustomerAccount();
        configureAdminAccountFromEnvironment();
    }

    /**
     * Ensures the demo customer account exists for candidate/recruiter evaluation.
     * Safe and idempotent: if the account already exists, it is untouched.
     */
    private void initializeDemoCustomerAccount() {
        if (!demoCustomerEnabled || !StringUtils.hasText(demoCustomerEmail)) {
            return;
        }

        String normalizedEmail = demoCustomerEmail.trim().toLowerCase();
        try {
            if (!userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                String encodedPassword = passwordEncoder.encode(
                        StringUtils.hasText(demoCustomerPassword) ? demoCustomerPassword : "Customer123!"
                );
                User demoCustomer = new User(
                        normalizedEmail,
                        encodedPassword,
                        "Demo",
                        "Customer",
                        Role.CUSTOMER
                );
                userRepository.save(demoCustomer);
                log.info("Initialized pre-seeded demo customer account [{}]", normalizedEmail);
            } else {
                log.debug("Demo customer account [{}] already exists; preserving existing state.", normalizedEmail);
            }
        } catch (Exception ex) {
            log.error("Failed to initialize demo customer account: {}", ex.getMessage());
        }
    }

    /**
     * Securely provisions or rotates password for the administrator account from environment variables.
     * <ul>
     *   <li>Fails startup if only one of email or password is supplied.</li>
     *   <li>Fails startup if configured email matches an existing CUSTOMER account (prevents silent privilege escalation).</li>
     *   <li>Rotates password via BCrypt if an existing ADMIN account matches.</li>
     *   <li>Provisions a new ADMIN account with BCrypt password if no account exists.</li>
     * </ul>
     */
    private void configureAdminAccountFromEnvironment() {
        boolean hasEmail = StringUtils.hasText(adminEmail);
        boolean hasPassword = StringUtils.hasText(adminPassword);

        // Fail startup if partial configuration is provided
        if (hasEmail ^ hasPassword) {
            throw new IllegalStateException(
                    "Invalid administrator configuration: Both STOCKGUARD_ADMIN_EMAIL and STOCKGUARD_ADMIN_PASSWORD " +
                    "must be provided together when configuring admin access via environment variables."
            );
        }

        // If neither is configured, preserve existing database accounts untouched
        if (!hasEmail) {
            log.debug("No admin environment credentials supplied; preserving existing database admin configuration.");
            return;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();
        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (existingUserOpt.isPresent()) {
            User user = existingUserOpt.get();

            // Guard against silent customer elevation
            if (user.getRole() != Role.ADMIN) {
                throw new IllegalStateException(
                        "Security violation: Configured STOCKGUARD_ADMIN_EMAIL matches an existing CUSTOMER account [" +
                        normalizedEmail + "]. Customer accounts cannot be silently elevated to ADMIN via environment configuration."
                );
            }

            // Existing ADMIN account: rotate password securely using BCrypt
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            user.setActive(true);
            if (StringUtils.hasText(adminFirstName)) {
                user.setFirstName(adminFirstName.trim());
            }
            if (StringUtils.hasText(adminLastName)) {
                user.setLastName(adminLastName.trim());
            }
            userRepository.save(user);
            log.info("Administrator account [{}] successfully synchronized from environment configuration (password rotated).", normalizedEmail);
        } else {
            // Provision new ADMIN account
            User newAdmin = new User(
                    normalizedEmail,
                    passwordEncoder.encode(adminPassword),
                    StringUtils.hasText(adminFirstName) ? adminFirstName.trim() : "System",
                    StringUtils.hasText(adminLastName) ? adminLastName.trim() : "Admin",
                    Role.ADMIN
            );
            userRepository.save(newAdmin);
            log.info("Administrator account [{}] successfully provisioned from environment configuration.", normalizedEmail);
        }
    }
}
