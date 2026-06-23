package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Auto-configuration for python-embed runtime integration.
 *
 * <p>Supports two modes, controlled by {@code python-embed.mode}:
 * <ul>
 *   <li>{@code SINGLE} -- a single {@link PythonEmbed} bean (default)</li>
 *   <li>{@code POOL} -- a {@link PythonEmbedPool} bean with configurable
 *       min/max/idle/health settings</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(PythonEmbed.class)
@EnableConfigurationProperties(PythonEmbedProperties.class)
public class PythonEmbedAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PythonEmbedAutoConfiguration.class);

    // ------------------------------------------------------------------
    // Options helper
    // ------------------------------------------------------------------

    static PythonEmbed.Options buildOptions(PythonEmbedProperties props) {
        PythonEmbed.Options.Builder builder = PythonEmbed.Options.builder();

        String venvPath = props.getVenvPath();
        if (venvPath != null && !venvPath.isBlank()) {
            builder.venvPath(Path.of(venvPath));
        }

        long timeoutMs = props.getOptions().getTimeoutMs();
        if (timeoutMs > 0) {
            builder.timeoutMs(timeoutMs);
        }

        var env = props.getOptions().getEnvironmentVars();
        if (env != null && !env.isEmpty()) {
            builder.env(env);
        }

        return builder.build();
    }

    // ------------------------------------------------------------------
    // SINGLE mode
    // ------------------------------------------------------------------

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "python-embed", name = "mode", havingValue = "SINGLE", matchIfMissing = true)
    PythonEmbed pythonEmbed(PythonEmbedProperties props) {
        PythonEmbed.Options options = buildOptions(props);
        log.info("Creating PythonEmbed (SINGLE mode)");
        return PythonEmbed.create(options);
    }

    // ------------------------------------------------------------------
    // POOL mode
    // ------------------------------------------------------------------

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "python-embed", name = "mode", havingValue = "POOL")
    PythonEmbedPool pythonEmbedPool(PythonEmbedProperties props) {
        PythonEmbed.Options options = buildOptions(props);
        var poolCfg = props.getPool();

        log.info("Creating PythonEmbedPool (POOL mode) min={} max={}", poolCfg.getMin(), poolCfg.getMax());
        return PythonEmbedPool.builder()
                .minPool(poolCfg.getMin())
                .maxPool(poolCfg.getMax())
                .idleTimeoutMs(poolCfg.getIdleTimeout().toMillis())
                .healthCheckIntervalMs(poolCfg.getHealthCheckInterval().toMillis())
                .options(options)
                .build();
    }

    /**
     * Closes the pool with the configured {@code python-embed.pool.close-timeout}
     * when the Spring context shuts down.
     *
     * <p>The pool's own JVM shutdown hook provides defense-in-depth (5s default).
     */
    @Bean
    @ConditionalOnProperty(prefix = "python-embed", name = "mode", havingValue = "POOL")
    DisposableBean pythonEmbedPoolCloser(PythonEmbedPool pool, PythonEmbedProperties props) {
        return new PoolCloser(pool, props.getPool());
    }

    static final class PoolCloser implements DisposableBean {
        private final PythonEmbedPool pool;
        private final PythonEmbedProperties.PoolProperties poolProps;

        PoolCloser(PythonEmbedPool pool, PythonEmbedProperties.PoolProperties poolProps) {
            this.pool = pool;
            this.poolProps = poolProps;
        }

        @Override
        public void destroy() {
            long ms = poolProps.getCloseTimeout().toMillis();
            log.info("Closing PythonEmbedPool with closeTimeout={}ms", ms);
            pool.close(ms, TimeUnit.MILLISECONDS);
        }
    }

    // ------------------------------------------------------------------
    // HealthIndicator (auto-detected when Actuator is on classpath)
    // ------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "python-embed", name = "mode", havingValue = "SINGLE", matchIfMissing = true)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    PythonEmbedHealthIndicator pythonEmbedHealthIndicator(PythonEmbed embed) {
        return new PythonEmbedHealthIndicator.Single(embed);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "python-embed", name = "mode", havingValue = "POOL")
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    PythonEmbedHealthIndicator pythonEmbedPoolHealthIndicator(PythonEmbedPool pool) {
        return new PythonEmbedHealthIndicator.Pool(pool);
    }
}
