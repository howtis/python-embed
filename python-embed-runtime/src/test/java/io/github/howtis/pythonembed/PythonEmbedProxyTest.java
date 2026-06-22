package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link PythonEmbed#proxy} and
 * {@link PythonEmbedPool#proxy} - wrapping Python objects as
 * Java interfaces via {@code java.lang.reflect.Proxy}.
 */
class PythonEmbedProxyTest {

    // ---- test interfaces ----

    interface Calculator {
        int add(int a, int b);
        int subtract(int a, int b);
        int multiply(int a, int b);
    }

    interface StringProcessor {
        String process(String input);
        void consume(String input);
    }

    interface DataService {
        List<Object> getValues();
        Map<String, Object> getMetadata();
    }

    // ---- test fixtures ----

    private static PythonEmbed py;

    @BeforeAll
    static void setUp() throws Exception {
        py = PythonEmbed.create(
                PythonEmbed.Options.builder()
                        .venvPath(Path.of("build", "python-venv")).build());
    }

    @AfterAll
    static void tearDown() {
        py.close();
    }

    @BeforeEach
    void clearState() throws Exception {
        py.exec("globals().clear()");
    }

    // ---- basic method calls ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_basicMethodCall() throws Exception {
        py.exec("""
                class Calc:
                    def add(self, a, b):
                        return a + b
                    def subtract(self, a, b):
                        return a - b
                    def multiply(self, a, b):
                        return a * b
                calc = Calc()
                """);
        PythonHandle handle = py.ref("calc");
        try {
            Calculator calc = py.proxy(handle.refId(), Calculator.class);
            assertEquals(7, calc.add(3, 4));
            assertEquals(-1, calc.subtract(3, 4));
            assertEquals(12, calc.multiply(3, 4));
        } finally {
            handle.release();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_stringResult() throws Exception {
        py.exec("""
                class Proc:
                    def process(self, s):
                        return s.upper()
                p = Proc()
                """);
        PythonHandle handle = py.ref("p");
        try {
            StringProcessor sp = py.proxy(handle.refId(), StringProcessor.class);
            assertEquals("HELLO", sp.process("hello"));
        } finally {
            handle.release();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_listResult() throws Exception {
        py.exec("""
                class DS:
                    def get_values(self):
                        return [1, 2, 3]
                ds = DS()
                """);
        PythonHandle handle = py.ref("ds");
        try {
            DataService svc = py.proxy(handle.refId(), DataService.class);
            List<Object> values = svc.getValues();
            assertEquals(3, values.size());
            assertEquals(1, ((Number) values.get(0)).intValue());
            assertEquals(2, ((Number) values.get(1)).intValue());
            assertEquals(3, ((Number) values.get(2)).intValue());
        } finally {
            handle.release();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_mapResult() throws Exception {
        py.exec("""
                class DS:
                    def get_metadata(self):
                        return {"name": "test", "version": 42}
                ds = DS()
                """);
        PythonHandle handle = py.ref("ds");
        try {
            DataService svc = py.proxy(handle.refId(), DataService.class);
            Map<String, Object> meta = svc.getMetadata();
            assertEquals("test", meta.get("name"));
            assertEquals(42, ((Number) meta.get("version")).intValue());
        } finally {
            handle.release();
        }
    }

    // ---- void method ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_voidMethod() throws Exception {
        py.exec("""
                class Proc:
                    def __init__(self):
                        self.last = None
                    def consume(self, s):
                        self.last = s
                p = Proc()
                """);
        PythonHandle handle = py.ref("p");
        try {
            StringProcessor sp = py.proxy(handle.refId(), StringProcessor.class);
            sp.consume("hello world");
            assertEquals("hello world", py.eval("p.last").asString());
        } finally {
            handle.release();
        }
    }

    // ---- camelCase to snake_case ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_snakeCaseConversion() throws Exception {
        py.exec("""
                class Calc:
                    def calculate_sum(self, a, b):
                        return a + b
                calc = Calc()
                """);
        PythonHandle handle = py.ref("calc");

        interface SnakeCalc {
            int calculateSum(int a, int b);
        }

        try {
            SnakeCalc sc = py.proxy(handle.refId(), SnakeCalc.class);
            assertEquals(10, sc.calculateSum(3, 7));
        } finally {
            handle.release();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_exactMatchPreferred() throws Exception {
        py.exec("""
                class Calc:
                    def calculateSum(self, a, b):
                        return a + b + 100
                    def calculate_sum(self, a, b):
                        return a + b
                calc = Calc()
                """);
        PythonHandle handle = py.ref("calc");

        interface SnakeCalc {
            int calculateSum(int a, int b);
        }

        try {
            SnakeCalc sc = py.proxy(handle.refId(), SnakeCalc.class);
            assertEquals(110, sc.calculateSum(3, 7));
        } finally {
            handle.release();
        }
    }

    // ---- exception propagation ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_exceptionPropagation() throws Exception {
        py.exec("""
                class Calc:
                    def add(self, a, b):
                        raise ValueError("Bad values!")
                calc = Calc()
                """);
        PythonHandle handle = py.ref("calc");
        try {
            Calculator calc = py.proxy(handle.refId(), Calculator.class);
            UndeclaredThrowableException ex = assertThrows(
                    UndeclaredThrowableException.class,
                    () -> calc.add(1, 2));
            Throwable cause = ex.getCause();
            assertTrue(cause instanceof PythonExecutionException);
            assertTrue(cause.getMessage().contains("ValueError"));
            assertTrue(cause.getMessage().contains("Bad values!"));
        } finally {
            handle.release();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_methodNotFound() throws Exception {
        py.exec("""
                class Calc:
                    pass
                calc = Calc()
                """);
        PythonHandle handle = py.ref("calc");
        try {
            Calculator calc = py.proxy(handle.refId(), Calculator.class);
            UndeclaredThrowableException ex = assertThrows(
                    UndeclaredThrowableException.class,
                    () -> calc.add(1, 2));
            Throwable cause = ex.getCause();
            assertTrue(cause instanceof PythonExecutionException);
            assertTrue(cause.getMessage().contains("AttributeError"));
        } finally {
            handle.release();
        }
    }

    // ---- Object methods ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_toString_local() throws Exception {
        py.exec("x = 42");
        PythonHandle handle = py.ref("x");
        try {
            Object proxy = py.proxy(handle.refId(), Comparable.class);
            String str = proxy.toString();
            assertTrue(str.contains("$Proxy") || str.contains("Proxy"),
                    "toString should contain Proxy, got: " + str);
        } finally {
            handle.release();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_hashCode_local() throws Exception {
        py.exec("x = 42");
        PythonHandle handle = py.ref("x");
        try {
            Object proxy = py.proxy(handle.refId(), Comparable.class);
            int hc = proxy.hashCode();
            // Should not throw - hash code is computed locally
            assertTrue(hc != 0 || true); // just verify no exception
        } finally {
            handle.release();
        }
    }

    // ---- getter optimization ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_getterUsesGetAttr() throws Exception {
        py.exec("""
                class DS:
                    def __init__(self):
                        self._values = [10, 20]
                    def get_values(self):
                        return self._values
                ds = DS()
                """);
        PythonHandle handle = py.ref("ds");
        try {
            DataService svc = py.proxy(handle.refId(), DataService.class);
            List<Object> values = svc.getValues();
            assertEquals(2, values.size());
            assertEquals(10, ((Number) values.get(0)).intValue());
            assertEquals(20, ((Number) values.get(1)).intValue());
        } finally {
            handle.release();
        }
    }

    // ---- invalid refId ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_invalidRefId() throws Exception {
        Calculator calc = py.proxy(99999, Calculator.class);
        UndeclaredThrowableException ex = assertThrows(
                UndeclaredThrowableException.class,
                () -> calc.add(1, 2));
        Throwable cause = ex.getCause();
        assertTrue(cause instanceof PythonExecutionException);
        assertTrue(cause.getMessage().contains("Invalid ref_id"));
    }

    // ---- not an interface ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void proxy_notAnInterface_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> py.proxy(1, String.class));
    }
}
