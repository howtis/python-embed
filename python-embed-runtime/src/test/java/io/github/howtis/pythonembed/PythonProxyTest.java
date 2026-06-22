package io.github.howtis.pythonembed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PythonProxy}:
 * {@code camelToSnake()}, {@code isAttributeError()}, {@code invoke()}, {@code invokePython()}.
 */
class PythonProxyTest {

    // ---- test interfaces ----

    interface Calc {
        int calculateSum(int a, int b);
        int add(int a, int b);
    }

    interface Getter {
        int getValue();
        int value();
    }

    interface VoidMethod {
        void doSomething();
    }

    // ---- protocol stub ----

    static class StubProtocol extends PythonProtocol {
        PythonValue callResult;
        PythonExecutionException callError;
        PythonValue snakeCallResult;
        PythonExecutionException snakeCallError;
        PythonValue getAttrResult;
        PythonExecutionException getAttrError;

        int callCount;
        int getAttrCount;
        String lastCallMethod;
        Object[] lastCallArgs;
        String lastGetAttrName;

        @Override
        PythonValue sendCall(Writer writer, int refId, String method, Object[] args) {
            int idx = callCount++;
            lastCallMethod = method;
            lastCallArgs = args;
            if (idx == 0 && callError != null) throw callError;
            if (idx == 1 && snakeCallError != null) throw snakeCallError;
            if (idx == 0) return callResult;
            return snakeCallResult;
        }

        @Override
        PythonValue sendGetAttr(Writer writer, int refId, String name) {
            getAttrCount++;
            lastGetAttrName = name;
            if (getAttrError != null) throw getAttrError;
            return getAttrResult;
        }

        @Override void handleResponse(byte[] raw) {}
        @Override byte[] buildEvalRequest(int id, String code) { return new byte[0]; }
        @Override byte[] buildExecRequest(int id, String code) { return new byte[0]; }
        @Override byte[] buildExitRequest(int id) { return new byte[0]; }
        @Override byte[] buildRefRequest(int id, String name) { return new byte[0]; }
        @Override byte[] buildReleaseRequest(int id, int refId) { return new byte[0]; }
        @Override byte[] buildCallRequest(int id, int refId, String method, Object[] args) { return new byte[0]; }
        @Override byte[] buildGetAttrRequest(int id, int refId, String name) { return new byte[0]; }
        @Override byte[] buildPingRequest(int id) { return new byte[0]; }
        @Override byte[] buildHealthRequest(int id) { return new byte[0]; }
        @Override byte[] buildStreamRequest(int id, String code) { return new byte[0]; }
        @Override byte[] buildCallbackResult(int id, Object value) { return new byte[0]; }
        @Override byte[] buildCallbackError(int id, String message) { return new byte[0]; }
        @Override byte[] buildPushRequest(int id, String name, Object value) { return new byte[0]; }
        @Override byte[] buildBatchRequest(int[] ids, List<Map<String, Object>> items) { return new byte[0]; }
    }

    private static final PythonProtocol.Writer NOOP_WRITER = data -> {};

    private static final PythonExecutionException ATTR_ERROR =
            new PythonExecutionException("module 'foo' has no attribute 'bar': AttributeError");

    private static final PythonExecutionException VALUE_ERROR =
            new PythonExecutionException("invalid value: ValueError: bad input");

    @BeforeEach
    void setUp() {
        PythonProxy.clearResolutionCache();
    }

    // ==================================================================
    // camelToSnake
    // ==================================================================

    @Test
    void camelToSnake_standardCamelCase() {
        assertEquals("calculate_sum", PythonProxy.camelToSnake("calculateSum"));
    }

    @Test
    void camelToSnake_singleWord() {
        assertEquals("add", PythonProxy.camelToSnake("add"));
    }

    @Test
    void camelToSnake_emptyString() {
        assertEquals("", PythonProxy.camelToSnake(""));
    }

    @Test
    void camelToSnake_singleLowercase() {
        assertEquals("a", PythonProxy.camelToSnake("a"));
    }

    @Test
    void camelToSnake_singleUppercase() {
        assertEquals("a", PythonProxy.camelToSnake("A"));
    }

    @Test
    void camelToSnake_consecutiveCapitals() {
        assertEquals("x_m_l_parser", PythonProxy.camelToSnake("XMLParser"));
    }

    @Test
    void camelToSnake_alreadySnakeCase() {
        assertEquals("already_snake", PythonProxy.camelToSnake("already_snake"));
    }

    @Test
    void camelToSnake_acronymAtEnd() {
        assertEquals("get_i_d", PythonProxy.camelToSnake("getID"));
    }

    // ==================================================================
    // isAttributeError
    // ==================================================================

    @Test
    void isAttributeError_withAttributeError() {
        assertTrue(PythonProxy.isAttributeError(ATTR_ERROR));
    }

    @Test
    void isAttributeError_withoutAttributeError() {
        assertFalse(PythonProxy.isAttributeError(VALUE_ERROR));
    }

    @Test
    void isAttributeError_nullMessage() {
        PythonExecutionException e = new PythonExecutionException(null);
        assertFalse(PythonProxy.isAttributeError(e));
    }

    @Test
    void isAttributeError_messageDoesNotContainExactWord() {
        // "AttributeError" substring match required - partial won't match
        PythonExecutionException e = new PythonExecutionException("AttrError");
        assertFalse(PythonProxy.isAttributeError(e));
    }

    // ==================================================================
    // invoke - Object methods routed locally
    // ==================================================================

    @Test
    void invoke_objectGetClass_routedLocally() throws Throwable {
        StubProtocol proto = new StubProtocol();
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 1, 1000);
        Object proxy = Proxy.newProxyInstance(
                Calc.class.getClassLoader(), new Class<?>[]{Calc.class}, handler);

        Object result = handler.invoke(proxy, Object.class.getMethod("getClass"), null);

        // Should return the proxy class, not make a Python call
        assertTrue(result instanceof Class);
        assertEquals(0, proto.callCount);
    }

    @Test
    void invoke_objectToString_routedLocally() throws Throwable {
        StubProtocol proto = new StubProtocol();
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 1, 1000);
        Object proxy = Proxy.newProxyInstance(
                Calc.class.getClassLoader(), new Class<?>[]{Calc.class}, handler);

        Object result = handler.invoke(proxy, Object.class.getMethod("toString"), null);

        assertTrue(result instanceof String);
        assertEquals(0, proto.callCount);
    }

    @Test
    void invoke_objectHashCode_routedLocally() throws Throwable {
        StubProtocol proto = new StubProtocol();
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 1, 1000);
        Object proxy = Proxy.newProxyInstance(
                Calc.class.getClassLoader(), new Class<?>[]{Calc.class}, handler);

        Object result = handler.invoke(proxy, Object.class.getMethod("hashCode"), null);

        assertTrue(result instanceof Integer);
        assertEquals(0, proto.callCount);
    }

    @Test
    void invoke_objectEquals_routedLocally() throws Throwable {
        StubProtocol proto = new StubProtocol();
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 1, 1000);
        Object proxy = Proxy.newProxyInstance(
                Calc.class.getClassLoader(), new Class<?>[]{Calc.class}, handler);

        Object result = handler.invoke(proxy,
                Object.class.getMethod("equals", Object.class), new Object[]{proxy});

        assertTrue(result instanceof Boolean);
        assertEquals(0, proto.callCount);
    }

    // ==================================================================
    // invokePython - call routing
    // ==================================================================

    @Test
    void invokePython_directCallSucceeds() throws Throwable {
        StubProtocol proto = new StubProtocol();
        proto.callResult = PythonValue.of(42);
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 7, 1000);

        Method method = Calc.class.getDeclaredMethod("calculateSum", int.class, int.class);
        Object result = handler.invokePython(method, new Object[]{3, 4});

        assertEquals(42, result);
        assertEquals(1, proto.callCount);
        assertEquals("calculateSum", proto.lastCallMethod);
    }

    @Test
    void invokePython_snakeCaseFallback() throws Throwable {
        StubProtocol proto = new StubProtocol();
        proto.callError = ATTR_ERROR;
        proto.snakeCallResult = PythonValue.of(15);
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 7, 1000);

        Method method = Calc.class.getDeclaredMethod("calculateSum", int.class, int.class);
        Object result = handler.invokePython(method, new Object[]{3, 4});

        assertEquals(15, result);
        assertEquals(2, proto.callCount);
        assertEquals("calculate_sum", proto.lastCallMethod); // second call used snake_case name
        assertEquals(0, proto.getAttrCount);
    }

    @Test
    void invokePython_getterFallbackViaSnake() throws Throwable {
        StubProtocol proto = new StubProtocol();
        proto.callError = ATTR_ERROR;
        proto.snakeCallError = ATTR_ERROR;
        proto.getAttrResult = PythonValue.of(99);
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 7, 1000);

        Method method = Getter.class.getDeclaredMethod("getValue");
        Object result = handler.invokePython(method, new Object[0]);

        assertEquals(99, result);
        assertEquals(2, proto.callCount);   // exact + snake both called
        assertEquals(1, proto.getAttrCount);
        assertEquals("get_value", proto.lastGetAttrName);
    }

    @Test
    void invokePython_snakeFailsWithArgs_noGetterFallback() throws Throwable {
        StubProtocol proto = new StubProtocol();
        proto.callError = ATTR_ERROR;
        proto.snakeCallError = ATTR_ERROR;
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 7, 1000);

        Method method = Calc.class.getDeclaredMethod("calculateSum", int.class, int.class);
        assertThrows(PythonExecutionException.class,
                () -> handler.invokePython(method, new Object[]{3, 4}));

        assertEquals(2, proto.callCount);
        assertEquals(0, proto.getAttrCount, "getter should not be tried when args present");
    }

    @Test
    void invokePython_exactNameIsSnake_getterFallback() throws Throwable {
        // "value" is already snake_case - camelToSnake returns same, so snake branch skipped
        StubProtocol proto = new StubProtocol();
        proto.callError = ATTR_ERROR;
        proto.getAttrResult = PythonValue.of(7);
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 7, 1000);

        Method method = Getter.class.getDeclaredMethod("value");
        Object result = handler.invokePython(method, new Object[0]);

        assertEquals(7, result);
        assertEquals(1, proto.callCount);       // only one sendCall (no snake retry)
        assertEquals(1, proto.getAttrCount);
    }

    @Test
    void invokePython_exactNameIsSnakeWithArgs_noGetter() throws Throwable {
        // "add" is already snake_case with args - no getter fallback path
        StubProtocol proto = new StubProtocol();
        proto.callError = ATTR_ERROR;
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 7, 1000);

        Method method = Calc.class.getDeclaredMethod("add", int.class, int.class);
        assertThrows(PythonExecutionException.class,
                () -> handler.invokePython(method, new Object[]{1, 2}));

        assertEquals(1, proto.callCount);
        assertEquals(0, proto.getAttrCount);
    }

    @Test
    void invokePython_nonAttributeError_rethrowsImmediately() throws Throwable {
        StubProtocol proto = new StubProtocol();
        proto.callError = VALUE_ERROR;
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 7, 1000);

        Method method = Calc.class.getDeclaredMethod("calculateSum", int.class, int.class);
        assertThrows(PythonExecutionException.class,
                () -> handler.invokePython(method, new Object[]{3, 4}));

        assertEquals(1, proto.callCount);
        assertEquals(0, proto.getAttrCount);
    }

    @Test
    void invokePython_voidReturn_returnsNull() throws Throwable {
        StubProtocol proto = new StubProtocol();
        proto.callResult = PythonValue.of(null);
        PythonProxy handler = new PythonProxy(proto, NOOP_WRITER, 7, 1000);

        Method method = VoidMethod.class.getDeclaredMethod("doSomething");
        Object result = handler.invokePython(method, new Object[0]);

        assertNull(result);
        assertEquals(1, proto.callCount);
    }

    // ==================================================================
    // resolution caching
    // ==================================================================

    @Test
    void caching_directCall_cacheHit() throws Throwable {
        Method method = Calc.class.getDeclaredMethod("calculateSum", int.class, int.class);

        // First call resolves via CALL_CAMEL and caches it
        StubProtocol proto1 = new StubProtocol();
        proto1.callResult = PythonValue.of(42);
        PythonProxy handler1 = new PythonProxy(proto1, NOOP_WRITER, 7, 1000);
        Object result1 = handler1.invokePython(method, new Object[]{3, 4});
        assertEquals(42, result1);
        assertEquals(1, proto1.callCount);

        // Second call with fresh protocol -- cache hit, uses CALL_CAMEL directly
        StubProtocol proto2 = new StubProtocol();
        proto2.callResult = PythonValue.of(99);
        PythonProxy handler2 = new PythonProxy(proto2, NOOP_WRITER, 7, 1000);
        Object result2 = handler2.invokePython(method, new Object[]{5, 6});
        assertEquals(99, result2);
        assertEquals(1, proto2.callCount);
        assertEquals("calculateSum", proto2.lastCallMethod);
    }

    @Test
    void caching_snakeCase_cacheHit() throws Throwable {
        Method method = Calc.class.getDeclaredMethod("calculateSum", int.class, int.class);

        // First call: CALL_CAMEL fails, CALL_SNAKE succeeds -- caches CALL_SNAKE
        StubProtocol proto1 = new StubProtocol();
        proto1.callError = ATTR_ERROR;
        proto1.snakeCallResult = PythonValue.of(15);
        PythonProxy handler1 = new PythonProxy(proto1, NOOP_WRITER, 7, 1000);
        Object result1 = handler1.invokePython(method, new Object[]{3, 4});
        assertEquals(15, result1);
        assertEquals(2, proto1.callCount);

        // Second call with fresh protocol: cache hit, 1 sendCall with snake_case name
        StubProtocol proto2 = new StubProtocol();
        proto2.callResult = PythonValue.of(30);
        PythonProxy handler2 = new PythonProxy(proto2, NOOP_WRITER, 7, 1000);
        Object result2 = handler2.invokePython(method, new Object[]{5, 6});
        assertEquals(30, result2);
        assertEquals(1, proto2.callCount);
        assertEquals("calculate_sum", proto2.lastCallMethod);
    }

    @Test
    void caching_getter_cacheHit() throws Throwable {
        Method method = Getter.class.getDeclaredMethod("getValue");

        // First call: CALL_CAMEL fails, CALL_SNAKE fails, GETATTR_SNAKE succeeds
        StubProtocol proto1 = new StubProtocol();
        proto1.callError = ATTR_ERROR;
        proto1.snakeCallError = ATTR_ERROR;
        proto1.getAttrResult = PythonValue.of(99);
        PythonProxy handler1 = new PythonProxy(proto1, NOOP_WRITER, 7, 1000);
        Object result1 = handler1.invokePython(method, new Object[0]);
        assertEquals(99, result1);

        // Second call with fresh protocol: cache hit, 1 sendGetAttr with snake_case name
        StubProtocol proto2 = new StubProtocol();
        proto2.getAttrResult = PythonValue.of(77);
        PythonProxy handler2 = new PythonProxy(proto2, NOOP_WRITER, 7, 1000);
        Object result2 = handler2.invokePython(method, new Object[0]);
        assertEquals(77, result2);
        assertEquals(0, proto2.callCount);
        assertEquals(1, proto2.getAttrCount);
        assertEquals("get_value", proto2.lastGetAttrName);
    }

    @Test
    void caching_wrongStrategy_fallsBackAndUpdatesCache() throws Throwable {
        Method method = Calc.class.getDeclaredMethod("calculateSum", int.class, int.class);

        // Pre-populate cache with CALL_CAMEL -- will throw AttributeError on first use
        PythonProxy.RESOLUTION_CACHE.put(method, PythonProxy.ResolutionStrategy.CALL_CAMEL);

        // Cache hit fails, falls through to full resolution, which succeeds
        StubProtocol proto1 = new StubProtocol();
        proto1.callError = ATTR_ERROR;
        proto1.snakeCallResult = PythonValue.of(42);
        PythonProxy handler1 = new PythonProxy(proto1, NOOP_WRITER, 7, 1000);
        Object result1 = handler1.invokePython(method, new Object[]{3, 4});
        assertEquals(42, result1);

        // Second call with fresh protocol: cache should have been updated by fallback
        StubProtocol proto2 = new StubProtocol();
        proto2.callResult = PythonValue.of(99);
        PythonProxy handler2 = new PythonProxy(proto2, NOOP_WRITER, 7, 1000);
        Object result2 = handler2.invokePython(method, new Object[]{5, 6});
        assertEquals(99, result2);
        assertEquals(1, proto2.callCount);
    }

    @Test
    void caching_differentMethods_independentCacheEntries() throws Throwable {
        Method getValueMethod = Getter.class.getDeclaredMethod("getValue");
        Method valueMethod = Getter.class.getDeclaredMethod("value");

        // getValue resolves via GETATTR_SNAKE
        StubProtocol proto1 = new StubProtocol();
        proto1.callError = ATTR_ERROR;
        proto1.snakeCallError = ATTR_ERROR;
        proto1.getAttrResult = PythonValue.of(99);
        PythonProxy handler1 = new PythonProxy(proto1, NOOP_WRITER, 7, 1000);
        handler1.invokePython(getValueMethod, new Object[0]);
        assertEquals(PythonProxy.ResolutionStrategy.GETATTR_SNAKE,
                PythonProxy.RESOLUTION_CACHE.get(getValueMethod));

        // value resolves via GETATTR_CAMEL (different cache entry)
        StubProtocol proto2 = new StubProtocol();
        proto2.callError = ATTR_ERROR;
        proto2.getAttrResult = PythonValue.of(7);
        PythonProxy handler2 = new PythonProxy(proto2, NOOP_WRITER, 7, 1000);
        handler2.invokePython(valueMethod, new Object[0]);
        assertEquals(PythonProxy.ResolutionStrategy.GETATTR_CAMEL,
                PythonProxy.RESOLUTION_CACHE.get(valueMethod));

        // getValue still has its own cache entry
        assertEquals(PythonProxy.ResolutionStrategy.GETATTR_SNAKE,
                PythonProxy.RESOLUTION_CACHE.get(getValueMethod));
    }
}
