package io.github.howtis.pythonembed;

/**
 * A point-in-time snapshot of {@link PythonEmbedPool} metrics.
 *
 * <p>Obtained via {@link PythonEmbedPool#metrics()}. All values reflect
 * the pool's state at the moment of the call and may change immediately
 * afterward.
 */
public record PoolMetrics(
        int poolSize,
        int activeCount,
        int idleCount,
        int minPool,
        int maxPool,
        long uptimeMs) {
}
