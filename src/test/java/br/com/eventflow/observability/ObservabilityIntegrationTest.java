package br.com.eventflow.observability;

import br.com.eventflow.testinfra.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ObservabilityIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void shouldExposeOnlyOverallHealthStatusPublicly()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/health")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("UP")
                )
                .andExpect(
                        jsonPath("$.components")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.details")
                                .doesNotExist()
                );
    }

    @Test
    void shouldReportDatabaseAsHealthy() {
        assertHealthContributorIsUp(
                "db"
        );
    }

    @Test
    void shouldReportRabbitMqAsHealthy() {
        assertHealthContributorIsUp(
                "rabbit"
        );
    }

    @Test
    void shouldReportRedisAsHealthy() {
        assertHealthContributorIsUp(
                "redis"
        );
    }

    private void assertHealthContributorIsUp(
            String contributor
    ) {
        HealthDescriptor health =
                healthEndpoint.healthForPath(
                        contributor
                );

        assertNotNull(
                health,
                "Expected health contributor: "
                        + contributor
        );

        assertEquals(
                Status.UP,
                health.getStatus(),
                "Expected "
                        + contributor
                        + " to be healthy"
        );
    }
}