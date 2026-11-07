package com.stockguard.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/health should return 200 OK and valid health payload")
    void healthEndpointShouldReturnOkAndHealthData() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.service", is("StockGuard Backend")))
                .andExpect(jsonPath("$.data.status", anyOf(is("UP"), is("DEGRADED"))))
                .andExpect(jsonPath("$.data.database", anyOf(is("CONNECTED"), is("DISCONNECTED"), is("UNAVAILABLE"))))
                .andExpect(jsonPath("$.data.environment", is("test")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}
