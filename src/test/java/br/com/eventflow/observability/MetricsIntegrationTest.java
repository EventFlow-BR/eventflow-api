package br.com.eventflow.observability;

import br.com.eventflow.testinfra.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldRegisterJvmMemoryMetrics() {
        var meter =
                meterRegistry.find(
                        "jvm.memory.used"
                ).meter();

        assertNotNull(meter);
    }

    @Test
    void shouldRegisterProcessUptimeMetric() {
        var gauge =
                meterRegistry.find(
                        "process.uptime"
                ).gauge();

        assertNotNull(gauge);

        assertTrue(
                gauge.value() >= 0
        );
    }

    @Test
    void shouldRegisterSystemCpuMetric() {
        var gauge =
                meterRegistry.find(
                        "system.cpu.count"
                ).gauge();

        assertNotNull(gauge);

        assertTrue(
                gauge.value() > 0
        );
    }
}