package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import io.github.howtis.pythonembed.PythonValue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates PythonEmbedPool - auto-scaling pool with async CompletableFuture API.
 * <p>
 * Key features shown:
 * - Pool builder configuration (min/max pool, idle timeout)
 * - Async eval/exec/batchEval
 * - Pool monitoring (size, activeCount)
 * - Parallel execution
 */
public class PoolAsyncExample {

    public static void main(String[] args) throws Exception {
        PythonEmbed.Options options = PythonEmbed.Options.builder()
                .warmupScript("x = 42")
                .build();

        try (PythonEmbedPool pool = PythonEmbedPool.builder()
                .minPool(2)
                .maxPool(4)
                .idleTimeoutMs(30_000)
                .options(options)
                .build()) {

            // Wait for pool to reach minimum size
            Thread.sleep(1000);
            System.out.println("Pool size: " + pool.size());

            // ---- Async evaluation ----
            CompletableFuture<PythonValue> f1 = pool.eval("sum([1, 2, 3])");
            CompletableFuture<PythonValue> f2 = pool.eval("x * 2");

            int sum = f1.get(5, TimeUnit.SECONDS).asInt();
            int doubled = f2.get(5, TimeUnit.SECONDS).asInt();
            System.out.println("eval sum = " + sum);
            System.out.println("eval x*2 = " + doubled);

            // ---- Batch execution ----
            CompletableFuture<List<PythonValue>> batch = pool.batchEval(
                    List.of("5 * 2", "5 + 1", "5 ** 2"));
            List<PythonValue> results = batch.get(5, TimeUnit.SECONDS);
            System.out.println("batchEval [5*2, 5+1, 5**2]:");
            for (int i = 0; i < results.size(); i++) {
                System.out.println("  " + i + ": " + results.get(i).asInt());
            }

            // ---- Concurrent execution ----
            pyExecParallel(pool);

            // ---- Pool monitoring ----
            System.out.println("Pool size: " + pool.size());
            System.out.println("Active: " + pool.activeCount());
            System.out.println("Min: " + pool.minPool() + ", Max: " + pool.maxPool());

            System.out.println("\nAll pool operations completed.");
        }
    }

    private static void pyExecParallel(PythonEmbedPool pool) {
        CompletableFuture<Void> t1 = pool.exec("import time; time.sleep(0.5)");
        CompletableFuture<Void> t2 = pool.exec("import time; time.sleep(0.5)");
        CompletableFuture<Void> t3 = pool.exec("import time; time.sleep(0.5)");
        CompletableFuture.allOf(t1, t2, t3).join();
        System.out.println("3 concurrent execs completed");
    }
}
