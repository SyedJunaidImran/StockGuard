package com.stockguard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StockGuardApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context initializes successfully
    }
}
