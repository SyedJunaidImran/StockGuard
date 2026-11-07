package com.stockguard.auth.controller;

import com.stockguard.common.dto.ApiResponse;
import com.stockguard.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller providing role-protected endpoints to verify and test
 * role-based access control (CUSTOMER vs ADMIN).
 */
@RestController
@RequestMapping("/api/v1/test")
public class RbacTestController {

    @GetMapping("/customer-only")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomerResource(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Access granted to CUSTOMER resource",
                Map.of(
                        "resource", "customer-area",
                        "user", principal.getUsername(),
                        "role", principal.getRole().name()
                )
        ));
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminResource(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Access granted to ADMIN resource",
                Map.of(
                        "resource", "admin-area",
                        "user", principal.getUsername(),
                        "role", principal.getRole().name()
                )
        ));
    }

    @GetMapping("/authenticated")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuthenticatedResource(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Access granted to authenticated user",
                Map.of(
                        "user", principal.getUsername(),
                        "role", principal.getRole().name()
                )
        ));
    }
}
