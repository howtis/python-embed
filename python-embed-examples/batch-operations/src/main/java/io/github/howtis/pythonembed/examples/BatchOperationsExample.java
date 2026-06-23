package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import io.github.howtis.pythonembed.PythonValue;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates batch evaluation and execution -- sending multiple requests
 * in a single round-trip to reduce protocol overhead.
 * <p>
 * Both {@link PythonEmbed} (single instance) and {@link PythonEmbedPool}
 * support batch operations. The pool version distributes the entire batch
 * to a single instance for sequential execution.
 */
public class BatchOperationsExample {

    public static void main(String[] args) throws Exception {
        // ---- Single embed batch operations ----
        System.out.println("=== Single Embed Batch ===");
        try (PythonEmbed py = PythonEmbed.create()) {
            py.exec("x = 10");

            // batchEval: evaluate multiple expressions in one round-trip
            List<PythonValue> results = py.batchEval(List.of(
                    "x * 2",
                    "'hello'.upper()",
                    "sum(range(1, x + 1))",
                    "[1, 2, 3] + [4, 5]"
            ));

            System.out.println("batchEval results:");
            System.out.println("  x * 2              = " + results.get(0).asInt());
            System.out.println("  'hello'.upper()    = " + results.get(1).asString());
            System.out.println("  sum(range(1,11))   = " + results.get(2).asInt());
            System.out.println("  [1,2,3]+[4,5]      = " + results.get(3).asList(Integer.class));

            // batchExec: execute multiple statements (no return value)
            py.batchExec(List.of(
                    "y = x ** 2",
                    "z = y + 1",
                    "result = f'x={x}, y={y}, z={z}'"
            ));

            System.out.println("\nAfter batchExec:");
            System.out.println("  y = " + py.eval("y").asInt());
            System.out.println("  z = " + py.eval("z").asInt());
            System.out.println("  " + py.eval("result").asString());

            // batchEval with timeout override
            List<PythonValue> timedResults = py.batchEval(
                    List.of("sum(range(1_000_000))", "len('hi')"),
                    10_000  // 10 second timeout for this batch
            );
            System.out.println("\nTimed batchEval:");
            System.out.println("  sum(1..999999) = " + timedResults.get(0).asLong());
            System.out.println("  len('hi')      = " + timedResults.get(1).asInt());
        }

        System.out.println();

        // ---- Pool batch operations ----
        System.out.println("=== Pool Batch ===");
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .warmupScript("value = 5")
                .build();

        try (PythonEmbedPool pool = PythonEmbedPool.builder()
                .minPool(2)
                .maxPool(4)
                .options(opts)
                .build()) {

            Thread.sleep(1000);

            // Pool batchEval -- returns CompletableFuture
            List<PythonValue> poolResults = pool.batchEval(List.of(
                    "value * 2",
                    "value + 10",
                    "value ** 2"
            )).get(5, TimeUnit.SECONDS);

            System.out.println("Pool batchEval (value=5):");
            System.out.println("  value * 2  = " + poolResults.get(0).asInt());
            System.out.println("  value + 10 = " + poolResults.get(1).asInt());
            System.out.println("  value ** 2 = " + poolResults.get(2).asInt());

            // Pool batchExec -- execute multiple statements
            pool.batchExec(List.of(
                    "import time",
                    "now = time.time()",
                    "msg = 'batch exec done'"
            )).get(5, TimeUnit.SECONDS);

            System.out.println("\nPool batchExec completed.");

            // Pool batchEval with timeout
            List<PythonValue> timedPoolResults = pool.batchEval(
                    List.of("sum(range(100_000))", "len('world')"),
                    10_000
            ).get(10, TimeUnit.SECONDS);
            System.out.println("\nPool timed batchEval:");
            System.out.println("  sum(range(100k)) = " + timedPoolResults.get(0).asLong());
            System.out.println("  len('world')     = " + timedPoolResults.get(1).asInt());

            System.out.println("\nAll batch operations completed.");
        }
    }
}
