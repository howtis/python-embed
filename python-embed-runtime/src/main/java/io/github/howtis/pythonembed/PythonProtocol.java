package io.github.howtis.pythonembed;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract protocol for communicating with a persistent Python REPL process.
 *
 * <p>Subclasses implement the serialization format (e.g., MessagePack).
 * Common request/response handling, ID allocation, and timeout management
 * are shared across all implementations.
 */
abstract class PythonProtocol {

    static final long DEFAULT_TIMEOUT_MS = 30_000;

    static final PythonValue STREAM_END = PythonValue.of(new Object() {
        @Override
        public String toString() { return "STREAM_END"; }
    });

    final AtomicInteger idCounter = new AtomicInteger(0);
    final Map<Integer, CompletableFuture<Response>> pendingRequests = new ConcurrentHashMap<>();
    final Map<Integer, BlockingQueue<PythonValue>> streamQueues = new ConcurrentHashMap<>();
    final long timeoutMs;

    protected PythonProtocol() {
        this(DEFAULT_TIMEOUT_MS);
    }

    protected PythonProtocol(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Builds a serialized eval request as bytes (including framing).
     */
    abstract byte[] buildEvalRequest(int id, String code);

    /**
     * Builds a serialized exec request as bytes (including framing).
     */
    abstract byte[] buildExecRequest(int id, String code);

    /**
     * Builds a serialized exit request as bytes (including framing).
     */
    abstract byte[] buildExitRequest(int id);

    /**
     * Builds a serialized ref request as bytes (including framing).
     */
    abstract byte[] buildRefRequest(int id, String name);

    /**
     * Builds a serialized release request as bytes (including framing).
     */
    abstract byte[] buildReleaseRequest(int id, int refId);

    /**
     * Builds a serialized call request as bytes (including framing).
     */
    abstract byte[] buildCallRequest(int id, int refId, String method, Object[] args);

    /**
     * Builds a serialized getattr request as bytes (including framing).
     */
    abstract byte[] buildGetAttrRequest(int id, int refId, String name);

    /**
     * Builds a serialized ping request as bytes (including framing).
     */
    abstract byte[] buildPingRequest(int id);

    /**
     * Builds a serialized health request as bytes (including framing).
     */
    abstract byte[] buildHealthRequest(int id);

    /**
     * Builds a serialized stream request as bytes (including framing).
     */
    abstract byte[] buildStreamRequest(int id, String code);

    /**
     * Builds a serialized callback result response as bytes (including framing).
     */
    abstract byte[] buildCallbackResult(int id, Object value);

    /**
     * Builds a serialized callback error response as bytes (including framing).
     */
    abstract byte[] buildCallbackError(int id, String message);

    /**
     * Builds a serialized push request as bytes (including framing).
     */
    abstract byte[] buildPushRequest(int id, String name, Object value);

    /**
     * Builds a serialized batch request containing multiple eval/exec items.
     * Each item already has its own ID. The Python bridge processes them
     * sequentially and writes one response frame per item.
     */
    abstract byte[] buildBatchRequest(int[] ids, List<Map<String, Object>> items);

    /**
     * Processes raw bytes read from the Python process stdout.
     * Each subclass implements its own parsing (e.g., MessagePack frames).
     */
    abstract void handleResponse(byte[] raw);

    /**
     * Dispatches Python&#8594;Java callback requests.
     */
    interface CallbackDispatcher {
        Object dispatchCall(int id, String name, List<Object> args) throws Exception;
        void dispatchPush(int id, String name, Object value);
    }

    CallbackDispatcher callbackDispatcher;

    void setCallbackDispatcher(CallbackDispatcher dispatcher) {
        this.callbackDispatcher = dispatcher;
    }

    /**
     * Listener for serialization size measurements, used to feed
     * {@code pythonembed.serialization.size} distribution summaries.
     */
    public interface SerializationSizeListener {
        void onRequestSize(int bytes);
        void onResponseSize(int bytes);
    }

    SerializationSizeListener serializationSizeListener;

    void setSerializationSizeListener(SerializationSizeListener listener) {
        this.serializationSizeListener = listener;
    }

    /**
     * Registers a pending request and returns a future that completes when the response arrives.
     */
    CompletableFuture<Response> registerRequest(int id) {
        return registerRequest(id, timeoutMs);
    }

    /**
     * Registers a pending request with a custom timeout.
     */
    CompletableFuture<Response> registerRequest(int id, long customTimeoutMs) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        return future.orTimeout(customTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Completes the future for the given response.
     */
    void completeResponse(Response response) {
        CompletableFuture<Response> future = pendingRequests.remove(response.id);
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * Cancels all pending requests. Called on process failure or shutdown.
     */
    void cancelAll(Throwable cause) {
        for (CompletableFuture<Response> future : pendingRequests.values()) {
            future.completeExceptionally(cause);
        }
        pendingRequests.clear();
    }

    /** Allocates the next request ID. */
    int nextId() {
        return idCounter.incrementAndGet();
    }

    /**
     * Sends an eval request and waits for the result.
     */
    PythonValue sendEval(Writer writer, String code)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        return sendEval(writer, code, 0);
    }

    /**
     * Sends an eval request with a per-call timeout override.
     * When timeoutMs &lt;= 0, falls back to the configured default timeout.
     */
    PythonValue sendEval(Writer writer, String code, long timeoutMs)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : this.timeoutMs;
        int id = nextId();
        byte[] request = buildEvalRequest(id, code);
        CompletableFuture<Response> future = registerRequest(id, effectiveTimeout);

        writer.write(request);

        Response response = awaitResponse(id, future, "eval");
        if (response.isError()) {
            throw new PythonExecutionException(extractErrorMessage(response.message), response.message, code);
        }
        return PythonValue.of(response.value);
    }

    /**
     * A single item within a batch request.
     *
     * @param type "eval" or "exec"
     * @param code the Python code to execute
     */
    public record BatchItem(String type, String code) {
        public static BatchItem eval(String code) { return new BatchItem("eval", code); }
        public static BatchItem exec(String code) { return new BatchItem("exec", code); }
    }

    /**
     * Result of sending a batch request.
     *
     * @param ids     the request IDs assigned to each batch item, in order
     * @param futures the futures that will complete with the response for each item
     */
    public record BatchResult(int[] ids, List<CompletableFuture<Response>> futures) {}

    /**
     * Sends a batch of eval/exec items in a single frame, reducing round-trips.
     * All items are processed sequentially on the Python side.
     */
    BatchResult sendBatch(Writer writer, List<BatchItem> items)
            throws java.io.IOException {
        return sendBatch(writer, items, 0);
    }

    /**
     * Sends a batch with a per-call timeout override.
     * When timeoutMs &lt;= 0, falls back to the configured default timeout.
     */
    BatchResult sendBatch(Writer writer, List<BatchItem> items, long timeoutMs)
            throws java.io.IOException {
        if (items.isEmpty()) {
            return new BatchResult(new int[0], List.of());
        }

        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : this.timeoutMs;
        int count = items.size();
        int[] ids = new int[count];
        List<Map<String, Object>> itemMaps = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            ids[i] = nextId();
            BatchItem item = items.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", ids[i]);
            map.put("type", item.type());
            map.put("code", item.code());
            itemMaps.add(map);
        }

        // Register all futures BEFORE writing the frame,
        // so responses arriving asynchronously are captured.
        List<CompletableFuture<Response>> futures = new ArrayList<>(count);
        for (int id : ids) {
            futures.add(registerRequest(id, effectiveTimeout));
        }

        byte[] request = buildBatchRequest(ids, itemMaps);
        writer.write(request);

        return new BatchResult(ids, futures);
    }

    /**
     * Sends a batch of eval expressions, waits for all results, and returns them.
     */
    List<PythonValue> sendBatchEval(Writer writer, List<String> codes, long timeoutMs)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        if (codes.isEmpty()) {
            return List.of();
        }
        List<BatchItem> items = new ArrayList<>(codes.size());
        for (String code : codes) {
            items.add(BatchItem.eval(code));
        }
        BatchResult batch = sendBatch(writer, items, timeoutMs);
        List<PythonValue> results = new ArrayList<>(batch.futures().size());
        for (int i = 0; i < batch.futures().size(); i++) {
            Response response = awaitResponse(batch.ids()[i], batch.futures().get(i), "batch_eval");
            if (response.isError()) {
                String failingCode = codes.get(i);
                throw new PythonExecutionException(
                        "batchEval[" + i + "]: " + extractErrorMessage(response.message()),
                        response.message(),
                        failingCode);
            }
            results.add(PythonValue.of(response.value()));
        }
        return results;
    }

    /**
     * Sends a batch of exec statements and waits for all to complete.
     */
    void sendBatchExec(Writer writer, List<String> codes, long timeoutMs)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        if (codes.isEmpty()) {
            return;
        }
        List<BatchItem> items = new ArrayList<>(codes.size());
        for (String code : codes) {
            items.add(BatchItem.exec(code));
        }
        BatchResult batch = sendBatch(writer, items, timeoutMs);
        for (int i = 0; i < batch.futures().size(); i++) {
            Response response = awaitResponse(batch.ids()[i], batch.futures().get(i), "batch_exec");
            if (response.isError()) {
                String failingCode = codes.get(i);
                throw new PythonExecutionException(
                        "batchExec[" + i + "]: " + extractErrorMessage(response.message()),
                        response.message(),
                        failingCode);
            }
        }
    }

    /**
     * Sends an exec request and waits for acknowledgment (or error).
     */
    void sendExec(Writer writer, String code)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        sendExec(writer, code, 0);
    }

    /**
     * Sends an exec request with a per-call timeout override.
     * When timeoutMs &lt;= 0, falls back to the configured default timeout.
     */
    void sendExec(Writer writer, String code, long timeoutMs)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : this.timeoutMs;
        int id = nextId();
        byte[] request = buildExecRequest(id, code);
        CompletableFuture<Response> future = registerRequest(id, effectiveTimeout);

        writer.write(request);

        Response response = awaitResponse(id, future, "exec");
        if (response.isError()) {
            throw new PythonExecutionException(extractErrorMessage(response.message), response.message, code);
        }
    }

    /**
     * Sends an exit command to gracefully terminate the Python process.
     */
    void sendExit(Writer writer) {
        int id = nextId();
        byte[] request = buildExitRequest(id);
        try {
            writer.write(request);
        } catch (java.io.IOException ignored) {
            // Process may already be gone
        }
    }

    /**
     * Sends a ping request and waits for a pong response.
     *
     * @return true if the Python process is healthy and responded with "pong"
     * @throws java.io.IOException on I/O error
     */
    boolean sendPing(Writer writer) throws java.io.IOException {
        int id = nextId();
        byte[] request = buildPingRequest(id);
        CompletableFuture<Response> future = registerRequest(id);

        writer.write(request);

        try {
            Response response = awaitResponse(id, future, "ping");
            if (response.isError()) {
                return false;
            }
            return "pong".equals(response.value);
        } catch (PythonExecutionException | TimeoutException e) {
            return false;
        }
    }

    /**
     * Sends a health request and returns the health information.
     *
     * @return health data collected from the Python process
     * @throws PythonExecutionException if the Python process returns an error
     * @throws TimeoutException if the request times out
     * @throws java.io.IOException on I/O error
     */
    @SuppressWarnings("unchecked")
    HealthInfo sendHealth(Writer writer) throws PythonExecutionException, TimeoutException, java.io.IOException {
        int id = nextId();
        byte[] request = buildHealthRequest(id);
        CompletableFuture<Response> future = registerRequest(id);

        writer.write(request);

        Response response = awaitResponse(id, future, "health");
        if (response.isError()) {
            throw new PythonExecutionException(extractErrorMessage(response.message), response.message);
        }
        Map<String, Object> data = (Map<String, Object>) response.value;
        return new HealthInfo(
            ((Number) data.get("memory_rss_kb")).longValue(),
            ((Number) data.get("ref_count")).intValue(),
            (Boolean) data.get("gc_enabled"),
            (List<Integer>) data.get("gc_counts")
        );
    }

    /**
     * Sends a ref request and returns the ref info (ref_id, type).
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> sendRef(Writer writer, String name)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        int id = nextId();
        byte[] request = buildRefRequest(id, name);
        CompletableFuture<Response> future = registerRequest(id);

        writer.write(request);

        Response response = awaitResponse(id, future, "ref");
        if (response.isError()) {
            throw new PythonExecutionException(extractErrorMessage(response.message), response.message);
        }
        return (Map<String, Object>) response.value;
    }

    /**
     * Sends a release request to free an object reference.
     */
    void sendRelease(Writer writer, int refId) {
        int id = nextId();
        byte[] request = buildReleaseRequest(id, refId);
        try {
            writer.write(request);
        } catch (java.io.IOException ignored) {
            // Process may already be gone
        }
    }

    /**
     * Sends a call request and returns the result.
     */
    PythonValue sendCall(Writer writer, int refId, String method, Object[] args)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        int id = nextId();
        byte[] request = buildCallRequest(id, refId, method, args);
        CompletableFuture<Response> future = registerRequest(id);

        writer.write(request);

        Response response = awaitResponse(id, future, "call");
        if (response.isError()) {
            throw new PythonExecutionException(extractErrorMessage(response.message), response.message,
                    "call(refId=" + refId + ", method=" + method + ")");
        }
        return PythonValue.of(response.value);
    }

    /**
     * Sends a getattr request and returns the attribute value.
     */
    PythonValue sendGetAttr(Writer writer, int refId, String name)
            throws PythonExecutionException, TimeoutException, java.io.IOException {
        int id = nextId();
        byte[] request = buildGetAttrRequest(id, refId, name);
        CompletableFuture<Response> future = registerRequest(id);

        writer.write(request);

        Response response = awaitResponse(id, future, "getattr");
        if (response.isError()) {
            throw new PythonExecutionException(extractErrorMessage(response.message), response.message,
                    "getattr(refId=" + refId + ", name=" + name + ")");
        }
        return PythonValue.of(response.value);
    }

    /**
     * Sends a stream request and returns an iterator over the streamed items.
     * The iterator blocks on {@code next()} until the next item arrives from Python.
     * The stream ends when a {@code stream_end} marker is received.
     */
    Iterator<PythonValue> sendStream(Writer writer, String code) throws java.io.IOException {
        return sendStream(writer, code, 0);
    }

    /**
     * Sends a stream request with a per-item poll timeout override.
     * When timeoutMs &lt;= 0, falls back to the configured default timeout.
     */
    Iterator<PythonValue> sendStream(Writer writer, String code, long timeoutMs) throws java.io.IOException {
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : this.timeoutMs;
        int id = nextId();
        byte[] request = buildStreamRequest(id, code);
        BlockingQueue<PythonValue> queue = new LinkedBlockingQueue<>();
        streamQueues.put(id, queue);

        writer.write(request);

        return new Iterator<PythonValue>() {
            private PythonValue nextItem;
            private boolean done;
            private boolean consumed = true;

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (!consumed) return true;
                try {
                    nextItem = queue.poll(effectiveTimeout, TimeUnit.MILLISECONDS);
                    if (nextItem == null) {
                        // Timeout
                        streamQueues.remove(id);
                        done = true;
                        return false;
                    }
                    if (nextItem == STREAM_END) {
                        streamQueues.remove(id);
                        done = true;
                        return false;
                    }
                    consumed = false;
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    streamQueues.remove(id);
                    done = true;
                    return false;
                }
            }

            @Override
            public PythonValue next() {
                if (done) throw new NoSuchElementException("Stream ended");
                if (consumed && !hasNext()) throw new NoSuchElementException("Stream ended");
                consumed = true;
                return nextItem;
            }
        };
    }

    /**
     * Extracts a short error message from the full Python traceback.
     * Returns the last non-empty line, which typically contains the error type and message.
     */
    private static String extractErrorMessage(String fullTraceback) {
        if (fullTraceback == null || fullTraceback.isEmpty()) {
            return "Unknown Python error";
        }
        String[] lines = fullTraceback.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line;
            }
        }
        return fullTraceback;
    }

    private Response awaitResponse(int id, CompletableFuture<Response> future, String operation)
            throws PythonExecutionException, TimeoutException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PythonExecutionException(operation + " interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException te) {
                pendingRequests.remove(id);
                throw te;
            }
            throw new PythonExecutionException(operation + " failed", cause);
        }
    }

    /**
     * Functional interface for writing raw bytes to the Python process stdin.
     */
    @FunctionalInterface
    interface Writer {
        void write(byte[] data) throws java.io.IOException;
    }

    /**
     * Internal response record.
     */
    record Response(int id, String type, Object value, String message) {
        boolean isError() {
            return "error".equals(type);
        }
    }
}
