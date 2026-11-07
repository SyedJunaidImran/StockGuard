package com.stockguard.common.dto;

import java.time.Instant;

/**
 * Health status payload for GET /api/v1/health endpoint.
 */
public class HealthResponse {

    private String status;
    private String service;
    private String version;
    private String environment;
    private String database;
    private Instant timestamp;
    private long uptimeSeconds;

    public HealthResponse() {
        this.timestamp = Instant.now();
    }

    public HealthResponse(String status, String service, String version, String environment, String database, long uptimeSeconds) {
        this.status = status;
        this.service = service;
        this.version = version;
        this.environment = environment;
        this.database = database;
        this.uptimeSeconds = uptimeSeconds;
        this.timestamp = Instant.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }
}
