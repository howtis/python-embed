package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests with explicit venv path,
 * using the venv created by the Gradle plugin at build time.
 */
class PythonEmbedExplicitPathTest {

    private PythonEmbed py;

    @BeforeEach
    void setUp() {
        // The Gradle plugin creates the venv at build/python-venv
        // relative to the module directory.
        Path venvPath = Path.of("build", "python-venv");
        if (!venvPath.toFile().exists()) {
            // Skip test if venv not found (e.g., in IDE without Gradle build)
            py = null;
            return;
        }
        py = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(venvPath).build());
    }

    @AfterEach
    void tearDown() {
        if (py != null) {
            py.close();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_simpleExpression_returnsInt() {
        assumeVenvExists();
        PythonValue result = py.eval("sum([1, 2, 3])");
        assertEquals(6, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_preservesState() {
        assumeVenvExists();
        py.exec("x = 42");
        assertEquals(42, py.eval("x").asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_stringResult() {
        assumeVenvExists();
        PythonValue result = py.eval("'hello'.upper()");
        assertEquals("HELLO", result.asString());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_errorThrows() {
        assumeVenvExists();
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval("1 / 0")
        );
        assertTrue(ex.getMessage().contains("ZeroDivisionError"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void create_withEnvVars_passesToProcess() {
        Path venvPath = Path.of("build", "python-venv");
        if (!venvPath.toFile().exists()) {
            return;
        }
        Map<String, String> env = Map.of("TEST_VAR", "hello_from_java");
        try (PythonEmbed pyWithEnv = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(venvPath).env(env).build())) {
            pyWithEnv.exec("import os");
            PythonValue result = pyWithEnv.eval("os.environ.get('TEST_VAR', 'NOT_FOUND')");
            assertEquals("hello_from_java", result.asString());
        }
    }

    @Test
    void create_nonexistentPath_throwsPythonExecutionException() {
        Path nonexistent = Path.of("/nonexistent/python-embed-venv-" + System.nanoTime());
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> PythonEmbed.create(
                        PythonEmbed.Options.builder()
                                .venvPath(nonexistent).build()));
        assertTrue(ex.getCause() instanceof IOException);
        assertTrue(ex.getCause().getMessage().contains("does not exist"));
    }

    @Test
    void create_pathIsFile_throwsPythonExecutionException(@TempDir Path tempDir) throws Exception {
        Path aFile = tempDir.resolve("not-a-directory.txt");
        Files.createFile(aFile);
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> PythonEmbed.create(
                        PythonEmbed.Options.builder()
                                .venvPath(aFile).build()));
        assertTrue(ex.getCause() instanceof IOException);
        assertTrue(ex.getCause().getMessage().contains("does not exist")
                || ex.getCause().getMessage().contains("not a directory"));
    }

    // ------------------------------------------------------------------
    // Stream
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_rangeYieldsValues() {
        assumeVenvExists();
        Iterator<PythonValue> iter = py.stream("range(3)");
        assertTrue(iter.hasNext());
        assertEquals(0, iter.next().asInt());
        assertEquals(1, iter.next().asInt());
        assertEquals(2, iter.next().asInt());
        assertFalse(iter.hasNext());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_listYieldsValues() {
        assumeVenvExists();
        Iterator<PythonValue> iter = py.stream("[10, 20, 30]");
        assertTrue(iter.hasNext());
        assertEquals(10, iter.next().asInt());
        assertEquals(20, iter.next().asInt());
        assertEquals(30, iter.next().asInt());
        assertFalse(iter.hasNext());
    }

    // ------------------------------------------------------------------
    // ref
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ref_createsHandle() {
        assumeVenvExists();
        py.exec("msg = 'explicit path test'");
        PythonHandle handle = py.ref("msg");
        assertEquals("str", handle.pythonType());
        assertEquals(0, handle.call("find", "explicit").asInt());
    }

    // ------------------------------------------------------------------
    // ping
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ping_healthyProcess_returnsTrue() {
        assumeVenvExists();
        assertTrue(py.ping());
    }

    // ------------------------------------------------------------------
    // close with custom timeout
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void close_withCustomTimeout_succeeds() {
        Path venvPath = Path.of("build", "python-venv");
        if (!venvPath.toFile().exists()) {
            return;
        }
        PythonEmbed localPy = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(venvPath).build());
        localPy.close(1, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------
    // Callback
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void registerCallback_pythonCallsBack() {
        assumeVenvExists();
        py.registerCallback("double", args -> {
            int value = ((Number) args[0]).intValue();
            return value * 2;
        });
        py.exec("""
                def call_double(n):
                    return _bridge.call('double', n)
                """);
        PythonValue result = py.eval("call_double(21)");
        assertEquals(42, result.asInt());
    }

    // ------------------------------------------------------------------
    // Push handler
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void registerPushHandler_pythonPushesValue() throws Exception {
        assumeVenvExists();
        final Object[] received = new Object[1];
        py.registerPushHandler("notify", (name, value) -> received[0] = value);
        py.exec("_bridge.push('notify', 'pushed-value')");
        // Give the push a moment to propagate
        Thread.sleep(200);
        assertEquals("pushed-value", received[0]);
    }

    // ------------------------------------------------------------------
    // Options with explicit path
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void create_withOptions_customTimeout() {
        Path venvPath = Path.of("build", "python-venv");
        if (!venvPath.toFile().exists()) {
            return;
        }
        try (PythonEmbed pyOpts = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .timeoutMs(60_000)
                        .venvPath(venvPath)
                        .build())) {
            assertEquals(42, pyOpts.eval("42").asInt());
        }
    }

    private void assumeVenvExists() {
        Assumptions.assumeTrue(py != null, "Venv not found at build/python-venv -- "
                + "run 'gradle :python-embed-runtime:createVenv' first");
    }
}
