package io.github.howtis.pythonembed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class PythonProtocolTest {

    /**
     * Minimal concrete protocol for testing the abstract PythonProtocol methods.
     */
    private static class TestProtocol extends PythonProtocol {
        TestProtocol() {
            super();
        }

        TestProtocol(long timeoutMs) {
            super(timeoutMs);
        }

        @Override
        byte[] buildEvalRequest(int id, String code) { return new byte[0]; }

        @Override
        byte[] buildExecRequest(int id, String code) { return new byte[0]; }

        @Override
        byte[] buildExitRequest(int id) { return new byte[0]; }

        @Override
        byte[] buildRefRequest(int id, String name) { return new byte[0]; }

        @Override
        byte[] buildReleaseRequest(int id, int refId) { return new byte[0]; }

        @Override
        byte[] buildCallRequest(int id, int refId, String method, Object[] args) { return new byte[0]; }

        @Override
        byte[] buildGetAttrRequest(int id, int refId, String name) { return new byte[0]; }

        @Override
        byte[] buildPingRequest(int id) { return new byte[0]; }

        @Override
        byte[] buildHealthRequest(int id) { return new byte[0]; }

        @Override
        byte[] buildStreamRequest(int id, String code) { return new byte[0]; }

        @Override
        byte[] buildCallbackResult(int id, Object value) { return new byte[0]; }

        @Override
        byte[] buildCallbackError(int id, String message) { return new byte[0]; }

        @Override
        byte[] buildPushRequest(int id, String name, Object value) { return new byte[0]; }

        @Override
        byte[] buildBatchRequest(int[] ids, java.util.List<java.util.Map<String, Object>> items) { return new byte[0]; }

        @Override
        void handleResponse(byte[] raw) { }
    }

    // ---- nextId ----

    @Test
    void nextId_returnsSequentialIntegers() {
        TestProtocol proto = new TestProtocol();
        assertEquals(1, proto.nextId());
        assertEquals(2, proto.nextId());
        assertEquals(3, proto.nextId());
    }

    // ---- registerRequest + completeResponse ----

    @Test
    void registerRequest_returnsFuture() {
        TestProtocol proto = new TestProtocol();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(1);
        assertNotNull(future);
        assertFalse(future.isDone());
    }

    @Test
    void registerRequest_thenCompleteResponse_completesFuture() throws Exception {
        TestProtocol proto = new TestProtocol();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(1);
        PythonProtocol.Response response = new PythonProtocol.Response(1, "result", 42.0, null);
        proto.completeResponse(response);

        assertTrue(future.isDone());
        assertEquals(42.0, future.get().value());
        assertFalse(future.get().isError());
    }

    @Test
    void completeResponse_unknownId_doesNothing() {
        TestProtocol proto = new TestProtocol();
        // Should not throw
        proto.completeResponse(new PythonProtocol.Response(999, "result", null, null));
    }

    @Test
    void registerRequest_duplicateId_orphansPreviousFuture() {
        TestProtocol proto = new TestProtocol();
        CompletableFuture<PythonProtocol.Response> first = proto.registerRequest(1);
        CompletableFuture<PythonProtocol.Response> second = proto.registerRequest(1);

        // The old future is replaced in the map but not cancelled;
        // only the new future receives completions.
        assertNotSame(first, second);
        assertFalse(first.isDone());
        PythonProtocol.Response response = new PythonProtocol.Response(1, "result", "ok", null);
        proto.completeResponse(response);
        assertTrue(second.isDone());
        assertFalse(first.isDone()); // orphaned, never completed
    }

    // ---- cancelAll ----

    @Test
    void cancelAll_completesAllPendingRequestsExceptionally() {
        TestProtocol proto = new TestProtocol();
        CompletableFuture<PythonProtocol.Response> f1 = proto.registerRequest(1);
        CompletableFuture<PythonProtocol.Response> f2 = proto.registerRequest(2);
        RuntimeException cause = new RuntimeException("process died");

        proto.cancelAll(cause);

        assertTrue(f1.isCompletedExceptionally());
        assertTrue(f2.isCompletedExceptionally());
        ExecutionException ee1 = assertThrows(ExecutionException.class, f1::get);
        assertSame(cause, ee1.getCause());
        ExecutionException ee2 = assertThrows(ExecutionException.class, f2::get);
        assertSame(cause, ee2.getCause());
    }

    @Test
    void cancelAll_clearsPendingAfterwards() {
        TestProtocol proto = new TestProtocol();
        proto.registerRequest(1);
        proto.cancelAll(new RuntimeException("died"));

        // New requests should work fine after cancelAll
        CompletableFuture<PythonProtocol.Response> f = proto.registerRequest(2);
        proto.completeResponse(new PythonProtocol.Response(2, "result", "ok", null));
        assertDoesNotThrow(() -> assertEquals("ok", f.get().value()));
    }

    @Test
    void cancelAll_noPendingRequests_doesNothing() {
        TestProtocol proto = new TestProtocol();
        assertDoesNotThrow(() -> proto.cancelAll(new RuntimeException("early")));
    }

    // ---- awaitResponse (via reflection, since it's private) ----

    @SuppressWarnings("unchecked")
    private PythonProtocol.Response invokeAwaitResponse(
            PythonProtocol proto, int id, CompletableFuture<PythonProtocol.Response> future, String operation)
            throws Throwable {
        Method m = PythonProtocol.class.getDeclaredMethod("awaitResponse",
                int.class, CompletableFuture.class, String.class);
        m.setAccessible(true);
        try {
            return (PythonProtocol.Response) m.invoke(proto, id, future, operation);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void awaitResponse_interrupted_throwsPythonExecutionException() {
        TestProtocol proto = new TestProtocol();
        CompletableFuture<PythonProtocol.Response> future = new CompletableFuture<>();

        Thread testThread = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(100);
                testThread.interrupt();
            } catch (InterruptedException ignored) { }
        });
        interrupter.start();

        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> invokeAwaitResponse(proto, 1, future, "eval"));
        assertTrue(ex.getMessage().contains("interrupted"));
        assertTrue(Thread.interrupted()); // interrupted flag should be preserved
    }

    @Test
    @Timeout(value = 3)
    void awaitResponse_completedNormally_returnsResponse() throws Throwable {
        TestProtocol proto = new TestProtocol();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(1);
        PythonProtocol.Response expected = new PythonProtocol.Response(1, "result", "data", null);
        future.complete(expected);

        PythonProtocol.Response actual = invokeAwaitResponse(proto, 1, future, "eval");
        assertSame(expected, actual);
    }

    @Test
    void awaitResponse_executionException_nonTimeout_throwsPythonExecutionException() {
        TestProtocol proto = new TestProtocol();
        CompletableFuture<PythonProtocol.Response> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalArgumentException("bad input"));

        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> invokeAwaitResponse(proto, 1, future, "eval"));
        assertTrue(ex.getMessage().contains("eval failed"));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    // ---- sendExit ----

    @Test
    void sendExit_ioException_isSwallowed() {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer throwingWriter = data -> {
            throw new IOException("pipe broken");
        };
        // Should not throw
        assertDoesNotThrow(() -> proto.sendExit(throwingWriter));
    }

    @Test
    void sendExit_normalWriter_doesNotThrow() {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        assertDoesNotThrow(() -> proto.sendExit(noopWriter));
    }

    // ---- sendRelease ----

    @Test
    void sendRelease_ioException_isSwallowed() {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer throwingWriter = data -> {
            throw new IOException("pipe broken");
        };
        assertDoesNotThrow(() -> proto.sendRelease(throwingWriter, 42));
    }

    @Test
    void sendRelease_normalWriter_doesNotThrow() {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        assertDoesNotThrow(() -> proto.sendRelease(noopWriter, 42));
    }

    // ---- sendStream ----

    @Test
    void sendStream_nextWithoutHasNext_throwsNoSuchElementException() throws IOException {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        Iterator<PythonValue> iter = proto.sendStream(noopWriter, "code");

        assertThrows(NoSuchElementException.class, iter::next);
    }

    @Test
    void sendStream_hasNext_interrupted_returnsFalse() throws IOException {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        Iterator<PythonValue> iter = proto.sendStream(noopWriter, "code");

        Thread.currentThread().interrupt();
        try {
            assertFalse(iter.hasNext());
            assertTrue(Thread.interrupted()); // interrupted flag should be restored
        } finally {
            // Clear interrupt flag
            Thread.interrupted();
        }
    }

    @Test
    void sendStream_hasNext_timeout_returnsFalse() throws IOException {
        TestProtocol proto = new TestProtocol(50); // 50ms timeout
        PythonProtocol.Writer noopWriter = data -> { };
        Iterator<PythonValue> iter = proto.sendStream(noopWriter, "code");

        // No items will be put in the queue, so poll should timeout
        assertFalse(iter.hasNext());
        // After timeout, next() should throw
        assertThrows(NoSuchElementException.class, iter::next);
    }

    @Test
    void sendStream_next_afterStreamEnd_throwsNoSuchElementException() throws IOException, InterruptedException {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        Iterator<PythonValue> iter = proto.sendStream(noopWriter, "code");

        // Manually end the stream by putting STREAM_END in the queue
        proto.streamQueues.values().iterator().next().put(PythonProtocol.STREAM_END);

        assertFalse(iter.hasNext());
        assertThrows(NoSuchElementException.class, iter::next);
    }

    @Test
    void sendStream_itemsArrive_areDelivered() throws IOException, InterruptedException {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        Iterator<PythonValue> iter = proto.sendStream(noopWriter, "code");

        // Put items into the stream queue
        java.util.concurrent.BlockingQueue<PythonValue> queue = proto.streamQueues.values().iterator().next();
        queue.put(PythonValue.of(1.0));
        queue.put(PythonValue.of(2.0));
        queue.put(PythonProtocol.STREAM_END);

        assertTrue(iter.hasNext());
        assertEquals(1, iter.next().asInt());
        assertTrue(iter.hasNext());
        assertEquals(2, iter.next().asInt());
        assertFalse(iter.hasNext());
    }

    // ---- error/exception response via sendEval ----

    @Test
    void sendEval_errorResponse_throwsPythonExecutionException() {
        TestProtocol proto = new TestProtocol() {
            @Override
            CompletableFuture<PythonProtocol.Response> registerRequest(int id, long customTimeoutMs) {
                CompletableFuture<PythonProtocol.Response> future = new CompletableFuture<>();
                future.complete(new PythonProtocol.Response(id, "error", null, "ZeroDivisionError: division by zero"));
                pendingRequests.put(id, future);
                return future;
            }
        };
        PythonProtocol.Writer noopWriter = data -> { };

        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> proto.sendEval(noopWriter, "1/0"));
        assertTrue(ex.getMessage().contains("division by zero"));
    }

    // ---- sendBatch ----

    @Test
    void sendBatch_emptyItems_returnsEmptyResult() throws IOException {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        PythonProtocol.BatchResult result = proto.sendBatch(noopWriter, java.util.List.of());
        assertEquals(0, result.ids().length);
        assertTrue(result.futures().isEmpty());
    }

    @Test
    void sendBatch_allocatesSequentialIds() throws IOException {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        java.util.List<PythonProtocol.BatchItem> items = java.util.List.of(
                PythonProtocol.BatchItem.eval("1+1"),
                PythonProtocol.BatchItem.eval("2+2"),
                PythonProtocol.BatchItem.exec("x=1")
        );
        PythonProtocol.BatchResult result = proto.sendBatch(noopWriter, items);
        assertEquals(3, result.ids().length);
        assertEquals(3, result.futures().size());
        // IDs should be sequential starting from 1
        assertEquals(1, result.ids()[0]);
        assertEquals(2, result.ids()[1]);
        assertEquals(3, result.ids()[2]);
    }

    @Test
    void sendBatch_futuresCompleteViaResponse() throws Exception {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        java.util.List<PythonProtocol.BatchItem> items = java.util.List.of(
                PythonProtocol.BatchItem.eval("1+1")
        );
        PythonProtocol.BatchResult result = proto.sendBatch(noopWriter, items);
        assertEquals(1, result.futures().size());
        assertFalse(result.futures().get(0).isDone());

        // Simulate response arriving from Python
        proto.completeResponse(new PythonProtocol.Response(result.ids()[0], "result", 2.0, null));
        assertTrue(result.futures().get(0).isDone());
        assertEquals(2.0, result.futures().get(0).get().value());
        assertFalse(result.futures().get(0).get().isError());
    }

    @Test
    void sendBatch_errorResponse_propagatesError() throws Exception {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        java.util.List<PythonProtocol.BatchItem> items = java.util.List.of(
                PythonProtocol.BatchItem.eval("1/0")
        );
        PythonProtocol.BatchResult result = proto.sendBatch(noopWriter, items);

        proto.completeResponse(new PythonProtocol.Response(result.ids()[0], "error", null, "division by zero"));
        assertTrue(result.futures().get(0).isDone());
        assertTrue(result.futures().get(0).get().isError());
        assertEquals("division by zero", result.futures().get(0).get().message());
    }

    @Test
    void sendBatch_multipleItems_independentFutures() throws Exception {
        TestProtocol proto = new TestProtocol();
        PythonProtocol.Writer noopWriter = data -> { };
        java.util.List<PythonProtocol.BatchItem> items = java.util.List.of(
                PythonProtocol.BatchItem.eval("a"),
                PythonProtocol.BatchItem.eval("b"),
                PythonProtocol.BatchItem.exec("c")
        );
        PythonProtocol.BatchResult result = proto.sendBatch(noopWriter, items);

        // Complete them out of order
        proto.completeResponse(new PythonProtocol.Response(result.ids()[1], "result", "B", null));
        proto.completeResponse(new PythonProtocol.Response(result.ids()[2], "result", null, null));
        proto.completeResponse(new PythonProtocol.Response(result.ids()[0], "result", "A", null));

        assertEquals("A", result.futures().get(0).get().value());
        assertEquals("B", result.futures().get(1).get().value());
        assertNull(result.futures().get(2).get().value());
    }

    // ---- timeoutMs default ----

    @Test
    void defaultTimeout_is30Seconds() throws Exception {
        TestProtocol proto = new TestProtocol();
        var field = PythonProtocol.class.getDeclaredField("timeoutMs");
        field.setAccessible(true);
        assertEquals(30_000L, field.getLong(proto));
    }
}
