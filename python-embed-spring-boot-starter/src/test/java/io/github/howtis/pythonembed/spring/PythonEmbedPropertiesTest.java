package io.github.howtis.pythonembed.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonEmbedPropertiesTest {

    @Test
    void defaultModeIsSingle() {
        var props = bind(Map.of());
        assertThat(props.getMode()).isEqualTo(PythonEmbedProperties.Mode.SINGLE);
    }

    @Test
    void explicitMode() {
        var props = bind(Map.of("python-embed.mode", "POOL"));
        assertThat(props.getMode()).isEqualTo(PythonEmbedProperties.Mode.POOL);
    }

    @Test
    void venvPathOverride() {
        var props = bind(Map.of("python-embed.venv-path", "/opt/custom-venv"));
        assertThat(props.getVenvPath()).isEqualTo("/opt/custom-venv");
    }

    @Test
    void poolDefaults() {
        var props = bind(Map.of());
        var pool = props.getPool();
        assertThat(pool.getMin()).isEqualTo(1);
        assertThat(pool.getMax()).isEqualTo(1);
        assertThat(pool.getIdleTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(pool.getHealthCheckInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(pool.getCloseTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void poolCustomValues() {
        var props = bind(Map.of(
                "python-embed.pool.min", "2",
                "python-embed.pool.max", "4",
                "python-embed.pool.idle-timeout", "120s",
                "python-embed.pool.health-check-interval", "15s",
                "python-embed.pool.close-timeout", "45s"
        ));
        var pool = props.getPool();
        assertThat(pool.getMin()).isEqualTo(2);
        assertThat(pool.getMax()).isEqualTo(4);
        assertThat(pool.getIdleTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(pool.getHealthCheckInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(pool.getCloseTimeout()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void optionsDefaults() {
        var props = bind(Map.of());
        var opts = props.getOptions();
        assertThat(opts.getTimeoutMs()).isZero();
        assertThat(opts.getEnvironmentVars()).isEmpty();
    }

    @Test
    void optionsCustomValues() {
        var props = bind(Map.of(
                "python-embed.options.timeout-ms", "60000",
                "python-embed.options.environment-vars.KEY1", "val1",
                "python-embed.options.environment-vars.KEY2", "val2"
        ));
        var opts = props.getOptions();
        assertThat(opts.getTimeoutMs()).isEqualTo(60_000L);
        assertThat(opts.getEnvironmentVars()).containsEntry("KEY1", "val1").containsEntry("KEY2", "val2");
    }

    @Test
    void invalidModeThrowsBindException() {
        var source = new MapConfigurationPropertySource(Map.of("python-embed.mode", "INVALID"));
        var binder = new Binder(source);
        assertThatThrownBy(() -> binder.bindOrCreate("python-embed", PythonEmbedProperties.class))
                .isInstanceOf(BindException.class);
    }

    @Test
    void negativePoolMinPropagates() {
        var props = bind(Map.of("python-embed.pool.min", "-1"));
        // Spring Boot does not validate numeric ranges; the value propagates as-is
        assertThat(props.getPool().getMin()).isEqualTo(-1);
    }

    private static PythonEmbedProperties bind(Map<String, String> source) {
        var binder = new Binder(new MapConfigurationPropertySource(source));
        return binder.bindOrCreate("python-embed", PythonEmbedProperties.class);
    }
}
