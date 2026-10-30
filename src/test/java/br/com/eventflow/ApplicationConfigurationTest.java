package br.com.eventflow;

import br.com.eventflow.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of())
            ))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(JwtService.class);

    @Test
    void shouldRequireJwtSecretOutsideLocalProfile() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT_SECRET");
        });
    }

    @Test
    void shouldUseSecureDefaultsWithExternallySuppliedJwtSecret() {
        contextRunner.withPropertyValues(
                "JWT_SECRET=configuration-test-key-with-at-least-32-bytes"
        ).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtService.class);
            var environment = context.getEnvironment();
            assertThat(environment.getActiveProfiles()).doesNotContain("local");
            assertThat(environment.getProperty("app.auth.cookie-secure", Boolean.class)).isTrue();
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health");
            assertThat(environment.getProperty("management.endpoint.health.show-details"))
                    .isEqualTo("never");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
            assertThat(environment.getProperty("spring.datasource.url")).isNull();
        });
    }

    @Test
    void shouldKeepDevelopmentOverridesOnlyInExplicitLocalProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=local").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtService.class);
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("app.auth.cookie-secure", Boolean.class)).isFalse();
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health,prometheus");
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5433/eventflow");
            assertThat(environment.getProperty("spring.rabbitmq.username")).isEqualTo("eventflow");
        });
    }
}
