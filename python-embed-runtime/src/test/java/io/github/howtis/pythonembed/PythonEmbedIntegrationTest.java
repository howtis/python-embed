package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    static void setUp() {
        py = PythonEmbed.create(PythonEmbed.Options.defaults());
    }

    @AfterAll
    static void tearDown() {
        py.close();
    }

    @BeforeEach
    void clearState() {
        py.exec("globals().clear()");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_simpleExpression_returnsInt() {
        PythonValue result = py.eval("sum([1, 2, 3])");
        assertEquals(6, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_withState_fromExec() {
        py.exec("x = 10");
        py.exec("y = 20");
        PythonValue result = py.eval("x + y");
        assertEquals(30, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_stringResult() {
        PythonValue result = py.eval("'hello'.upper()");
        assertEquals("HELLO", result.asString());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_booleanResult() {
        PythonValue result = py.eval("1 + 1 == 2");
        assertTrue(result.asBoolean());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_doubleResult() {
        PythonValue result = py.eval("3.14 * 2");
        assertEquals(6.28, result.asDouble(), 0.001);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_listResult() {
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
    void exec_multiline_functionDef() {
        py.exec("""
                def add(a, b):
                    return a + b
                """);
        PythonValue result = py.eval("add(3, 4)");
        assertEquals(7, result.asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_multiline_ifElse() {
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
    void exec_multiline_forLoop() {
        py.exec("""
                total = 0
                for i in range(1, 4):
                    total += i * i
                """);
        assertEquals(14, py.eval("total").asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_classDef_multiline() {
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
    void multipleEval_preservesState() {
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
    void options_maxCodeLength_rejectsOversizedCode() {
        try (PythonEmbed py2 = PythonEmbed.create(
                        PythonEmbed.Options.builder()
                                .maxCodeLength(10)
                                .build())) {
            PythonExecutionException ex = assertThrows(
                    PythonExecutionException.class,
                    () -> py2.eval("12345678901")
            );
            assertTrue(ex.getMessage().contains("maximum length"));
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_maxCodeLength_allowsCodeWithinLimit() {
        try (PythonEmbed py2 = PythonEmbed.create(
                        PythonEmbed.Options.builder()
                                .maxCodeLength(10)
                                .build())) {
            PythonValue result = py2.eval("42");
            assertEquals(42, result.asInt());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_startupTimeout_succeedsWithValidProcess() {
        try (PythonEmbed py2 = PythonEmbed.create(
                        PythonEmbed.Options.builder()
                                .startupTimeoutMs(10_000)
                                .build())) {
            assertTrue(py2.eval("True").asBoolean());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_startupTimeout_failsWithInvalidExecutable() {
        assertThrows(PythonExecutionException.class, () -> {
            try (PythonEmbed py2 = PythonEmbed.create(
                            PythonEmbed.Options.builder()
                                    .pythonExecutable("/nonexistent/python")
                                    .startupTimeoutMs(3_000)
                                    .build())) {
                py2.eval("True");
            }
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_customTimeout_takesEffect() {
        try (PythonEmbed py2 = PythonEmbed.create(
                        PythonEmbed.Options.builder()
                                .timeoutMs(60_000)
                                .build())) {
            assertEquals(42, py2.eval("42").asInt());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_defaults_workWithoutBuilder() {
        try (PythonEmbed py2 = PythonEmbed.create(PythonEmbed.Options.defaults())) {
            assertEquals(42, py2.eval("42").asInt());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void options_withExplicitVenvPath() {
        // Test that builder with a non-existent venv path throws PythonExecutionException
        assertThrows(PythonExecutionException.class, () -> PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(Path.of("/nonexistent/venv"))
                        .build()));
    }

    // ---- Ping / health check tests ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ping_healthyProcess_returnsTrue() {
        assertTrue(py.ping(), "ping() should return true for a healthy process");
    }

    // ---- Log forwarding tests ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void log_warningLevel_forwardsToSLF4J() {
        // Python log forwarding should not throw and should be handled by routeLog
        py.exec("import logging; logging.getLogger('test.logger').warning('test warning message')");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void log_allLevels_smokeTest() {
        py.exec("import logging");
        py.exec("logging.getLogger('test.debug').debug('debug msg')");
        py.exec("logging.getLogger('test.info').info('info msg')");
        py.exec("logging.getLogger('test.warning').warning('warning msg')");
        py.exec("logging.getLogger('test.error').error('error msg')");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void log_forwarding_disabledWhenNoBinding() {
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
    void log_forwarding_enabledWhenBindingPresent() {
        assertTrue(MsgpackProtocol.LOG_FORWARDING_AVAILABLE,
                "Log forwarding should be enabled when SLF4J binding is present");
    }

    // ---- Enhanced health check tests ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void health_returnsValidData() {
        HealthInfo info = py.health();
        assertNotNull(info);
        assertTrue(info.memoryRssKb() >= 0, "memory_rss_kb should be >= 0 (0 on platforms without resource module)");
        assertTrue(info.refCount() >= 0, "ref_count should be >= 0");
        assertNotNull(info.gcCounts());
        assertEquals(3, info.gcCounts().size(), "gc_counts should have 3 generations");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void health_refCount_increasesWithRef() {
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
    void health_refCount_decreasesAfterRelease() {
        PythonHandle handle = py.ref("42");
        HealthInfo withRef = py.health();
        int refCount = withRef.refCount();

        handle.release();
        HealthInfo afterRelease = py.health();
        assertEquals(refCount - 1, afterRelease.refCount(), "ref_count should decrease after release");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void health_gcEnabled_returnsTrue() {
        HealthInfo info = py.health();
        assertTrue(info.gcEnabled(), "gc should be enabled by default");
    }

    // ---- Traceback tests ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void traceback_nameErrorIncludesTraceback() {
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
    void traceback_syntaxErrorIncludesTraceback() {
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
    void traceback_getMessageStillContainsFirstLine() {
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
    void timeoutOverride_shortTimeout_throwsPythonExecutionException() {
        // Use a very short timeout for a long operation
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.eval("__import__('time').sleep(10)", 100));
        assertTrue(ex.getCause() instanceof TimeoutException);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_longTimeout_succeeds() {
        // Long timeout allows the sleep to complete
        PythonValue result = py.eval("__import__('time').sleep(0.5) or 42", 5_000);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_zeroTimeout_fallsBackToDefault() {
        // Zero timeout should fall back to default (30s), so this should succeed
        PythonValue result = py.eval("42", 0);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_negativeTimeout_fallsBackToDefault() {
        // Negative timeout should fall back to default
        PythonValue result = py.eval("42", -1);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_exec_withTimeout() {
        // exec should work with timeout override
        py.exec("x = 99", 5_000);
        assertEquals(99, py.eval("x").asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeoutOverride_exec_shortTimeout_throws() {
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.exec("__import__('time').sleep(10)", 100));
        assertTrue(ex.getCause() instanceof TimeoutException);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_stream_withTimeout() {
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
    void stream_withDefaultTimeout() {
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
    void batchEval_multipleExpressions_returnsAllResults() {
        List<String> codes = List.of("10 + 20", "3.14 * 2", "'hello'.upper()");
        List<PythonValue> results = py.batchEval(codes);
        assertEquals(3, results.size());
        assertEquals(30, results.get(0).asInt());
        assertEquals(6.28, results.get(1).asDouble(), 0.001);
        assertEquals("HELLO", results.get(2).asString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_dependentExpressions_worksSequentially() {
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
    void batchEval_emptyList_returnsEmptyResult() {
        List<PythonValue> results = py.batchEval(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_errorInMiddle_throwsWithIndex() {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.batchEval(List.of("1 + 1", "undefined_var", "2 + 2"))
        );
        assertTrue(ex.getMessage().contains("batchEval[1]"));
        assertTrue(ex.getMessage().contains("NameError"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_multipleStatements_preservesState() {
        py.batchExec(List.of("x = 10", "y = 20", "z = x + y"));
        assertEquals(30, py.eval("z").asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_emptyList_doesNothing() {
        py.batchExec(List.of());
        // Should not throw
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_error_throwsWithIndex() {
        PythonExecutionException ex = assertThrows(
                PythonExecutionException.class,
                () -> py.batchExec(List.of("x = 1", "raise ValueError('test error')", "y = 2"))
        );
        assertTrue(ex.getMessage().contains("batchExec[1]"));
        assertTrue(ex.getMessage().contains("ValueError"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_thenEval_stateIsPreserved() {
        py.batchExec(List.of("a = 100", "b = 200"));
        assertEquals(300, py.eval("a + b").asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_withTimeoutOverride() {
        List<PythonValue> results = py.batchEval(List.of("1 + 1", "2 + 2"), 5000);
        assertEquals(2, results.size());
        assertEquals(2, results.get(0).asInt());
        assertEquals(4, results.get(1).asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_withTimeoutOverride() {
        py.batchExec(List.of("m = 5", "n = 6"), 5000);
        assertEquals(11, py.eval("m + n").asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_timeoutZero_usesDefault() {
        // timeoutMs <= 0 falls back to configured default timeout
        List<PythonValue> results = py.batchEval(List.of("5 * 5", "10 + 3"), 0);
        assertEquals(2, results.size());
        assertEquals(25, results.get(0).asInt());
        assertEquals(13, results.get(1).asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchEval_timeoutNegative_usesDefault() {
        // Negative timeout falls back to configured default
        List<PythonValue> results = py.batchEval(List.of("7 + 3", "2 * 9"), -1);
        assertEquals(2, results.size());
        assertEquals(10, results.get(0).asInt());
        assertEquals(18, results.get(1).asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_timeoutZero_usesDefault() {
        // timeoutMs <= 0 falls back to configured default timeout
        py.batchExec(List.of("p = 3", "q = 7"), 0);
        assertEquals(10, py.eval("p + q").asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void batchExec_timeoutNegative_usesDefault() {
        // Negative timeout falls back to configured default
        py.batchExec(List.of("r = 8", "s = 9"), -1);
        assertEquals(17, py.eval("r + s").asInt());
    }

    // ---- Warmup tests ----

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_importsModule() {
        PythonEmbed embed = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .warmupScript("import math")
                        .venvPath(Path.of("build", "python-venv"))
                        .build());
        try {
            PythonValue result = embed.eval("math.pi");
            assertEquals(3.141592653589793, result.asDouble(), 0.0001);
        } finally {
            embed.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_scriptFailureDoesNotBreakInstance() {
        PythonEmbed embed = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .warmupScript("raise RuntimeError('test warmup error')")
                        .venvPath(Path.of("build", "python-venv"))
                        .build());
        try {
            PythonValue result = embed.eval("42");
            assertEquals(42, result.asInt());
        } finally {
            embed.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_explicitMethodCall() {
        PythonEmbed embed = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(Path.of("build", "python-venv")).build());
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
    void causeCode_evalErrorIncludesCode() {
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
    void causeCode_execErrorIncludesCode() {
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
    void causeCode_batchEvalErrorIncludesCode() {
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
    void causeCode_batchExecErrorIncludesCode() {
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
    void pythonValue_isNull_returnsTrueForNone() {
        PythonValue value = py.eval("None");
        assertTrue(value.isNull());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_isNull_returnsFalseForNonNull() {
        PythonValue value = py.eval("42");
        assertFalse(value.isNull());
        assertEquals(42, value.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_noneToInt_throwsIllegalStateException() {
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
    void pythonValue_noneToBoolean_throwsIllegalStateException() {
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
    void pythonValue_noneToList_throwsIllegalStateException() {
        PythonValue value = py.eval("None");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                value::asList
        );
        assertTrue(ex.getMessage().contains("Python returned None"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pythonValue_noneAsString_returnsNullString() {
        PythonValue value = py.eval("None");
        assertEquals("null", value.asString());
    }

    // ---- Warmup strict mode tests (Phase 1, #10) ----

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmup_lenientWarmupDefault_doesNotThrowOnError() {
        // Default lenientWarmup=true: warmup failure should not prevent startup
        PythonEmbed embed = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .warmupScript("raise RuntimeError('warmup error')")
                        .venvPath(Path.of("build", "python-venv"))
                        .build());
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
                PythonExecutionException.class,
                () -> PythonEmbed.create(
                        PythonEmbed.Options.builder()
                                .warmupScript("raise RuntimeError('warmup error')")
                                .lenientWarmup(false)
                                .venvPath(Path.of("build", "python-venv"))
                                .build())
        );
    }

    // ---- PythonEmbed.arg() integration tests ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_safeStringLen() {
        PythonValue result = py.eval("len(" + PythonEmbed.arg("safe") + ")");
        assertEquals(4, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_safeIntEval() {
        PythonValue result = py.eval("[" + PythonEmbed.arg(10) + ", " + PythonEmbed.arg(20) + "]");
        List<Double> list = result.asList(Double.class);
        assertEquals(List.of(10.0, 20.0), list);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_listViaExec() {
        py.exec("x = " + PythonEmbed.arg(List.of(1, 2, 3)));
        PythonValue result = py.eval("x");
        List<Double> list = result.asList(Double.class);
        assertEquals(List.of(1.0, 2.0, 3.0), list);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_mapViaExec() {
        py.exec("d = " + PythonEmbed.arg(Map.of("name", "test", "count", 42)));
        PythonValue name = py.eval("d['name']");
        assertEquals("test", name.asString());
        PythonValue count = py.eval("d['count']");
        assertEquals(42, count.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_sqlInjection_blocked() {
        // Classic SQL injection pattern should be treated as data, not code
        String malicious = "'; import os; os.system('echo hacked') #";
        String code = "len(" + PythonEmbed.arg(malicious) + ")";
        // arg() escapes quotes so the entire payload is a string literal.
        // len() returns the string length -- no code execution occurs.
        PythonValue result = py.eval(code);
        assertTrue(result.asInt() > 0, "Expected positive length, got: " + result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_pythonInjection_blocked() {
        // Python-specific injection: breaking out of a string context
        String malicious = "'); import os; os.system('echo pwned') #";
        String code = "len(" + PythonEmbed.arg(malicious) + ")";
        // arg() escapes the single quotes, so the string is safe
        PythonValue result = py.eval(code);
        // len() of the escaped string -- no code execution
        assertTrue(result.asInt() > 0, "Expected length > 0 for escaped string");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_stringWithSpecialChars_roundtrip() {
        String input = "line1\nline2\tindented\\path'quote";
        py.exec("s = " + PythonEmbed.arg(input));
        PythonValue result = py.eval("s");
        assertEquals(input, result.asString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_emptyString() {
        PythonValue result = py.eval(PythonEmbed.arg(""));
        assertEquals("", result.asString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_noneValue() {
        py.exec("v = " + PythonEmbed.arg(null));
        PythonValue result = py.eval("v is None");
        assertTrue(result.asBoolean());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_boolValues() {
        py.exec("t = " + PythonEmbed.arg(true));
        py.exec("f = " + PythonEmbed.arg(false));
        assertTrue(py.eval("t").asBoolean());
        assertFalse(py.eval("f").asBoolean());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_doubleSpecialValues() {
        py.exec("nan = " + PythonEmbed.arg(Double.NaN));
        py.exec("inf = " + PythonEmbed.arg(Double.POSITIVE_INFINITY));
        py.exec("ninf = " + PythonEmbed.arg(Double.NEGATIVE_INFINITY));
        py.exec("import math");
        // Verify the values are recognized as special floats in Python
        assertTrue(py.eval("math.isnan(nan)").asBoolean());
        assertTrue(py.eval("inf == float('inf')").asBoolean());
        assertTrue(py.eval("ninf == float('-inf')").asBoolean());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void arg_nestedCollections() {
        py.exec("data = " + PythonEmbed.arg(
                List.of(Map.of("name", "alice", "scores", List.of(90, 85, 95)))));
        PythonValue name = py.eval("data[0]['name']");
        assertEquals("alice", name.asString());
        PythonValue secondScore = py.eval("data[0]['scores'][1]");
        assertEquals(85.0, secondScore.asDouble(), 0.001);
    }

    // ---- warmupScripts(List) ----

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void warmupScripts_multipleScripts_viaBuilder() {
        PythonEmbed embed = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .warmupScripts(List.of("import math", "import json"))
                        .venvPath(Path.of("build", "python-venv"))
                        .build());
        try {
            // Both imports should have executed
            PythonValue pi = embed.eval("math.pi");
            assertEquals(3.141592653589793, pi.asDouble(), 0.0001);
            String result = embed.eval("json.dumps({'key': 'value'})").asString();
            assertTrue(result.contains("key"));
        } finally {
            embed.close();
        }
    }

    // ---- getPid() ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void getPid_returnsPositiveWhileRunning() {
        long pid = py.getPid();
        assertTrue(pid > 0, "Expected positive PID, got: " + pid);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void getPid_afterClose_stillReturnsPid() {
        PythonEmbed embed = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(Path.of("build", "python-venv"))
                        .build());
        long pid = embed.getPid();
        assertTrue(pid > 0, "Embed should have valid PID while open");
        embed.close();
        // After close, getPid() still returns the historical PID;
        // isOpen() is the authoritative closed-state check.
        assertEquals(pid, embed.getPid());
        assertFalse(embed.isOpen());
    }

    // ---- toJson() from eval ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void toJson_dictFromPython() {
        py.exec("import json");
        PythonValue v = py.eval("json.loads('{\"name\": \"Alice\", \"age\": 30}')");
        String json = v.toJson();
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"Alice\""));
        assertTrue(json.contains("\"age\""));
        assertTrue(json.contains("30"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void toJson_listFromPython() {
        PythonValue v = py.eval("[1, 2, 3]");
        assertEquals("[1, 2, 3]", v.toJson());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void toJson_primitiveFromPython() {
        PythonValue v = py.eval("42");
        assertEquals("42", v.toJson());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void toJson_nullFromPython() {
        PythonValue v = py.eval("None");
        assertEquals("null", v.toJson());
    }

    // ---- execFile ----

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void execFile_simpleScript() throws Exception {
        Path scriptFile = Files.createTempFile("pyembed-test", ".py");
        try {
            Files.writeString(scriptFile, "x = 42\ny = 58");
            py.execFile(scriptFile);
            assertEquals(100, py.eval("x + y").asInt());
        } finally {
            Files.deleteIfExists(scriptFile);
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void execFile_withTimeoutOverride() throws Exception {
        Path scriptFile = Files.createTempFile("pyembed-test", ".py");
        try {
            Files.writeString(scriptFile, "z = 99");
            py.execFile(scriptFile, 10_000);
            assertEquals(99, py.eval("z").asInt());
        } finally {
            Files.deleteIfExists(scriptFile);
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void execFile_fileNotFound_throwsIOException() {
        Path missing = Path.of("nonexistent_script_12345.py");
        assertThrows(IOException.class, () -> py.execFile(missing));
    }

    // ---- eval(Map) with variables ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void eval_withVariables_simpleMath() {
        PythonValue result = py.eval(Map.of("x", 10, "y", 20), "x + y");
        assertEquals(30, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void eval_withVariables_stringManipulation() {
        PythonValue result = py.eval(
                Map.of("name", "World"), "'Hello, ' + name");
        assertEquals("Hello, World", result.asString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void eval_withVariables_emptyMap() {
        PythonValue result = py.eval(Map.of(), "1 + 1");
        assertEquals(2, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void eval_withVariables_andTimeout() {
        PythonValue result = py.eval(Map.of("a", 5), "a * a", 10_000);
        assertEquals(25, result.asInt());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void exec_withVariables_stateIsPreserved() {
        py.exec(Map.of("greeting", "hi"), "result = greeting + ' there'");
        assertEquals("hi there", py.eval("result").asString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void exec_withVariables_andTimeout() {
        py.exec(Map.of("n", 7), "s = n * n", 10_000);
        assertEquals(49, py.eval("s").asInt());
    }

    // ---- Datetime eval round-trip ----

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void arg_localDateTime_roundtrip() {
        py.warmup("import datetime");
        LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 0, 0);
        py.exec("result = " + PythonEmbed.arg(dt));
        PythonValue year = py.eval("result.year");
        assertEquals(2024, year.asInt());
        PythonValue month = py.eval("result.month");
        assertEquals(6, month.asInt());
        PythonValue day = py.eval("result.day");
        assertEquals(15, day.asInt());
        PythonValue hour = py.eval("result.hour");
        assertEquals(14, hour.asInt());
    }

    // ---- stream() edge cases ----

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void stream_afterClose_throwsException() {
        PythonEmbed embed = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(Path.of("build", "python-venv"))
                        .build());
        embed.close();
        assertThrows(PythonExecutionException.class, () -> embed.stream("range(3)"));
    }
}
