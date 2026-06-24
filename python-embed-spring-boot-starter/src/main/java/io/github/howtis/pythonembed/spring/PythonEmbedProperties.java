package io.github.howtis.pythonembed.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for python-embed auto-configuration.
 *
 * <p>Prefix: {@code python-embed}
 *
 * @see PythonEmbedAutoConfiguration
 */
@ConfigurationProperties(prefix = "python-embed")
public class PythonEmbedProperties {

    public enum Mode { SINGLE, POOL }

    /**
     * Operating mode: {@code SINGLE} for a single PythonEmbed bean,
     * {@code POOL} for a PythonEmbedPool with configurable min/max.
     */
    private Mode mode = Mode.SINGLE;

    /** Overrides the venv path discovered from classpath or build-time properties. */
    private String venvPath;

    /** Pool configuration, active when {@code mode=POOL}. */
    @NestedConfigurationProperty
    private PoolProperties pool = new PoolProperties();

    /** General options shared across all modes. */
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
        /** Minimum number of Python processes to keep alive. */
        private int min = 1;
        /** Maximum number of Python processes allowed. */
        private int max = 1;
        /** Time before an idle process is evicted from the pool. */
        private Duration idleTimeout = Duration.ofSeconds(60);
        /** Interval between health-check ping/pong requests. */
        private Duration healthCheckInterval = Duration.ofSeconds(30);
        /** Maximum time to wait for graceful shutdown of the pool. */
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
        /** Timeout for Python operations in milliseconds (0 = no timeout). */
        private long timeoutMs = 0;
        /** Maximum code length in characters. */
        private int maxCodeLength = 100_000;
        /** Startup timeout in milliseconds. */
        private long startupTimeoutMs = 30_000;
        /** Override the Python executable path. */
        private String pythonExecutable;
        /** Warmup scripts to execute after instance initialization. */
        private List<String> warmupScripts = new ArrayList<>();
        /** Whether warmup script failures should be logged as warnings instead of throwing exceptions. */
        private boolean lenientWarmup = true;
        /** Environment variables passed to the Python subprocess. */
        private Map<String, String> environmentVars = Map.of();

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getMaxCodeLength() { return maxCodeLength; }
        public void setMaxCodeLength(int maxCodeLength) { this.maxCodeLength = maxCodeLength; }
        public long getStartupTimeoutMs() { return startupTimeoutMs; }
        public void setStartupTimeoutMs(long startupTimeoutMs) { this.startupTimeoutMs = startupTimeoutMs; }
        public String getPythonExecutable() { return pythonExecutable; }
        public void setPythonExecutable(String pythonExecutable) { this.pythonExecutable = pythonExecutable; }
        public List<String> getWarmupScripts() { return warmupScripts; }
        public void setWarmupScripts(List<String> warmupScripts) { this.warmupScripts = warmupScripts; }
        public boolean isLenientWarmup() { return lenientWarmup; }
        public void setLenientWarmup(boolean lenientWarmup) { this.lenientWarmup = lenientWarmup; }
        public Map<String, String> getEnvironmentVars() { return environmentVars; }
        public void setEnvironmentVars(Map<String, String> environmentVars) { this.environmentVars = environmentVars; }
    }
}
