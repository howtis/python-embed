package io.github.howtis.pythonembed;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PythonHandleTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = PythonHandle.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) {
        try {
            Field f = PythonHandle.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A protocol stub that records calls for verification.
     */
    private static class RecordingProtocol extends PythonProtocol {
        final AtomicInteger releaseCalls = new AtomicInteger(0);
        final AtomicInteger callCalls = new AtomicInteger(0);
        final AtomicInteger getAttrCalls = new AtomicInteger(0);
        volatile int lastReleaseRefId = -1;
        volatile int lastCallRefId = -1;
        volatile String lastCallMethod;
        volatile Object[] lastCallArgs;
        volatile int lastGetAttrRefId = -1;
        volatile String lastGetAttrName;
        volatile PythonValue nextCallResult;
        volatile PythonValue nextGetAttrResult;
        volatile boolean throwOnSendCall;
        volatile boolean throwOnSendGetAttr;

        @Override
        PythonValue sendCall(PythonProtocol.Writer writer, int refId, String method, Object[] args)
                throws TimeoutException, IOException {
            callCalls.incrementAndGet();
            lastCallRefId = refId;
            lastCallMethod = method;
            lastCallArgs = args;
            if (throwOnSendCall) throw new IOException("sendCall failed");
            return nextCallResult;
        }

        @Override
        PythonValue sendGetAttr(PythonProtocol.Writer writer, int refId, String name)
                throws TimeoutException, IOException {
            getAttrCalls.incrementAndGet();
            lastGetAttrRefId = refId;
            lastGetAttrName = name;
            if (throwOnSendGetAttr) throw new TimeoutException("sendGetAttr timed out");
            return nextGetAttrResult;
        }

        @Override
        void sendRelease(PythonProtocol.Writer writer, int refId) {
            releaseCalls.incrementAndGet();
            lastReleaseRefId = refId;
        }

        @Override
        void handleResponse(byte[] raw) {
            // no-op for unit tests
        }

        // ---- Stub implementations of abstract build* methods ----
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

    // ------------------------------------------------------------------
    // Constructor & basic fields
    // ------------------------------------------------------------------

    @Test
    void constructor_initializesFields() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 42, "ndarray");

        assertEquals(42, handle.refId());
        assertEquals("ndarray", handle.pythonType());
        assertFalse((boolean) getField(handle, "released"), "should not be released initially");
    }

    // ------------------------------------------------------------------
    // release() - idempotency
    // ------------------------------------------------------------------

    @Test
    void release_idempotent_secondCallIsNoop() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 7, "list");
        // Pre-mark as released to test idempotency without triggering owner.forgetHandle
        setField(handle, "released", true);

        handle.release();

        // Protocol should not be called when already released
        assertEquals(0, proto.releaseCalls.get());
    }

    @Test
    void release_idempotent_multipleCalls() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 7, "list");
        setField(handle, "released", true);

        handle.release();
        handle.release();
        handle.release();

        assertEquals(0, proto.releaseCalls.get());
    }

    // ------------------------------------------------------------------
    // call() behavior
    // ------------------------------------------------------------------

    @Test
    void call_delegatesToProtocol() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 10, "Counter");

        handle.call("update", "key", 1);

        assertEquals(1, proto.callCalls.get());
        assertEquals(10, proto.lastCallRefId);
        assertEquals("update", proto.lastCallMethod);
        assertArrayEquals(new Object[]{"key", 1}, proto.lastCallArgs);
    }

    @Test
    void call_afterRelease_throwsIllegalStateException() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 1, "str");
        setField(handle, "released", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> handle.call("upper"));
        assertEquals("PythonHandle already released", ex.getMessage());
    }

    @Test
    void call_whenProtocolThrows_wrapsInPythonExecutionException() {
        RecordingProtocol proto = new RecordingProtocol();
        proto.throwOnSendCall = true;
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 5, "object");

        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> handle.call("method"));
        assertTrue(ex.getMessage().contains("call"), "message should mention the operation");
    }

    @Test
    void call_withNoArgs_delegatesCorrectly() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 3, "function");

        handle.call("execute");

        assertEquals(1, proto.callCalls.get());
        assertEquals("execute", proto.lastCallMethod);
        assertArrayEquals(new Object[0], proto.lastCallArgs);
    }

    // ------------------------------------------------------------------
    // getAttr() behavior
    // ------------------------------------------------------------------

    @Test
    void getAttr_delegatesToProtocol() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 20, "ndarray");

        handle.getAttr("shape");

        assertEquals(1, proto.getAttrCalls.get());
        assertEquals(20, proto.lastGetAttrRefId);
        assertEquals("shape", proto.lastGetAttrName);
    }

    @Test
    void getAttr_afterRelease_throwsIllegalStateException() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 1, "list");
        setField(handle, "released", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> handle.getAttr("length"));
        assertEquals("PythonHandle already released", ex.getMessage());
    }

    @Test
    void getAttr_whenProtocolThrows_wrapsInPythonExecutionException() {
        RecordingProtocol proto = new RecordingProtocol();
        proto.throwOnSendGetAttr = true;
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 5, "object");

        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> handle.getAttr("missing"));
        assertTrue(ex.getMessage().contains("getAttr"), "message should mention the operation");
    }

    // ------------------------------------------------------------------
    // close() behavior
    // ------------------------------------------------------------------

    @Test
    void close_idempotent() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 7, "list");
        setField(handle, "released", true);

        handle.close();
        handle.close();
        handle.close();

        assertEquals(0, proto.releaseCalls.get());
    }

    // ------------------------------------------------------------------
    // Cleaner registration (no crash on GC)
    // ------------------------------------------------------------------

    @Test
    void cleanerRegistration_succeedsWithoutError() {
        // The Cleaner is registered in the constructor. There is no public
        // API to inspect it, but construction succeeding means registration
        // completed without throwing. The GC-triggered cleanup path is
        // covered implicitly by integration tests.
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 77, "dict");
        assertNotNull(handle);
        assertEquals(77, handle.refId());
    }

    // ------------------------------------------------------------------
    // Integration: call + getAttr on unreleased handle
    // ------------------------------------------------------------------

    @Test
    void callAndGetAttr_workOnSameHandle() {
        RecordingProtocol proto = new RecordingProtocol();
        PythonHandle handle = new PythonHandle(null, proto, NOOP_WRITER, 15, "MyClass");

        handle.call("init");
        handle.getAttr("data");
        handle.call("process", "input");
        handle.getAttr("result");

        assertEquals(2, proto.callCalls.get());
        assertEquals(2, proto.getAttrCalls.get());
    }
}
