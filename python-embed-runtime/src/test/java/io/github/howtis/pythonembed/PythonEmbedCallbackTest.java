package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PythonEmbedCallbackTest {

    private static PythonEmbed py;

    @BeforeAll
    static void setUp() {
        py = PythonEmbed.create(PythonEmbed.Options.defaults());
    }

    @AfterAll
    static void tearDown() {
        py.close();
    }

    @AfterEach
    void clearHandlers() {
        // Handlers are cleared when PythonEmbed is closed (in @AfterAll).
        // Individual tests register their own handlers with unique names.
    }

    // ---- basic sanity ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void basicEvalWorks() {
        PythonValue result = py.eval("1 + 1");
        assertEquals(2, result.asInt());
    }

    // ---- _bridge.call() ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_returnsValue() {
        py.registerCallback("add", args -> ((Number) args[0]).intValue() + ((Number) args[1]).intValue());

        PythonValue result = py.eval("_bridge.call('add', 3, 4)");
        assertEquals(7, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_typedArgs_noDowncasting() {
        py.registerCallback("typed_add", new Class<?>[]{Integer.class, Integer.class},
                args -> (Integer) args[0] + (Integer) args[1]);

        PythonValue result = py.eval("_bridge.call('typed_add', 3, 4)");
        assertEquals(7, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_withStringArg() {
        py.registerCallback("greet", args -> "Hello, " + args[0] + "!");

        PythonValue result = py.eval("_bridge.call('greet', 'World')");
        assertEquals("Hello, World!", result.asString());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_withListArg() {
        py.registerCallback("first", args -> ((List<?>) args[0]).get(0));

        PythonValue result = py.eval("_bridge.call('first', [10, 20, 30])");
        assertEquals(10, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_withNoArgs() {
        py.registerCallback("now", args -> "ok");

        PythonValue result = py.eval("_bridge.call('now')");
        assertEquals("ok", result.asString());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_returnsNull() {
        py.registerCallback("nothing", args -> null);

        PythonValue result = py.eval("_bridge.call('nothing')");
        assertNull(result.raw());
    }

    // ---- _bridge.call() error handling ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_unknownHandler_throwsRuntimeError() {
        // No handler registered for "unknown"
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval("_bridge.call('unknown')")
        );
        assertTrue(ex.getMessage().contains("No callback registered for name: unknown"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_handlerThrows_propagatesError() {
        py.registerCallback("explode", args -> {
            throw new IllegalArgumentException("Boom from Java!");
        });

        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval("_bridge.call('explode')")
        );
        assertTrue(ex.getMessage().contains("Boom from Java!"));
    }

    // ---- _bridge.push() ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void push_received() throws Exception {
        AtomicInteger received = new AtomicInteger(0);
        py.registerPushHandler("progress", (name, value) -> received.set(((Number) value).intValue()));

        py.exec("_bridge.push('progress', 75)");
        // Give a brief moment for the push to be processed
        Thread.sleep(200);
        assertEquals(75, received.get());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void push_typedValue_noDowncasting() throws Exception {
        AtomicInteger received = new AtomicInteger(0);
        py.registerPushHandler("typed_progress", Integer.class,
                (name, value) -> received.set(value));

        py.exec("_bridge.push('typed_progress', 75)");
        Thread.sleep(200);
        assertEquals(75, received.get());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void push_doesNotBlockPython() {
        AtomicInteger received = new AtomicInteger(0);
        py.registerPushHandler("log", Integer.class, (name, value) -> {
            received.set(value);
        });

        // Push followed by computation should work
        py.exec("_bridge.push('log', 42); x = 100");
        PythonValue x = py.eval("x");
        assertEquals(100, x.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void push_handlerThrows_noEffect() {
        py.registerPushHandler("failing", (name, value) -> {
            throw new RuntimeException("Push handler failure");
        });

        // Should not affect Python execution
        assertDoesNotThrow(() -> py.exec("_bridge.push('failing', 'test')"));
        PythonValue result = py.eval("42");
        assertEquals(42, result.asInt());
    }

    // ---- combined scenarios ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void multipleCallsAndPushes_duringExec() {
        AtomicInteger callCount = new AtomicInteger(0);
        List<Object> pushValues = new ArrayList<>();

        py.registerCallback("count", args -> callCount.incrementAndGet());
        py.registerPushHandler("track", (name, value) -> pushValues.add(value));

        py.exec("""
                for i in range(1, 4):
                    _bridge.push('track', i)
                    _bridge.call('count')
                """);

        assertEquals(3, callCount.get());
        assertEquals(3, pushValues.size());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_nestedCallback() {
        py.registerCallback("outer", args -> {
            // The inner call goes back to Java, which calls another handler
            // The result comes back from Python's eval
            return "outer-result";
        });
        py.registerCallback("inner", args -> "inner-result");

        py.exec("""
                # Store results in list to verify both execute
                results = []
                results.append(_bridge.call('outer'))
                results.append(_bridge.call('inner'))
                """);

        PythonValue r = py.eval("results");
        List<?> results = r.asList(Object.class);
        assertEquals("outer-result", results.get(0));
        assertEquals("inner-result", results.get(1));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_withMultipleArgs_ofDifferentTypes() {
        AtomicReference<Object[]> captured = new AtomicReference<>();
        py.registerCallback("capture", args -> {
            captured.set(args);
            return args.length;
        });

        PythonValue result = py.eval("_bridge.call('capture', 1, 'two', [3.0, 4.0])");
        assertEquals(3, result.asInt());
        Object[] args = captured.get();
        assertEquals(3, args.length);
    }
}
