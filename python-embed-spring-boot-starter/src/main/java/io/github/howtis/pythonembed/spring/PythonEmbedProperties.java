package io.github.howtis.pythonembed.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for python-embed auto-configuration.
 *
 * <p>Prefix: {@code python-embed}
 */
@ConfigurationProperties(prefix = "python-embed")
public class PythonEmbedProperties {

    public enum Mode { SINGLE, POOL }

    private Mode mode = Mode.SINGLE;

    /** Overrides the venv path discovered from classpath or build-time properties. */
    private String venvPath;

    @NestedConfigurationProperty
    private PoolProperties pool = new PoolProperties();

    @NestedConfigurationProperty
    private OptionsProperties options = new OptionsProperties();

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getVenvPath() { return venvPath; }
    public void setVenvPath(String venvPath) { this.venvPath = venvPath; }
    public PoolProperties getPool() { return pool; }
    public void setPool(PoolProperties pool) { this.pool = pool; }
    public OptionsProperties getOptions() { return options; }
    public void setOptions(OptionsProperties options) { this.options = options; }

    public static class PoolProperties {
        private int min = 1;
        private int max = 1;
        private Duration idleTimeout = Duration.ofSeconds(60);
        private Duration healthCheckInterval = Duration.ofSeconds(30);
        private Duration closeTimeout = Duration.ofSeconds(30);

        public int getMin() { return min; }
        public void setMin(int min) { this.min = min; }
        public int getMax() { return max; }
        public void setMax(int max) { this.max = max; }
        public Duration getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
        public Duration getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(Duration healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }
        public Duration getCloseTimeout() { return closeTimeout; }
        public void setCloseTimeout(Duration closeTimeout) { this.closeTimeout = closeTimeout; }
    }

    public static class OptionsProperties {
        private long timeoutMs = 0;
        private Map<String, String> environmentVars = Map.of();

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public Map<String, String> getEnvironmentVars() { return environmentVars; }
        public void setEnvironmentVars(Map<String, String> environmentVars) { this.environmentVars = environmentVars; }
    }
}
