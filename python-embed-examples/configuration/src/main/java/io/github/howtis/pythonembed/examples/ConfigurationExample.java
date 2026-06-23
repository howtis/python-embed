package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.CloseReason;
import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonValue;

import java.util.List;
import java.util.Map;

/**
 * Demonstrates the full PythonEmbed configuration options.
 * <p>
 * {@link PythonEmbed.Options} consolidates all configuration:
 * venv path, environment variables, timeouts, warmup scripts, code limits,
 * close hooks, and more. All options are set through the fluent builder.
 */
public class ConfigurationExample {

    public static void main(String[] args) {
        // ---- Build configuration with all available options ----
        PythonEmbed.Options options = PythonEmbed.Options.builder()
                // Timeouts
                .timeoutMs(60_000)             // per-request timeout (default: 30s)
                .startupTimeoutMs(15_000)      // process startup timeout (default: 10s)

                // Code safety
                .maxCodeLength(200_000)        // max code size in chars (default: 100k)

                // Environment
                .env(Map.of(
                        "PYTHONUNBUFFERED", "1",
                        "OMP_NUM_THREADS", "2"
                ))

                // Warmup: scripts executed on startup (before first user request)
                .warmupScript("import json")
                .warmupScripts(List.of(
                        "import math",
                        "import collections",
                        "from datetime import datetime"
                ))
                .lenientWarmup(true)           // don't fail if warmup has errors

                // Explicit Python executable (optional)
                .pythonExecutable("python3")   // or full path like /usr/bin/python3.11

                // Explicit venv path (optional -- for pre-built environments)
                // .venvPath(Path.of("/opt/myapp/venv"))

                // Close lifecycle hooks
                .onBeforeClose((py, reason) -> {
                    if (reason == CloseReason.USER) {
                        py.exec("import os; os.remove('temp.dat') if os.path.exists('temp.dat') else None");
                    }
                    System.out.println("[config example] Closing PythonEmbed: " + reason);
                })
                .onAfterClose((py, reason) ->
                        System.out.println("[config example] Closed PythonEmbed: " + reason))

                .build();

        try (PythonEmbed py = PythonEmbed.create(options)) {
            // The warmup scripts have already been executed at this point
            PythonValue now = py.eval("datetime.now().isoformat()");
            System.out.println("Current time (via warmup import): " + now.asString());

            // Verify other warmup imports
            double pi = py.eval("math.pi").asDouble();
            System.out.println("math.pi (via warmup): " + pi);

            int counter = py.eval("collections.Counter('hello').get('l', 0)").asInt();
            System.out.println("Counter('hello')['l'] (via warmup): " + counter);

            // Environment variables are accessible in Python
            String ompThreads = py.eval("__import__('os').environ.get('OMP_NUM_THREADS', 'N/A')").asString();
            System.out.println("OMP_NUM_THREADS (from env): " + ompThreads);

            System.out.println("\nAll configuration demonstrations completed.");
        }
    }
}
