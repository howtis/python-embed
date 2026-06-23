package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.HealthInfo;
import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;

/**
 * Demonstrates health monitoring for PythonEmbed instances and pools.
 * <p>
 * Use {@link PythonEmbed#ping()} to check liveness and
 * {@link PythonEmbed#health()} to collect detailed runtime statistics
 * (memory, reference count, GC status).
 * <p>
 * For pools, {@link PythonEmbedPool#healthCheck()} runs a batch health
 * check on all idle instances and removes unhealthy ones.
 */
public class HealthMonitorExample {

    public static void main(String[] args) throws Exception {
        // ---- Single instance health monitoring ----
        try (PythonEmbed py = PythonEmbed.create()) {
            // ping(): quick liveness check
            boolean alive = py.ping();
            System.out.println("Ping: " + (alive ? "OK" : "FAIL"));

            // health(): detailed runtime statistics
            py.exec("x = [1] * 1_000_000");  // allocate some memory
            py.exec("""
                    import gc
                    gc.disable()  # disable GC to show gcEnabled=false
                    """);

            HealthInfo info = py.health();
            System.out.println("\nHealth info:");
            System.out.println("  Memory (RSS kB): " + info.memoryRssKb());
            System.out.println("  Active refs:     " + info.refCount());
            System.out.println("  GC enabled:      " + info.gcEnabled());
            System.out.println("  GC counts:       " + info.gcCounts());

            // Re-enable GC and check again
            py.exec("gc.enable()");
            py.exec("import gc; gc.collect()");
            HealthInfo info2 = py.health();
            System.out.println("\nAfter GC re-enable + collect:");
            System.out.println("  GC enabled:      " + info2.gcEnabled());
            System.out.println("  GC counts:       " + info2.gcCounts());
        }

        System.out.println();

        // ---- Pool health monitoring ----
        PythonEmbedPool pool = PythonEmbedPool.builder()
                .minPool(2)
                .maxPool(4)
                .healthCheckIntervalMs(10_000)   // periodic health checks
                .options(PythonEmbed.Options.builder().build())
                .build();

        try {
            // Give the pool time to initialize
            Thread.sleep(1000);

            System.out.println("Pool status (before health check):");
            System.out.println("  Size:   " + pool.size());
            System.out.println("  Active: " + pool.activeCount());
            System.out.println("  Min:    " + pool.minPool());
            System.out.println("  Max:    " + pool.maxPool());

            // Manual health check on all idle instances
            int removed = pool.healthCheck();
            System.out.println("Health check removed: " + removed + " unhealthy instance(s)");

            System.out.println("\nPool status (after health check):");
            System.out.println("  Size:   " + pool.size());
            System.out.println("  Active: " + pool.activeCount());

            System.out.println("\nAll health monitoring completed.");
        } finally {
            pool.close();
        }
    }
}
