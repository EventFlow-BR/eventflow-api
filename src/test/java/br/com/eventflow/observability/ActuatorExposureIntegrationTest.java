package br.com.eventflow.observability;

import br.com.eventflow.testinfra.AbstractIntegrationTest;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorExposureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebEndpointsSupplier webEndpointsSupplier;

    @Autowired
    private PrometheusMeterRegistry prometheusMeterRegistry;

    @Test
    void shouldExposeOnlyHealthByDefaultWhileRetainingPrometheusRegistry() {
        assertThat(webEndpointsSupplier.getEndpoints())
                .extracting(endpoint -> endpoint.getEndpointId().toString())
                .containsExactly("health");
        assertThat(prometheusMeterRegistry.getMeters()).isNotEmpty();
    }
}
