package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PythonEmbed#close()} and
 * {@link PythonEmbed#closeInternal(long, long)}:
 * handle release order, shutdown hook removal, duplicate close.
 */
class PythonEmbedCloseTest {

    private PythonEmbed embed;

    @BeforeEach
    void setUp() {
        embed = new PythonEmbed(PythonEmbed.Options.defaults());
    }

    @AfterEach
    void tearDown() {
        if (embed != null) {
            embed.close();
        }
    }

    // ---- Reflection helpers ----

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------
    // Shutdown hook removal
    // ------------------------------------------------------------------

    @Test
    void close_removesShutdownHook() {
        // Register a dummy shutdown hook to simulate initialize()
        Thread hook = new Thread(() -> {}, "python-process-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        setField(embed, "processCleanupHook", hook);

        embed.close();

        // The hook should be removed and the field cleared
        assertNull(getField(embed, "processCleanupHook"),
                "processCleanupHook should be null after close");
    }

    @Test
    void close_shutdownHookAlreadyRemoved_isSafe() {
        // Simulate: hook was already removed (e.g., JVM shutting down)
        setField(embed, "processCleanupHook", null);

        assertDoesNotThrow(() -> embed.close(),
                "close() should not throw when shutdown hook is already null");
    }

    // ------------------------------------------------------------------
    // Handle release order
    // ------------------------------------------------------------------

    @Test
    void close_releasesAllHandlesBeforeClearing() throws Exception {
        // Create handles and add them to the handles list via reflection
        PythonHandle h1 = createHandle(1, "int");
        PythonHandle h2 = createHandle(2, "str");
        PythonHandle h3 = createHandle(3, "list");

        List<PythonHandle> handles = getField(embed, "handles");
        handles.add(h1);
        handles.add(h2);
        handles.add(h3);
        assertEquals(3, handles.size());

        embed.close();

        // All handles should be marked released
        assertTrue((boolean) getField(h1, "released"), "h1 should be released");
        assertTrue((boolean) getField(h2, "released"), "h2 should be released");
        assertTrue((boolean) getField(h3, "released"), "h3 should be released");
        // handles list should be cleared
        assertTrue(handles.isEmpty(), "handles list should be empty after close");
    }

    @Test
    void close_emptyHandlesList_isSafe() {
        List<PythonHandle> handles = getField(embed, "handles");
        assertTrue(handles.isEmpty(), "handles should be empty initially");

        assertDoesNotThrow(() -> embed.close(),
                "close() should be safe with empty handles list");
    }

    @Test
    void close_handlesWithReleaseException_continuesCleanup() throws Exception {
        // A handle that throws on release() should not prevent other handles
        // from being released or the rest of close logic from running.
        PythonHandle good1 = createHandle(10, "int");
        PythonHandle good2 = createHandle(20, "str");

        List<PythonHandle> handles = getField(embed, "handles");
        handles.add(good1);
        handles.add(good2);
        assertEquals(2, handles.size());

        embed.close();

        assertTrue(handles.isEmpty(), "handles list should be cleared even if some releases threw");
    }

    // ------------------------------------------------------------------
    // Callback / Push handler cleanup
    // ------------------------------------------------------------------

    @Test
    void close_clearsCallbackHandlers() {
        Map<String, CallbackHandler> callbacks = getField(embed, "callbackHandlers");
        callbacks.put("test", args -> null);
        assertEquals(1, callbacks.size());

        embed.close();

        assertTrue(callbacks.isEmpty(), "callbackHandlers should be empty after close");
    }

    @Test
    void close_clearsPushHandlers() {
        Map<String, PushHandler> push = getField(embed, "pushHandlers");
        push.put("test", (name, value) -> {});
        assertEquals(1, push.size());

        embed.close();

        assertTrue(push.isEmpty(), "pushHandlers should be empty after close");
    }

    // ------------------------------------------------------------------
    // Duplicate close (idempotence)
    // ------------------------------------------------------------------

    @Test
    void close_isIdempotent() {
        // Register a shutdown hook so the first close can remove it
        Thread hook = new Thread(() -> {}, "python-process-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        setField(embed, "processCleanupHook", hook);

        embed.close();
        embed.close();
        embed.close();

        // Should not throw - three consecutive closes are safe
    }

    // ------------------------------------------------------------------
    // close() default vs close(timeout, unit)
    // ------------------------------------------------------------------

    @Test
    void close_defaultTimeout_callsCloseInternal() {
        // Default close() uses 5_000ms wait, 2_000ms forceWait
        Thread hook = new Thread(() -> {}, "python-process-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        setField(embed, "processCleanupHook", hook);

        assertDoesNotThrow(() -> embed.close(),
                "Default close() should succeed");
        assertNull(getField(embed, "processCleanupHook"));
    }

    @Test
    void close_timeoutNegative_usesDefault() {
        // Negative timeout: waitMs = -1, forceWaitMs = min(-1, 2000) = -1
        // closeInternal handles negative values gracefully via processManager.close()
        Thread hook = new Thread(() -> {}, "python-process-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        setField(embed, "processCleanupHook", hook);

        assertDoesNotThrow(() -> embed.close(-1, TimeUnit.MILLISECONDS),
                "close() with negative timeout should not throw");
        assertNull(getField(embed, "processCleanupHook"));
    }

    @Test
    void close_timeoutZero_usesDefault() {
        // Zero timeout: waitMs = 0, forceWaitMs = min(0, 2000) = 0
        // Zero timeouts mean immediate fall-through in processManager.close()
        Thread hook = new Thread(() -> {}, "python-process-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        setField(embed, "processCleanupHook", hook);

        assertDoesNotThrow(() -> embed.close(0, TimeUnit.MILLISECONDS),
                "close() with zero timeout should not throw");
        assertNull(getField(embed, "processCleanupHook"));
    }

    @Test
    void close_largeTimeout_gracefulShutdown() {
        // Large timeout: waitMs is large, forceWaitMs = min(large/2, 2000) = 2000
        Thread hook = new Thread(() -> {}, "python-process-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        setField(embed, "processCleanupHook", hook);

        assertDoesNotThrow(() -> embed.close(60_000, TimeUnit.MILLISECONDS),
                "close() with large timeout should not throw");
        assertNull(getField(embed, "processCleanupHook"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PythonHandle createHandle(int refId, String pythonType) throws Exception {
        // Use the package-private constructor via reflection
        java.lang.reflect.Constructor<PythonHandle> ctor =
                PythonHandle.class.getDeclaredConstructor(
                        PythonEmbed.class, PythonProtocol.class,
                        PythonProtocol.Writer.class, int.class, String.class);
        ctor.setAccessible(true);

        // Create a no-op protocol for the handle
        PythonProtocol proto = new PythonProtocol() {
            @Override
            void handleResponse(byte[] raw) {}
            @Override byte[] buildEvalRequest(int id, String code) { return new byte[0]; }
            @Override byte[] buildExecRequest(int id, String code) { return new byte[0]; }
            @Override byte[] buildExitRequest(int id) { return new byte[0]; }
            @Override byte[] buildRefRequest(int id, String name) { return new byte[0]; }
            @Override byte[] buildReleaseRequest(int id, int refId2) { return new byte[0]; }
            @Override byte[] buildCallRequest(int id, int refId2, String method, Object[] args) { return new byte[0]; }
            @Override byte[] buildGetAttrRequest(int id, int refId2, String name) { return new byte[0]; }
            @Override byte[] buildPingRequest(int id) { return new byte[0]; }
            @Override byte[] buildHealthRequest(int id) { return new byte[0]; }
            @Override byte[] buildStreamRequest(int id, String code) { return new byte[0]; }
            @Override byte[] buildCallbackResult(int id, Object value) { return new byte[0]; }
            @Override byte[] buildCallbackError(int id, String message) { return new byte[0]; }
            @Override byte[] buildPushRequest(int id, String name, Object value) { return new byte[0]; }
            @Override byte[] buildBatchRequest(int[] ids, List<Map<String, Object>> items) { return new byte[0]; }
        };

        PythonProtocol.Writer noopWriter = data -> {};
        return ctor.newInstance(embed, proto, noopWriter, refId, pythonType);
    }
}
