package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import io.github.howtis.pythonembed.PythonExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator logic for python-embed (Spring Boot version-agnostic).
 *
 * <p>Does not implement any Spring Boot health interface directly.
 * Instead, version-specific adapters ({@link HealthIndicatorAdapter} for
 * Boot 3.x, {@link HealthIndicatorV4Adapter} for Boot 4.x) wrap instances
 * of this class and expose them as the appropriate {@code HealthIndicator}.
 *
 * <p>SINGLE mode: calls {@link PythonEmbed#health()} -- UP if the
 * Python process responds without error.
 *
 * <p>POOL mode: checks that the pool has at least {@link PythonEmbedPool#minPool()}
 * live instances ({@link PythonEmbedPool#size()} &gt;= {@code minPool}).
 */
public sealed abstract class PythonEmbedHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(PythonEmbedHealthIndicator.class);

    /**
     * Version-agnostic health result.
     *
     * @param up whether the component is healthy
     * @param details key-value details (may be empty)
     * @param error the exception that caused the failure (or null)
     */
    public record HealthData(boolean up, Map<String, Object> details, Exception error) {
        public HealthData {
            details = Map.copyOf(details);
        }

        public static HealthData up(Map<String, Object> details) {
            return new HealthData(true, details, null);
        }

        public static HealthData down(Exception error) {
            return new HealthData(false, Map.of(), error);
        }

        public static HealthData down(Map<String, Object> details) {
            return new HealthData(false, details, null);
        }
    }

    public abstract HealthData health();

    static final class Single extends PythonEmbedHealthIndicator {
        private final PythonEmbed embed;

        Single(PythonEmbed embed) {
            this.embed = embed;
        }

        @Override
        public HealthData health() {
            try {
                var info = embed.health();
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("memoryRssKb", info.memoryRssKb());
                details.put("refCount", info.refCount());
                details.put("gcEnabled", info.gcEnabled());
                details.put("gcCounts", info.gcCounts());
                return HealthData.up(details);
            } catch (PythonExecutionException e) {
                log.warn("PythonEmbed health check failed", e);
                return HealthData.down(e);
            }
        }
    }

    static final class Pool extends PythonEmbedHealthIndicator {
        private final PythonEmbedPool pool;

        Pool(PythonEmbedPool pool) {
            this.pool = pool;
        }

        @Override
        public HealthData health() {
            try {
                int size = pool.size();
                int min = pool.minPool();
                int active = pool.activeCount();
                boolean healthy = size >= min;
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("size", size);
                details.put("minPool", min);
                details.put("activeCount", active);
                return healthy ? HealthData.up(details) : HealthData.down(details);
            } catch (Exception e) {
                log.warn("PythonEmbedPool health check failed", e);
                return HealthData.down(e);
            }
        }
    }
}
