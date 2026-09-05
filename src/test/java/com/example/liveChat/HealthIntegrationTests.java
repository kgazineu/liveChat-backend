package com.example.liveChat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private HealthContributorRegistry healthContributors;

    @Test
    void databaseFailureReturnsServiceUnavailableWithoutDetails() throws Exception {
        var databaseHealth = healthContributors.unregisterContributor("db");
        org.assertj.core.api.Assertions.assertThat(databaseHealth).isNotNull();
        try {
            healthContributors.registerContributor("db", (HealthIndicator) () ->
                    Health.down().withDetail("database", "private details").build());
            mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                    .andExpect(content().string("{\"status\":\"DOWN\"}"));
        } finally {
            healthContributors.unregisterContributor("db");
            healthContributors.registerContributor("db", databaseHealth);
        }
    }
}
