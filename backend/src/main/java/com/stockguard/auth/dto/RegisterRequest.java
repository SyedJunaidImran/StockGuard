package com.stockguard.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

/**
 * Registration request payload.
 * Accepts full name or individual first/last name, email, and password.
 * Note: Any client-supplied 'role' field is strictly discarded to prevent privilege escalation.
 */
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters long")
    private String password;

    @Size(max = 200, message = "Name cannot exceed 200 characters")
    private String name;

    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    // Intentionally ignored in business logic to prevent privilege escalation attacks
    private String role;

    public RegisterRequest() {
    }

    public RegisterRequest(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public RegisterRequest(String email, String password, String firstName, String lastName) {
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @AssertTrue(message = "Name or first name is required")
    public boolean isNameProvided() {
        return StringUtils.hasText(name) || StringUtils.hasText(firstName);
    }

    public String getEffectiveFirstName() {
        if (StringUtils.hasText(firstName)) {
            return firstName.trim();
        }
        if (StringUtils.hasText(name)) {
            String trimmed = name.trim();
            int spaceIndex = trimmed.indexOf(' ');
            return spaceIndex > 0 ? trimmed.substring(0, spaceIndex).trim() : trimmed;
        }
        return "Customer";
    }

    public String getEffectiveLastName() {
        if (StringUtils.hasText(lastName)) {
            return lastName.trim();
        }
        if (StringUtils.hasText(name)) {
            String trimmed = name.trim();
            int spaceIndex = trimmed.indexOf(' ');
            return spaceIndex > 0 ? trimmed.substring(spaceIndex + 1).trim() : "";
        }
        return "";
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
