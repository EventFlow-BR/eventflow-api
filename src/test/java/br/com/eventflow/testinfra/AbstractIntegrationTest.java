package br.com.eventflow.testinfra;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
        properties = {
                "app.rabbitmq.registration-consumer.enabled=false",
                "app.jwt.secret=test-secret-key-for-testcontainers-integration-tests-123456789"
        }
)
public abstract class AbstractIntegrationTest {
}
