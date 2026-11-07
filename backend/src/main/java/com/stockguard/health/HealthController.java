package com.stockguard.health;

import com.stockguard.common.dto.ApiResponse;
import com.stockguard.common.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Health check controller providing operational health status, database connectivity,
 * version, and uptime for cloud probes and the React frontend.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;

    @Value("${stockguard.version:1.0.0}")
    private String version;

    @Value("${stockguard.environment:development}")
    private String environment;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth() {
        String dbStatus = checkDatabaseHealth();
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        String overallStatus = "CONNECTED".equals(dbStatus) ? "UP" : "DEGRADED";

        HealthResponse health = new HealthResponse(
                overallStatus,
                "StockGuard Backend",
                version,
                environment,
                dbStatus,
                uptimeSeconds
        );

        return ResponseEntity.ok(ApiResponse.success("Service is operational", health));
    }

    private String checkDatabaseHealth() {
        if (dataSource == null) {
            return "NOT_CONFIGURED";
        }
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return "CONNECTED";
            }
            return "UNAVAILABLE";
        } catch (SQLException ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return "DISCONNECTED";
        }
    }
}
