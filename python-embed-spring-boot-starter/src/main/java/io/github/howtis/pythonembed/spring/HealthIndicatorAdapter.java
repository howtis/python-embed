package io.github.howtis.pythonembed.spring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Map;

/**
 * Adapts a {@link PythonEmbedHealthIndicator} to Spring Boot 3.x
 * {@code org.springframework.boot.actuate.health.HealthIndicator}.
 *
 * <p>Registered by {@link PythonEmbedAutoConfiguration.HealthIndicatorConfigurationV3}
 * when Boot 3.x Actuator is on the classpath.
 */
final class HealthIndicatorAdapter implements HealthIndicator {

    private final PythonEmbedHealthIndicator delegate;

    HealthIndicatorAdapter(PythonEmbedHealthIndicator delegate) {
        this.delegate = delegate;
    }

    @Override
    public Health health() {
        PythonEmbedHealthIndicator.HealthData data = delegate.health();
        if (data.up()) {
            Health.Builder builder = Health.up();
            for (Map.Entry<String, Object> entry : data.details().entrySet()) {
                builder.withDetail(entry.getKey(), entry.getValue());
            }
            return builder.build();
        }
        Health.Builder builder = Health.down();
        if (data.error() != null) {
            builder.withException(data.error());
        }
        for (Map.Entry<String, Object> entry : data.details().entrySet()) {
            builder.withDetail(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }
}
