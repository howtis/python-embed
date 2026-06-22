package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the MessagePack binary protocol, object reference
 * handles, and streaming generator results.
 */
class PythonEmbedBinaryIntegrationTest {

    private static PythonEmbed py;

    @BeforeAll
    static void setUp() throws Exception {
        py = PythonEmbed.builder()
                .venvPath(Path.of("build", "python-venv")).build();
    }

    @AfterAll
    static void tearDown() {
        py.close();
    }

    @BeforeEach
    void clearState() throws Exception {
        py.exec("globals().clear()");
    }

    // ---- Binary protocol: eval ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_simpleExpression_returnsInt() throws Exception {
        assertEquals(6, py.eval("sum([1, 2, 3])").asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_withState_fromExec() throws Exception {
        py.exec("x = 10");
        py.exec("y = 20");
        assertEquals(30, py.eval("x + y").asInt());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_stringResult() throws Exception {
        assertEquals("HELLO", py.eval("'hello'.upper()").asString());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_booleanResult() throws Exception {
        assertTrue(py.eval("1 + 1 == 2").asBoolean());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_doubleResult() throws Exception {
        assertEquals(6.28, py.eval("3.14 * 2").asDouble(), 0.001);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_listResult() throws Exception {
        List<Double> list = py.eval("[i * 2 for i in range(5)]").asList(Double.class);
        assertEquals(List.of(0.0, 2.0, 4.0, 6.0, 8.0), list);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_largeList_roundtrip() throws Exception {
        PythonValue result = py.eval("list(range(1000))");
        List<Double> list = result.asList(Double.class);
        assertEquals(1000, list.size());
        assertEquals(0.0, list.get(0));
        assertEquals(999.0, list.get(999));
    }

    // ---- Binary protocol: error propagation ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_syntaxError_throws() {
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.eval("1 + "));
        assertTrue(ex.getMessage().contains("SyntaxError"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_nameError_throws() {
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.eval("undefined_var"));
        assertTrue(ex.getMessage().contains("NameError"));
    }

    // ---- Binary protocol: exec and state ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_multiline_functionDef() throws Exception {
        py.exec("def add(a, b):\n    return a + b\n");
        assertEquals(7, py.eval("add(3, 4)").asInt());
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

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_classDef() throws Exception {
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

    // ---- Object reference: ref ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ref_getIntVariable_returnsHandle() throws Exception {
        py.exec("x = 42");
        try (PythonHandle handle = py.ref("x")) {
            assertEquals("int", handle.pythonType());
            assertTrue(handle.refId() > 0);
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ref_getStringVariable_returnsHandle() throws Exception {
        py.exec("s = 'hello'");
        try (PythonHandle handle = py.ref("s")) {
            assertEquals("str", handle.pythonType());
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ref_nonexistentVariable_throws() {
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.ref("nonexistent_var"));
        assertTrue(ex.getMessage().contains("NameError"));
    }

    // ---- Object reference: call ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_noArgMethod_returnsResult() throws Exception {
        py.exec("s = 'hello world'");
        try (PythonHandle handle = py.ref("s")) {
            PythonValue result = handle.call("upper");
            assertEquals("HELLO WORLD", result.asString());
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_methodWithSingleArg_returnsResult() throws Exception {
        py.exec("s = 'hello world'");
        try (PythonHandle handle = py.ref("s")) {
            PythonValue result = handle.call("split", " ");
            List<String> parts = result.asList(String.class);
            assertEquals(List.of("hello", "world"), parts);
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_methodWithMultipleArgs_returnsResult() throws Exception {
        py.exec("s = 'hello world'");
        try (PythonHandle handle = py.ref("s")) {
            PythonValue result = handle.call("replace", "world", "there");
            assertEquals("hello there", result.asString());
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_onClassInstance() throws Exception {
        py.exec("""
                class Adder:
                    def __init__(self, n):
                        self.n = n
                    def add(self, x):
                        return self.n + x
                a = Adder(10)
                """);
        try (PythonHandle handle = py.ref("a")) {
            assertEquals(15, handle.call("add", 5).asInt());
            assertEquals(20, handle.call("add", 10).asInt());
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void call_onListMethod() throws Exception {
        py.exec("lst = [3, 1, 2]");
        try (PythonHandle handle = py.ref("lst")) {
            handle.call("sort");
            List<Double> sorted = py.eval("lst").asList(Double.class);
            assertEquals(List.of(1.0, 2.0, 3.0), sorted);
        }
    }

    // ---- Object reference: getAttr ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void getAttr_simpleAttribute() throws Exception {
        py.exec("""
                class Foo:
                    def __init__(self):
                        self.bar = 99
                f = Foo()
                """);
        try (PythonHandle handle = py.ref("f")) {
            assertEquals(99, handle.getAttr("bar").asInt());
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void getAttr_docAttribute() throws Exception {
        py.exec("""
                def my_func():
                    \"""Documentation string.\"""
                    pass
                """);
        try (PythonHandle handle = py.ref("my_func")) {
            assertTrue(handle.getAttr("__doc__").asString().contains("Documentation"));
        }
    }

    // ---- Object reference: lifecycle ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void release_makesHandleUnusable() throws Exception {
        py.exec("x = 42");
        PythonHandle handle = py.ref("x");
        handle.release();
        assertThrows(IllegalStateException.class, () -> handle.call("__str__"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void close_releasesHandle() throws Exception {
        py.exec("x = 42");
        PythonHandle handle = py.ref("x");
        handle.close();
        assertThrows(IllegalStateException.class, () -> handle.getAttr("real"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void multipleHandles_independent() throws Exception {
        py.exec("a = [1, 2, 3]");
        py.exec("b = 'hello'");
        try (PythonHandle handleA = py.ref("a");
             PythonHandle handleB = py.ref("b")) {
            assertEquals("list", handleA.pythonType());
            assertEquals("str", handleB.pythonType());
            // Operations on one don't affect the other
            assertEquals(3, handleA.call("__len__").asInt());
            assertEquals(5, handleB.call("__len__").asInt());
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void handle_survivesAcrossCalls() throws Exception {
        // Object handles should survive across multiple eval/exec calls
        py.exec("""
                class Counter:
                    def __init__(self):
                        self.n = 0
                counter = Counter()
                """);
        PythonHandle handle = py.ref("counter");
        assertEquals(0, handle.getAttr("n").asInt());
        py.exec("counter.n += 1");
        assertEquals(1, handle.getAttr("n").asInt());
        py.exec("counter.n += 2");
        assertEquals(3, handle.getAttr("n").asInt());
        handle.close();
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void handlesReleasedOnPythonEmbedClose() throws Exception {
        // All handles should be released when PythonEmbed is closed
        PythonEmbed py2 = PythonEmbed.builder()
                .venvPath(Path.of("build", "python-venv")).build();
        py2.exec("x = [1, 2, 3]");
        PythonHandle handle = py2.ref("x");
        assertEquals("list", handle.pythonType());
        py2.close();
        // After close, handle should be released
        assertThrows(IllegalStateException.class, () -> handle.call("__len__"));
    }

    // ---- Streaming ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_list_returnsAllItems() throws Exception {
        Iterator<PythonValue> iter = py.stream("[1, 2, 3]");
        List<Integer> items = new ArrayList<>();
        while (iter.hasNext()) {
            items.add(iter.next().asInt());
        }
        assertEquals(List.of(1, 2, 3), items);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_generator_returnsAllItems() throws Exception {
        Iterator<PythonValue> iter = py.stream("(i * 2 for i in range(5))");
        List<Integer> items = new ArrayList<>();
        while (iter.hasNext()) {
            items.add(iter.next().asInt());
        }
        assertEquals(List.of(0, 2, 4, 6, 8), items);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_range_generator() throws Exception {
        Iterator<PythonValue> iter = py.stream("range(3)");
        List<Integer> items = new ArrayList<>();
        while (iter.hasNext()) {
            items.add(iter.next().asInt());
        }
        assertEquals(List.of(0, 1, 2), items);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_emptyList_returnsNoItems() throws Exception {
        Iterator<PythonValue> iter = py.stream("[]");
        assertFalse(iter.hasNext());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_singleValue_returnsOneItem() throws Exception {
        Iterator<PythonValue> iter = py.stream("42");
        assertTrue(iter.hasNext());
        assertEquals(42, iter.next().asInt());
        assertFalse(iter.hasNext());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_string_returnsSingleItem() throws Exception {
        // Strings are not treated as iterables by _is_iterable
        Iterator<PythonValue> iter = py.stream("'hello'");
        assertTrue(iter.hasNext());
        assertEquals("hello", iter.next().asString());
        assertFalse(iter.hasNext());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_partialConsumption_breaksEarly() throws Exception {
        Iterator<PythonValue> iter = py.stream("range(100)");
        int count = 0;
        while (iter.hasNext() && count < 5) {
            assertNotNull(iter.next());
            count++;
        }
        assertEquals(5, count);
        // Breaking early should not cause issues; remaining items are discarded
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_largeGenerator() throws Exception {
        Iterator<PythonValue> iter = py.stream("range(10000)");
        int count = 0;
        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        assertEquals(10000, count);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_throwsNoSuchElementException_whenStreamExhausted() throws Exception {
        Iterator<PythonValue> iter = py.stream("[1]");
        iter.next();
        assertFalse(iter.hasNext());
        assertThrows(java.util.NoSuchElementException.class, iter::next);
    }
}
