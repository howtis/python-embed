package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import io.github.howtis.pythonembed.PythonExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Actuator health indicator for python-embed.
 *
 * <p>SINGLE mode: calls {@link PythonEmbed#health()} -- UP if the
 * Python process responds without error.
 *
 * <p>POOL mode: checks that the pool has at least {@link PythonEmbedPool#minPool()}
 * live instances ({@link PythonEmbedPool#size()} &gt;= {@code minPool}).
 */
public sealed abstract class PythonEmbedHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(PythonEmbedHealthIndicator.class);

    static final class Single extends PythonEmbedHealthIndicator {
        private final PythonEmbed embed;

        Single(PythonEmbed embed) {
            this.embed = embed;
        }

        @Override
        public Health health() {
            try {
                var info = embed.health();
                return Health.up()
                        .withDetail("memoryRssKb", info.memoryRssKb())
                        .withDetail("refCount", info.refCount())
                        .withDetail("gcEnabled", info.gcEnabled())
                        .withDetail("gcCounts", info.gcCounts())
                        .build();
            } catch (PythonExecutionException e) {
                log.warn("PythonEmbed health check failed", e);
                return Health.down()
                        .withException(e)
                        .build();
            }
        }
    }

    static final class Pool extends PythonEmbedHealthIndicator {
        private final PythonEmbedPool pool;

        Pool(PythonEmbedPool pool) {
            this.pool = pool;
        }

        @Override
        public Health health() {
            try {
                int size = pool.size();
                int min = pool.minPool();
                int active = pool.activeCount();
                boolean healthy = size >= min;
                var builder = healthy ? Health.up() : Health.down();
                return builder
                        .withDetail("size", size)
                        .withDetail("minPool", min)
                        .withDetail("activeCount", active)
                        .build();
            } catch (Exception e) {
                log.warn("PythonEmbedPool health check failed", e);
                return Health.down()
                        .withException(e)
                        .build();
            }
        }
    }
}
