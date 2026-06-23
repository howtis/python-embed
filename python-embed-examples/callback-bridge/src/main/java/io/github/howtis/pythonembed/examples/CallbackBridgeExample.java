package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;

/**
 * Demonstrates Python-to-Java callbacks and push handlers.
 * <p>
 * Python code can call Java methods via:
 * - {@code _bridge.call("name", args...)} - synchronous callback
 * - {@code _bridge.push("name", value)} - fire-and-forget notification
 */
public class CallbackBridgeExample {

    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            // ---- registerCallback: Python calls Java ----
            py.registerCallback("greet", params -> {
                String name = (String) params[0];
                return "Hello from Java, " + name + "!";
            });

            py.registerCallback("add", Integer.class, Integer.class, (a, b) -> {
                System.out.println("  Java: adding " + a + " + " + b);
                return a + b;
            });

            // Python calls back into Java
            String greetResult = py.eval("_bridge.call('greet', 'Python')").asString();
            System.out.println("Python got (via _bridge.call): " + greetResult);

            int addResult = py.eval("_bridge.call('add', 10, 20)").asInt();
            System.out.println("Python got (via _bridge.call): " + addResult);

            // ---- registerPushHandler: fire-and-forget ----
            py.registerPushHandler("progress", (String name, Object value) -> {
                System.out.println("Progress [" + name + "]: " + value + "%");
            });

            py.exec("""
                    for i in [25, 50, 75, 100]:
                        _bridge.push("progress", i)
                    """);

            // ---- Multi-arg callback ----
            py.registerCallback("format", String.class, Integer.class, String.class,
                    (name, age, city) -> String.format("%s is %d years old from %s", name, age, city));

            String formatted = py.eval("_bridge.call('format', 'Alice', 30, 'Seoul')").asString();
            System.out.println("Formatted: " + formatted);

            System.out.println("\nAll callback operations completed.");
        }
    }
}
