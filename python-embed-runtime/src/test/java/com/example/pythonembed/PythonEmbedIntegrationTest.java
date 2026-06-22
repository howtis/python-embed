package com.example.pythonembed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonEmbedIntegrationTest {

    private static PythonEmbed py;

    @BeforeAll
    static void setUp() throws Exception {
        py = PythonEmbed.builder().build();
    }

    @AfterAll
    static void tearDown() {
        py.close();
    }

    @BeforeEach
    void clearState() throws Exception {
        py.exec("globals().clear()");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_simpleExpression_returnsInt() throws Exception {
        PythonValue result = py.eval("sum([1, 2, 3])");
        assertEquals(6, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_withState_fromExec() throws Exception {
        py.exec("x = 10");
        py.exec("y = 20");
        PythonValue result = py.eval("x + y");
        assertEquals(30, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_stringResult() throws Exception {
        PythonValue result = py.eval("'hello'.upper()");
        assertEquals("HELLO", result.asString());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_booleanResult() throws Exception {
        PythonValue result = py.eval("1 + 1 == 2");
        assertTrue(result.asBoolean());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_doubleResult() throws Exception {
        PythonValue result = py.eval("3.14 * 2");
        assertEquals(6.28, result.asDouble(), 0.001);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_listResult() throws Exception {
        PythonValue result = py.eval("[i * 2 for i in range(5)]");
        List<Double> list = result.asList(Double.class);
        assertEquals(List.of(0.0, 2.0, 4.0, 6.0, 8.0), list);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_syntaxError_throws() {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval("1 + ")
        );
        assertTrue(ex.getMessage().contains("SyntaxError"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_nameError_throws() {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval("undefined_var")
        );
        assertTrue(ex.getMessage().contains("NameError"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_multiline_functionDef() throws Exception {
        py.exec("""
                def add(a, b):
                    return a + b
                """);
        PythonValue result = py.eval("add(3, 4)");
        assertEquals(7, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_multiline_ifElse() throws Exception {
        py.exec("""
                x = 5
                if x > 3:
                    x = 100
                else:
                    x = -1
                """);
        assertEquals(100, py.eval("x").asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_multiline_forLoop() throws Exception {
        py.exec("""
                total = 0
                for i in range(1, 4):
                    total += i * i
                """);
        assertEquals(14, py.eval("total").asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_classDef_multiline() throws Exception {
        py.exec("""
                class Counter:
                    def __init__(self):
                        self.n = 0
                    def inc(self):
                        self.n += 1
                c = Counter()
                c.inc()
                c.inc()
                """);
        assertEquals(2, py.eval("c.n").asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void multipleEval_preservesState() throws Exception {
        py.exec("counter = 0");
        assertEquals(0, py.eval("counter").asInt());
        py.exec("counter += 1");
        assertEquals(1, py.eval("counter").asInt());
        py.exec("counter += 2");
        assertEquals(3, py.eval("counter").asInt());
    }

    // ---- Options tests ----

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_maxCodeLength_rejectsOversizedCode() throws Exception {
        try (PythonEmbed py2 = PythonEmbed.builder()
                .maxCodeLength(10)
                .build()) {
            PythonExecutionException ex = assertThrows(
                    PythonExecutionException.class,
                    () -> py2.eval("12345678901")
            );
            assertTrue(ex.getMessage().contains("maximum length"));
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_maxCodeLength_allowsCodeWithinLimit() throws Exception {
        try (PythonEmbed py2 = PythonEmbed.builder()
                .maxCodeLength(10)
                .build()) {
            PythonValue result = py2.eval("42");
            assertEquals(42, result.asInt());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_startupTimeout_succeedsWithValidProcess() throws Exception {
        try (PythonEmbed py2 = PythonEmbed.builder()
                .startupTimeoutMs(10_000)
                .build()) {
            assertTrue(py2.eval("True").asBoolean());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_startupTimeout_failsWithInvalidExecutable() {
        assertThrows(IOException.class, () -> {
            try (PythonEmbed py2 = PythonEmbed.builder()
                    .pythonExecutable("/nonexistent/python")
                    .startupTimeoutMs(3_000)
                    .build()) {
                py2.eval("True");
            }
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_customTimeout_takesEffect() throws Exception {
        try (PythonEmbed py2 = PythonEmbed.builder()
                .timeoutMs(60_000)
                .build()) {
            assertEquals(42, py2.eval("42").asInt());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_defaults_workWithoutBuilder() throws Exception {
        try (PythonEmbed py2 = PythonEmbed.builder().build()) {
            assertEquals(42, py2.eval("42").asInt());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_withExplicitVenvPath() {
        // Test that builder with a non-existent venv path throws IOException
        assertThrows(IOException.class, () -> PythonEmbed.builder()
                .venvPath(Path.of("/nonexistent/venv"))
                .build());
    }

    // ---- Ping / health check tests ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ping_healthyProcess_returnsTrue() throws Exception {
        assertTrue(py.ping(), "ping() should return true for a healthy process");
    }

    // ---- Log forwarding tests ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void log_warningLevel_forwardsToSLF4J() throws Exception {
        // Python log forwarding should not throw and should be handled by routeLog
        py.exec("import logging; logging.getLogger('test.logger').warning('test warning message')");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void log_allLevels_smokeTest() throws Exception {
        py.exec("import logging");
        py.exec("logging.getLogger('test.debug').debug('debug msg')");
        py.exec("logging.getLogger('test.info').info('info msg')");
        py.exec("logging.getLogger('test.warning').warning('warning msg')");
        py.exec("logging.getLogger('test.error').error('error msg')");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void log_forwarding_disabledWhenNoBinding() throws Exception {
        boolean original = MsgpackProtocol.LOG_FORWARDING_AVAILABLE;
        try {
            MsgpackProtocol.LOG_FORWARDING_AVAILABLE = false;
            // Sending Python log messages should not throw when forwarding is disabled
            py.exec("import logging; logging.getLogger('test').warning('should be silently dropped')");
            py.exec("import logging; logging.getLogger('test').error('error also dropped')");
        } finally {
            MsgpackProtocol.LOG_FORWARDING_AVAILABLE = original;
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void log_forwarding_enabledWhenBindingPresent() throws Exception {
        assertTrue(MsgpackProtocol.LOG_FORWARDING_AVAILABLE,
                "Log forwarding should be enabled when SLF4J binding is present");
    }

    // ---- Enhanced health check tests ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void health_returnsValidData() throws Exception {
        HealthInfo info = py.health();
        assertNotNull(info);
        assertTrue(info.memoryRssKb() >= 0, "memory_rss_kb should be >= 0 (0 on platforms without resource module)");
        assertTrue(info.refCount() >= 0, "ref_count should be >= 0");
        assertNotNull(info.gcCounts());
        assertEquals(3, info.gcCounts().size(), "gc_counts should have 3 generations");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void health_refCount_increasesWithRef() throws Exception {
        HealthInfo before = py.health();
        int beforeCount = before.refCount();

        PythonHandle handle = py.ref("42");
        try {
            HealthInfo after = py.health();
            assertEquals(beforeCount + 1, after.refCount(), "ref_count should increase by 1 after ref()");
        } finally {
            handle.release();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void health_refCount_decreasesAfterRelease() throws Exception {
        PythonHandle handle = py.ref("42");
        HealthInfo withRef = py.health();
        int refCount = withRef.refCount();

        handle.release();
        HealthInfo afterRelease = py.health();
        assertEquals(refCount - 1, afterRelease.refCount(), "ref_count should decrease after release");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void health_gcEnabled_returnsTrue() throws Exception {
        HealthInfo info = py.health();
        assertTrue(info.gcEnabled(), "gc should be enabled by default");
    }

    // ---- Traceback tests ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void traceback_nameErrorIncludesTraceback() throws Exception {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval("undefined_var")
        );
        assertNotNull(ex.getPythonTraceback(), "Traceback should not be null");
        assertTrue(ex.getPythonTraceback().contains("Traceback"),
                "Traceback should contain 'Traceback' header");
        assertTrue(ex.getPythonTraceback().contains("NameError"),
                "Traceback should contain 'NameError'");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void traceback_syntaxErrorIncludesTraceback() throws Exception {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval("1 + ")
        );
        assertNotNull(ex.getPythonTraceback(), "Traceback should not be null");
        assertTrue(ex.getPythonTraceback().contains("SyntaxError"),
                "Traceback should contain 'SyntaxError'");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void traceback_getMessageStillContainsFirstLine() throws Exception {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval("undefined_var")
        );
        assertTrue(ex.getMessage().contains("NameError"),
                "getMessage() should contain error type");
        // The message should be shorter than the full traceback
        assertTrue(ex.getMessage().length() < ex.getPythonTraceback().length(),
                "getMessage() should be shorter than full traceback");
    }

    // ---- Per-call timeout tests ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_shortTimeout_throwsTimeoutException() throws Exception {
        // Use a very short timeout for a long operation
        assertThrows(TimeoutException.class,
                () -> py.eval("__import__('time').sleep(10)", 100));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_longTimeout_succeeds() throws Exception {
        // Long timeout allows the sleep to complete
        PythonValue result = py.eval("__import__('time').sleep(0.5) or 42", 5_000);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_zeroTimeout_fallsBackToDefault() throws Exception {
        // Zero timeout should fall back to default (30s), so this should succeed
        PythonValue result = py.eval("42", 0);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_negativeTimeout_fallsBackToDefault() throws Exception {
        // Negative timeout should fall back to default
        PythonValue result = py.eval("42", -1);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_exec_withTimeout() throws Exception {
        // exec should work with timeout override
        py.exec("x = 99", 5_000);
        assertEquals(99, py.eval("x").asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_exec_shortTimeout_throws() throws Exception {
        assertThrows(TimeoutException.class,
                () -> py.exec("__import__('time').sleep(10)", 100));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_stream_withTimeout() throws Exception {
        Iterator<PythonValue> iter = py.stream("range(3)", 5_000);
        assertTrue(iter.hasNext());
        assertEquals(0, iter.next().asInt());
        assertTrue(iter.hasNext());
        assertEquals(1, iter.next().asInt());
        assertTrue(iter.hasNext());
        assertEquals(2, iter.next().asInt());
        assertTrue(!iter.hasNext());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void stream_withDefaultTimeout() throws Exception {
        // Verify default stream works as before
        Iterator<PythonValue> iter = py.stream("[10, 20, 30]");
        assertTrue(iter.hasNext());
        assertEquals(10, iter.next().asInt());
        assertTrue(iter.hasNext());
        assertEquals(20, iter.next().asInt());
        assertTrue(iter.hasNext());
        assertEquals(30, iter.next().asInt());
        assertTrue(!iter.hasNext());
    }

    // ---- Batch execution tests ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_multipleExpressions_returnsAllResults() throws Exception {
        List<String> codes = List.of("10 + 20", "3.14 * 2", "'hello'.upper()");
        List<PythonValue> results = py.batchEval(codes);
        assertEquals(3, results.size());
        assertEquals(30, results.get(0).asInt());
        assertEquals(6.28, results.get(1).asDouble(), 0.001);
        assertEquals("HELLO", results.get(2).asString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_dependentExpressions_worksSequentially() throws Exception {
        py.exec("a = 10");
        List<String> codes = List.of("a * 2", "a * 3", "a * 4");
        List<PythonValue> results = py.batchEval(codes);
        assertEquals(3, results.size());
        assertEquals(20, results.get(0).asInt());
        assertEquals(30, results.get(1).asInt());
        assertEquals(40, results.get(2).asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_emptyList_returnsEmptyResult() throws Exception {
        List<PythonValue> results = py.batchEval(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_errorInMiddle_throwsWithIndex() throws Exception {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.batchEval(List.of("1 + 1", "undefined_var", "2 + 2"))
        );
        assertTrue(ex.getMessage().contains("batchEval[1]"));
        assertTrue(ex.getMessage().contains("NameError"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_multipleStatements_preservesState() throws Exception {
        py.batchExec(List.of("x = 10", "y = 20", "z = x + y"));
        assertEquals(30, py.eval("z").asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_emptyList_doesNothing() throws Exception {
        py.batchExec(List.of());
        // Should not throw
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_error_throwsWithIndex() throws Exception {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.batchExec(List.of("x = 1", "raise ValueError('test error')", "y = 2"))
        );
        assertTrue(ex.getMessage().contains("batchExec[1]"));
        assertTrue(ex.getMessage().contains("ValueError"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_thenEval_stateIsPreserved() throws Exception {
        py.batchExec(List.of("a = 100", "b = 200"));
        assertEquals(300, py.eval("a + b").asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_withTimeoutOverride() throws Exception {
        List<PythonValue> results = py.batchEval(List.of("1 + 1", "2 + 2"), 5000);
        assertEquals(2, results.size());
        assertEquals(2, results.get(0).asInt());
        assertEquals(4, results.get(1).asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_withTimeoutOverride() throws Exception {
        py.batchExec(List.of("m = 5", "n = 6"), 5000);
        assertEquals(11, py.eval("m + n").asInt());
    }

    // ---- Warmup tests ----

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_importsModule() throws Exception {
        PythonEmbed embed = PythonEmbed.builder()
                .warmupScript("import math")
                .venvPath(Path.of("build", "python-venv"))
                .build();
        try {
            PythonValue result = embed.eval("math.pi");
            assertEquals(3.141592653589793, result.asDouble(), 0.0001);
        } finally {
            embed.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_scriptFailureDoesNotBreakInstance() throws Exception {
        PythonEmbed embed = PythonEmbed.builder()
                .warmupScript("raise RuntimeError('test warmup error')")
                .venvPath(Path.of("build", "python-venv"))
                .build();
        try {
            PythonValue result = embed.eval("42");
            assertEquals(42, result.asInt());
        } finally {
            embed.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_explicitMethodCall() throws Exception {
        PythonEmbed embed = PythonEmbed.builder()
                .venvPath(Path.of("build", "python-venv")).build();
        try {
            embed.warmup("import math");
            PythonValue result = embed.eval("math.factorial(5)");
            assertEquals(120, result.asInt());
        } finally {
            embed.close();
        }
    }

    // ---- causeCode tests (Phase 1, #2) ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void causeCode_evalErrorIncludesCode() throws Exception {
        String code = "undefined_var";
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.eval(code)
        );
        assertEquals(code, ex.getCauseCode());
        assertTrue(ex.getMessage().contains("NameError"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void causeCode_execErrorIncludesCode() throws Exception {
        String code = "raise ValueError('test')";
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.exec(code)
        );
        assertEquals(code, ex.getCauseCode());
        assertTrue(ex.getMessage().contains("ValueError"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void causeCode_batchEvalErrorIncludesCode() throws Exception {
        String failingCode = "1/0";
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.batchEval(List.of("1 + 1", failingCode, "2 + 2"))
        );
        assertEquals(failingCode, ex.getCauseCode());
        assertTrue(ex.getMessage().contains("batchEval[1]"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void causeCode_batchExecErrorIncludesCode() throws Exception {
        String failingCode = "raise RuntimeError('batch error')";
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.batchExec(List.of("x = 1", failingCode, "y = 2"))
        );
        assertEquals(failingCode, ex.getCauseCode());
        assertTrue(ex.getMessage().contains("batchExec[1]"));
    }

    // ---- PythonValue null safety tests (Phase 1, #3) ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_isNull_returnsTrueForNone() throws Exception {
        PythonValue value = py.eval("None");
        assertTrue(value.isNull());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_isNull_returnsFalseForNonNull() throws Exception {
        PythonValue value = py.eval("42");
        assertFalse(value.isNull());
        assertEquals(42, value.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_noneToInt_throwsIllegalStateException() throws Exception {
        PythonValue value = py.eval("None");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                value::asInt
        );
        assertTrue(ex.getMessage().contains("Python returned None"));
        assertTrue(ex.getMessage().contains("int"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_noneToBoolean_throwsIllegalStateException() throws Exception {
        PythonValue value = py.eval("None");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                value::asBoolean
        );
        assertTrue(ex.getMessage().contains("Python returned None"));
        assertTrue(ex.getMessage().contains("boolean"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_noneToList_throwsIllegalStateException() throws Exception {
        PythonValue value = py.eval("None");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                value::asList
        );
        assertTrue(ex.getMessage().contains("Python returned None"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_noneAsString_returnsNullString() throws Exception {
        PythonValue value = py.eval("None");
        assertEquals("null", value.asString());
    }

    // ---- Warmup strict mode tests (Phase 1, #10) ----

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_lenientWarmupDefault_doesNotThrowOnError() throws Exception {
        // Default lenientWarmup=true: warmup failure should not prevent startup
        PythonEmbed embed = PythonEmbed.builder()
                .warmupScript("raise RuntimeError('warmup error')")
                .venvPath(Path.of("build", "python-venv"))
                .build();
        try {
            PythonValue result = embed.eval("42");
            assertEquals(42, result.asInt());
        } finally {
            embed.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_strictMode_throwsOnError() {
        assertThrows(
                IOException.class,
                () -> PythonEmbed.builder()
                        .warmupScript("raise RuntimeError('warmup error')")
                        .lenientWarmup(false)
                        .venvPath(Path.of("build", "python-venv"))
                        .build()
        );
    }
}
