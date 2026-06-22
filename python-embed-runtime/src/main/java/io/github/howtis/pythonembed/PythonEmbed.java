package io.github.howtis.pythonembed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main API for embedding Python in Java applications.
 * Uses a persistent CPython process with stdin/stdout
 * MessagePack (binary) protocol.
 *
 * <p>All configuration is consolidated in {@link Options}.
 * The Python environment (venv) can be provided in three ways:
 * <ol>
 *   <li><b>Explicit path</b> -- via {@link Options.Builder#venvPath(Path)}
 *       uses the given venv directory directly (no JAR extraction).</li>
 *   <li><b>Properties-driven</b> -- reads
 *       {@code META-INF/python-embed.properties} from the classpath
 *       and uses the configured {@code venv.path} if
 *       {@code venv.embedded=false}.</li>
 *   <li><b>Classpath resource</b> -- Falls back to extracting
 *       {@code python-venv/} from the JAR into a temp directory.</li>
 * </ol>
 *
 * <pre>{@code
 * // Defaults:
 * try (PythonEmbed py = PythonEmbed.create(Options.defaults())) {
 *     int result = py.eval("sum([1, 2, 3])").asInt();
 *     py.exec("x = 42");
 * }
 *
 * // With explicit venv path:
 * try (PythonEmbed py = PythonEmbed.create(
 *         Options.builder()
 *                 .venvPath(Path.of("/opt/myapp/venv")).build())) {
 *     py.exec("import numpy as np");
 * }
 * }</pre>
 */
public class PythonEmbed implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(PythonEmbed.class.getName());
    private static final String VENV_RESOURCE_PATH = "python-venv";
    private static final String BRIDGE_RESOURCE_PATH = "io/github/howtis/pythonembed/bridge.py";
    private static final String PROPS_PATH = "META-INF/python-embed.properties";
    private static final String PROPS_KEY_VENV_PATH = "venv.path";
    private static final String PROPS_KEY_VENV_EMBEDDED = "venv.embedded";

    private final VenvExtractor venvExtractor;
    private final PythonProcessManager processManager;
    final PythonProtocol protocol;
    final PythonProtocol.Writer writer;
    private final Map<String, String> env;
    private final Path explicitVenvPath;
    final Options options;
    private final List<PythonHandle> handles = new CopyOnWriteArrayList<>();
    private final Map<String, CallbackHandler> callbackHandlers = new ConcurrentHashMap<>();
    private final Map<String, PushHandler> pushHandlers = new ConcurrentHashMap<>();
    private Path venvPath;
    private Path bridgePath;
    private Thread processCleanupHook;

    PythonEmbed(Options options) {
        this.venvExtractor = new VenvExtractor();
        this.options = options;
        this.protocol = new MsgpackProtocol(options.timeoutMs());
        this.processManager = new PythonProcessManager(protocol);
        this.writer = processManager.stdinWriter();
        this.env = options.env();
        this.explicitVenvPath = options.venvPath();
    }

    /**
     * Creates a new PythonEmbed instance with the given options.
     *
     * <p>The {@link Options} class consolidates all configuration:
     * venv path, environment variables, timeouts, warmup scripts, etc.
     * Use {@link Options#builder()} for fluent construction.
     *
     * <pre>{@code
     * // Defaults:
     * PythonEmbed py = PythonEmbed.create(Options.defaults());
     *
     * // With explicit venv:
     * PythonEmbed py = PythonEmbed.create(
     *         Options.builder().venvPath(Path.of("/opt/venv")).build());
     * }</pre>
     *
     * @return a ready-to-use PythonEmbed instance
     * @throws PythonExecutionException if venv extraction or process startup fails
     */
    public static PythonEmbed create() {
        return create(Options.defaults());
    }

    public static PythonEmbed create(Options options) {
        try {
            PythonEmbed embed = new PythonEmbed(options);
            embed.initialize();
            return embed;
        } catch (IOException e) {
            throw PythonExecutionException.wrap("create", e);
        }
    }

    private void initialize() throws IOException {
        Properties props = loadProperties();

        // Resolve venv path
        if (explicitVenvPath != null) {
            venvPath = resolveExplicitPath(explicitVenvPath);
        } else {
            venvPath = resolveFromProperties(props);
            if (venvPath == null) {
                // Fall back to classpath resource extraction
                venvPath = venvExtractor.extract(VENV_RESOURCE_PATH);
            }
        }

        // Extract bridge.py to temp
        bridgePath = extractBridge();

        // Start the Python REPL process
        processManager.start(venvPath, bridgePath, env,
                options.maxCodeLength(),
                options.pythonExecutable(), options.startupTimeoutMs());

        // Ensure the Python process is killed on JVM exit
        processCleanupHook = new Thread(
                processManager::hardShutdown,
                "python-process-cleanup");
        Runtime.getRuntime().addShutdownHook(processCleanupHook);

        setupCallbackDispatcher();

        // Execute warmup scripts
        for (String script : options.warmupScripts()) {
            try {
                protocol.sendExec(writer, script, options.timeoutMs());
            } catch (Exception e) {
                if (options.lenientWarmup()) {
                    logger.log(Level.WARNING, "Warmup script failed: " + script, e);
                } else {
                    throw new IOException("Warmup script failed: " + script, e);
                }
            }
        }

        logger.info("PythonEmbed initialized successfully");
    }

    private void setupCallbackDispatcher() {
        protocol.setCallbackDispatcher(new PythonProtocol.CallbackDispatcher() {
            @Override
            public Object dispatchCall(int id, String name, List<Object> args) throws Exception {
                CallbackHandler handler = callbackHandlers.get(name);
                if (handler == null) {
                    byte[] err = protocol.buildCallbackError(
                            id, "No callback registered for name: " + name);
                    try {
                        writer.write(err);
                    } catch (IOException ignored) {
                    }
                    throw new RuntimeException("No callback registered for name: " + name);
                }
                try {
                    Object result = handler.handle(args.toArray());
                    byte[] resp = protocol.buildCallbackResult(id, result);
                    writer.write(resp);
                    return result;
                } catch (Exception e) {
                    byte[] err = protocol.buildCallbackError(id, e.toString());
                    try {
                        writer.write(err);
                    } catch (IOException ignored) {
                    }
                    throw e;
                }
            }

            @Override
            public void dispatchPush(int id, String name, Object value) {
                PushHandler handler = pushHandlers.get(name);
                if (handler == null) {
                    logger.warning("No push handler registered for name: " + name);
                    return;
                }
                try {
                    handler.accept(name, value);
                } catch (Exception e) {
                    logger.log(Level.WARNING,
                            "Push handler '" + name + "' threw exception", e);
                }
            }
        });
    }


    private Path resolveExplicitPath(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IOException("Venv directory does not exist or is not a directory: " + path);
        }
        logger.info(() -> "Using explicit venv path: " + path);
        return path;
    }

    /**
     * Reads properties and returns the venv path if the venv is external
     * (not embedded in JAR). Returns {@code null} if the venv is embedded
     * or properties are missing.
     */
    private Path resolveFromProperties(Properties props) {
        if (props == null) {
            return null;
        }

        String embeddedStr = props.getProperty(PROPS_KEY_VENV_EMBEDDED, "true");
        boolean embedded = Boolean.parseBoolean(embeddedStr);

        String pathStr = props.getProperty(PROPS_KEY_VENV_PATH);
        Path path = (pathStr != null && !pathStr.isEmpty()) ? Path.of(pathStr) : null;

        if (embedded) {
            // When embedded=true, use the build-time venv path if it still exists
            // (development/test scenario). This avoids unnecessary classpath
            // extraction when the venv is already on the filesystem.
            if (path != null && Files.isDirectory(path)) {
                logger.info(() -> "Using embedded venv path from properties: " + path);
                return path;
            }
            logger.fine("venv.embedded=true -- will extract from classpath");
            return null;
        }

        if (path == null) {
            return null;
        }

        if (!Files.isDirectory(path)) {
            logger.warning(() -> "External venv path from properties does not exist: " + path
                    + " -- falling back to classpath extraction");
            return null;
        }

        logger.info(() -> "Using venv path from properties: " + path);
        return path;
    }

    private Properties loadProperties() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(PROPS_PATH)) {
            if (is == null) {
                logger.fine("No " + PROPS_PATH + " found on classpath");
                return null;
            }
            Properties props = new Properties();
            props.load(is);
            return props;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read " + PROPS_PATH, e);
            return null;
        }
    }

    /**
     * Evaluates a Python expression and returns the result.
     *
     * @param code Python expression to evaluate
     * @return the result wrapped in PythonValue
     * @throws PythonExecutionException if Python evaluation fails,
     *         communication with the Python process fails, or the request times out
     */
    public PythonValue eval(String code) {
        try {
            return protocol.sendEval(writer, code);
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("eval", e);
        }
    }

    /**
     * Evaluates a Python expression with a per-call timeout override.
     *
     * @param code Python expression to evaluate
     * @param timeoutMs timeout in milliseconds for this call;
     *                  when &lt;= 0, uses the configured default timeout
     * @return the result wrapped in PythonValue
     * @throws PythonExecutionException if Python evaluation fails,
     *         communication with the Python process fails, or the request times out
     */
    public PythonValue eval(String code, long timeoutMs) {
        try {
            return protocol.sendEval(writer, code, timeoutMs);
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("eval", e);
        }
    }

    /**
     * Executes one or more Python statements.
     * State is preserved across calls (shared namespace).
     *
     * @param code Python statements to execute
     * @throws PythonExecutionException if Python execution fails,
     *         communication with the Python process fails, or the request times out
     */
    public void exec(String code) {
        try {
            protocol.sendExec(writer, code);
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("exec", e);
        }
    }

    /**
     * Executes Python statements with a per-call timeout override.
     *
     * @param code Python statements to execute
     * @param timeoutMs timeout in milliseconds for this call;
     *                  when &lt;= 0, uses the configured default timeout
     * @throws PythonExecutionException if Python execution fails,
     *         communication with the Python process fails, or the request times out
     */
    public void exec(String code, long timeoutMs) {
        try {
            protocol.sendExec(writer, code, timeoutMs);
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("exec", e);
        }
    }

    /**
     * Executes a warmup script on an already-running Python instance.
     *
     * @param script Python code to execute (typically import statements)
     * @throws PythonExecutionException if Python execution fails,
     *         communication with the Python process fails, or the request times out
     */
    public void warmup(String script) {
        try {
            protocol.sendExec(writer, script, options.timeoutMs());
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("warmup", e);
        }
    }

    /**
     * Evaluates multiple Python expressions in a single batch request,
     * reducing N-1 round-trips compared to N individual eval calls.
     *
     * <p>All expressions are evaluated sequentially in the shared namespace,
     * so later expressions can reference side-effects of earlier ones.
     *
     * @param codes Python expressions to evaluate
     * @return a list of results, one per expression, in the same order
     * @throws PythonExecutionException if any expression fails,
     *         communication with the Python process fails, or the batch times out
     */
    public List<PythonValue> batchEval(List<String> codes) {
        return batchEval(codes, 0);
    }

    /**
     * Evaluates multiple Python expressions in a single batch request
     * with a per-call timeout override.
     *
     * @param codes Python expressions to evaluate
     * @param timeoutMs timeout in milliseconds for the entire batch;
     *                  when &lt;= 0, uses the configured default timeout
     * @return a list of results, one per expression, in the same order
     * @throws PythonExecutionException if any expression fails,
     *         communication with the Python process fails, or the batch times out
     */
    public List<PythonValue> batchEval(List<String> codes, long timeoutMs) {
        try {
            return protocol.sendBatchEval(writer, codes, timeoutMs);
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("batchEval", e);
        }
    }

    /**
     * Executes multiple Python statements in a single batch request,
     * reducing N-1 round-trips compared to N individual exec calls.
     *
     * <p>Statements are executed sequentially in the shared namespace.
     *
     * @param codes Python statements to execute
     * @throws PythonExecutionException if any statement fails,
     *         communication with the Python process fails, or the batch times out
     */
    public void batchExec(List<String> codes) {
        batchExec(codes, 0);
    }

    /**
     * Executes multiple Python statements in a single batch request
     * with a per-call timeout override.
     *
     * @param codes Python statements to execute
     * @param timeoutMs timeout in milliseconds for the entire batch;
     *                  when &lt;= 0, uses the configured default timeout
     * @throws PythonExecutionException if any statement fails,
     *         communication with the Python process fails, or the batch times out
     */
    public void batchExec(List<String> codes, long timeoutMs) {
        try {
            protocol.sendBatchExec(writer, codes, timeoutMs);
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("batchExec", e);
        }
    }

    /**
     * Creates a handle to a Python variable, keeping the object in Python
     * memory and avoiding re-serialization on subsequent calls.
     *
     * @param variableName the name of an existing Python variable
     * @return a handle that can be used for method calls and attribute access
     * @throws PythonExecutionException if the variable doesn't exist,
     *         communication with the Python process fails, or the request times out
     */
    public PythonHandle ref(String variableName) {
        try {
            Map<String, Object> refInfo = protocol.sendRef(writer, variableName);
            int refId = ((Number) refInfo.get("ref_id")).intValue();
            String type = (String) refInfo.get("type");
            PythonHandle handle = new PythonHandle(this, protocol, writer, refId, type);
            handles.add(handle);
            return handle;
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("ref", e);
        }
    }

    /**
     * Creates a dynamic proxy that wraps a Python object as a Java interface.
     *
     * <p>The Python object is identified by its {@code refId} (obtained via
     * {@link #ref(String)}). Each interface method call is transparently
     * routed to the Python object using the {@code call}/{@code getattr}
     * protocol commands.
     *
     * <p>Java camelCase method names are automatically converted to Python
     * snake_case when an exact match is not found (e.g., {@code calculateSum}
     * maps to {@code calculate_sum}).
     *
     * <p>Python exceptions are propagated as {@link PythonExecutionException}.
     *
     * <pre>{@code
     * PythonHandle handle = py.ref("my_object");
     * MyInterface obj = py.proxy(handle.getRefId(), MyInterface.class);
     * String result = obj.process("input");  // calls my_object.process("input")
     * }</pre>
     *
     * @param <T>            the interface type
     * @param refId          the Python object reference ID from {@link PythonHandle#refId()}
     * @param interfaceClass the Java interface to proxy
     * @return a dynamic proxy implementing the given interface
     * @throws IllegalArgumentException if {@code interfaceClass} is not an interface
     */
    @SuppressWarnings("unchecked")
    public <T> T proxy(int refId, Class<T> interfaceClass) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(
                    "proxy() requires an interface, got: " + interfaceClass.getName());
        }
        PythonProxy handler = new PythonProxy(protocol, writer, refId, options.timeoutMs());
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler);
    }

    /**
     * Shortcut that wraps a Python variable as a Java interface proxy
     * in a single call.
     *
     * <p>Equivalent to {@code proxy(ref(name).refId(), interfaceClass)}
     * but with automatic handle lifecycle management: the Python object
     * stays alive as long as the returned proxy is reachable.
     *
     * <pre>{@code
     * py.exec("class Calc:\n    def add(self, a, b): return a + b\ncalc = Calc()");
     * Calculator c = py.proxy("calc", Calculator.class);
     * int result = c.add(3, 4);  // 7
     * }</pre>
     *
     * @param <T>            the interface type
     * @param variableName   the Python variable name in the global scope
     * @param interfaceClass the Java interface to proxy
     * @return a dynamic proxy implementing the given interface
     * @throws PythonExecutionException if ref resolution fails
     * @throws IllegalArgumentException if {@code interfaceClass} is not an interface
     */
    @SuppressWarnings("unchecked")
    public <T> T proxy(String variableName, Class<T> interfaceClass) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(
                    "proxy() requires an interface, got: " + interfaceClass.getName());
        }
        PythonHandle handle = ref(variableName);
        PythonProxy handler = new PythonProxy(protocol, writer, handle.refId(),
                options.timeoutMs(), handle);
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler);
    }

    /**
     * Streams results from a Python generator or iterable expression.
     *
     * <p>The code must evaluate to an iterable (generator, list, tuple, etc.).
     * Each item is yielded as a separate {@link PythonValue} through the returned
     * {@link Iterator}. The iterator blocks on {@code next()} until the next
     * item arrives from the Python process.
     *
     * @param code a Python expression that evaluates to an iterable
     * @return an iterator over the streamed values
     * @throws PythonExecutionException if the stream request fails
     */
    public Iterator<PythonValue> stream(String code) {
        try {
            return protocol.sendStream(writer, code);
        } catch (IOException e) {
            throw PythonExecutionException.wrap("stream", e);
        }
    }

    /**
     * Streams results with a per-item poll timeout override.
     *
     * @param code a Python expression that evaluates to an iterable
     * @param pollTimeoutMs timeout in milliseconds per poll;
     *                      when &lt;= 0, uses the configured default timeout
     * @return an iterator over the streamed values
     * @throws PythonExecutionException if the stream request fails
     */
    public Iterator<PythonValue> stream(String code, long pollTimeoutMs) {
        try {
            return protocol.sendStream(writer, code, pollTimeoutMs);
        } catch (IOException e) {
            throw PythonExecutionException.wrap("stream", e);
        }
    }

    /**
     * Pings the Python process to check if it is still healthy.
     *
     * <p>Sends a ping request and waits for a pong response.
     *
     * @return true if the Python process is healthy and responded
     * @throws PythonExecutionException if an I/O error occurs
     */
    public boolean ping() {
        try {
            return protocol.sendPing(writer);
        } catch (IOException e) {
            throw PythonExecutionException.wrap("ping", e);
        }
    }

    /**
     * Collects health information from the Python process.
     *
     * <p>Returns memory usage, active reference count, GC status, and
     * GC generation counts.
     *
     * @return health data collected from the Python process
     * @throws PythonExecutionException if the Python process returns an error,
     *         communication fails, or the request times out
     */
    public HealthInfo health() {
        try {
            return protocol.sendHealth(writer);
        } catch (TimeoutException | IOException e) {
            throw PythonExecutionException.wrap("health", e);
        }
    }

    void forgetHandle(PythonHandle handle) {
        handles.remove(handle);
    }

    boolean isOpen() {
        return processManager.isRunning();
    }

    long getPid() {
        return processManager.getPid();
    }

    void hardShutdown() {
        processManager.hardShutdown();
    }

    /**
     * Registers a callback handler that Python can call via
     * {@code _bridge.call(name, ...)}.
     *
     * @param name    the name Python uses to identify this handler
     * @param handler the handler that receives args and returns a value
     */
    public void registerCallback(String name, CallbackHandler handler) {
        callbackHandlers.put(name, handler);
    }

    /**
     * Registers a callback handler with automatic argument type conversion.
     * Each argument from Python is converted to the declared type before
     * the handler is invoked, eliminating manual downcasting in handler bodies.
     *
     * <p>Only {@link Number} subtypes and {@link String} are supported as
     * argument types. A {@link ClassCastException} is thrown at invocation
     * time for unsupported types.
     *
     * @param name     the name Python uses to identify this handler
     * @param argTypes the expected Java types for each argument
     * @param handler  the handler that receives converted args
     */
    public void registerCallback(String name, Class<?>[] argTypes, CallbackHandler handler) {
        callbackHandlers.put(name, args -> {
            Object[] converted = new Object[args.length];
            for (int i = 0; i < args.length && i < argTypes.length; i++) {
                converted[i] = PythonValue.convertValue(args[i], argTypes[i]);
            }
            return handler.handle(converted);
        });
    }

    /**
     * Registers a push handler that receives fire-and-forget pushes
     * from Python via {@code _bridge.push(name, value)}.
     *
     * @param name    the name Python uses to identify this handler
     * @param handler the handler that receives name and value
     */
    public void registerPushHandler(String name, PushHandler handler) {
        pushHandlers.put(name, handler);
    }

    /**
     * Registers a push handler with automatic value type conversion.
     * The value from Python is converted to the declared type before
     * the handler is invoked, eliminating manual downcasting.
     *
     * <p>Only {@link Number} subtypes and {@link String} are supported as
     * the value type. A {@link ClassCastException} is thrown at invocation
     * time for unsupported types.
     *
     * @param name      the name Python uses to identify this handler
     * @param valueType the expected Java type of the push value
     * @param handler   the handler that receives converted name and value
     */
    public void registerPushHandler(String name, Class<?> valueType, PushHandler handler) {
        pushHandlers.put(name, (n, value) ->
                handler.accept(n, PythonValue.convertValue(value, valueType)));
    }

    @Override
    public void close() {
        closeInternal(5_000, 2_000);
    }

    /**
     * Gracefully shuts down with a configurable shutdown timeout.
     *
     * <p>Sends an exit command to the Python process, waits up to the
     * given timeout for graceful termination, then force-destroys
     * the process if it's still alive. A brief additional wait
     * (2 seconds) is applied after force-destroy.
     *
     * @param timeout the maximum time to wait for graceful shutdown
     * @param unit the time unit of the timeout argument
     */
    public void close(long timeout, TimeUnit unit) {
        long waitMs = unit.toMillis(timeout);
        closeInternal(waitMs, Math.min(waitMs / 2, 2_000));
    }

    private void closeInternal(long waitMs, long forceWaitMs) {
        // Remove the JVM-shutdown cleanup hook (process is being
        // closed explicitly, so the hook should not fire later).
        if (processCleanupHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(processCleanupHook);
            } catch (Exception ignored) {
                // Hook may have already run or been removed
            }
            processCleanupHook = null;
        }

        // Release all tracked handles
        for (PythonHandle handle : handles) {
            try {
                handle.release();
            } catch (Exception e) {
                logger.log(Level.FINE, "Error releasing handle", e);
            }
        }
        handles.clear();

        callbackHandlers.clear();
        pushHandlers.clear();

        try {
            processManager.close(waitMs, forceWaitMs);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error closing process manager", e);
        }
        try {
            venvExtractor.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up venv", e);
        }
    }

    // ------------------------------------------------------------------
    // Safe parameter injection
    // ------------------------------------------------------------------

    private static final int ARG_MAX_DEPTH = 20;
    private static final int ARG_MAX_COLLECTION_SIZE = 1000;

    /**
     * Converts a Java value to a safe Python literal string
     * for use in eval/exec code strings.
     *
     * <p>This eliminates the risk of Python code injection when
     * values from external sources (user input, databases, etc.)
     * are interpolated into Python code via string concatenation.
     *
     * <pre>{@code
     * // Safe:
     * py.eval("len(" + PythonEmbed.arg(userInput) + ")");
     *
     * // Type examples:
     * PythonEmbed.arg(null)        -&gt; None
     * PythonEmbed.arg("hello")     -&gt; 'hello'
     * PythonEmbed.arg(42)          -&gt; 42
     * PythonEmbed.arg(true)        -&gt; True
     * PythonEmbed.arg(List.of(1, 2)) -&gt; [1, 2]
     * PythonEmbed.arg(Map.of("k", 1)) -&gt; {'k': 1}
     * }</pre>
     *
     * @param value the Java value to convert (null allowed)
     * @return a Python literal expression as a String
     * @throws IllegalArgumentException if nesting depth exceeds 20
     *         or collection/map size exceeds 1000
     */
    public static String arg(Object value) {
        return arg(value, 0);
    }

    private static String arg(Object value, int depth) {
        if (depth > ARG_MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "PythonEmbed.arg() nesting depth exceeds limit of " + ARG_MAX_DEPTH);
        }
        if (value == null) {
            return "None";
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "True" : "False";
        }
        if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof Double) {
            double d = (Double) value;
            if (Double.isNaN(d)) {
                return "float('nan')";
            }
            if (Double.isInfinite(d)) {
                return d > 0 ? "float('inf')" : "float('-inf')";
            }
            return value.toString();
        }
        if (value instanceof Float) {
            float f = (Float) value;
            if (Float.isNaN(f)) {
                return "float('nan')";
            }
            if (Float.isInfinite(f)) {
                return f > 0 ? "float('inf')" : "float('-inf')";
            }
            return value.toString();
        }
        if (value instanceof String) {
            return "'" + escapePythonString((String) value) + "'";
        }
        if (value instanceof Collection<?> coll) {
            if (coll.size() > ARG_MAX_COLLECTION_SIZE) {
                throw new IllegalArgumentException(
                        "PythonEmbed.arg() collection size " + coll.size()
                                + " exceeds limit of " + ARG_MAX_COLLECTION_SIZE);
            }
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            boolean first = true;
            for (Object elem : coll) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(arg(elem, depth + 1));
                first = false;
            }
            sb.append(']');
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > ARG_MAX_COLLECTION_SIZE) {
                throw new IllegalArgumentException(
                        "PythonEmbed.arg() map size " + map.size()
                                + " exceeds limit of " + ARG_MAX_COLLECTION_SIZE);
            }
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(arg(entry.getKey(), depth + 1));
                sb.append(": ");
                sb.append(arg(entry.getValue(), depth + 1));
                first = false;
            }
            sb.append('}');
            return sb.toString();
        }
        // Fallback: unrecognized type -&gt; escape toString()
        return "'" + escapePythonString(value.toString()) + "'";
    }

    private static String escapePythonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == 0x7f) {
                        sb.append("\\x");
                        sb.append(String.format("%02x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Configuration options for PythonEmbed.
     *
     * <p>Use {@link PythonEmbed#create(Options)} with this builder:
     * <pre>{@code
     * try (PythonEmbed py = PythonEmbed.create(
     *         Options.builder()
     *                 .timeoutMs(60_000)
     *                 .venvPath(Path.of("/opt/venv"))
     *                 .build())) { ... }
     * }</pre>
     *
     * <p>Or pass Options around separately:
     * <pre>{@code
     * Options opts = Options.builder()
     *         .timeoutMs(60_000)
     *         .venvPath(Path.of("/opt/venv"))
     *         .env(Map.of("CUDA_VISIBLE_DEVICES", "0"))
     *         .build();
     * try (PythonEmbed py = PythonEmbed.create(opts)) { ... }
     * }</pre>
     */
    public static final class Options {
        private final long timeoutMs;
        private final int maxCodeLength;
        private final long startupTimeoutMs;
        private final String pythonExecutable;
        private final List<String> warmupScripts;
        private final boolean lenientWarmup;
        private final Path venvPath;
        private final Map<String, String> env;

        private Options(long timeoutMs,
                        int maxCodeLength, long startupTimeoutMs,
                        String pythonExecutable,
                        List<String> warmupScripts,
                        boolean lenientWarmup,
                        Path venvPath,
                        Map<String, String> env) {
            this.timeoutMs = timeoutMs;
            this.maxCodeLength = maxCodeLength;
            this.startupTimeoutMs = startupTimeoutMs;
            this.pythonExecutable = pythonExecutable;
            this.warmupScripts = warmupScripts;
            this.lenientWarmup = lenientWarmup;
            this.venvPath = venvPath;
            this.env = env;
        }

        /** Per-request timeout in milliseconds (default: 30_000). */
        public long timeoutMs() { return timeoutMs; }

        /** Maximum code length in characters (default: 100_000). */
        public int maxCodeLength() { return maxCodeLength; }

        /** Startup timeout in milliseconds (default: 30_000). */
        public long startupTimeoutMs() { return startupTimeoutMs; }

        /** Python executable override, or null for auto-detect. */
        public String pythonExecutable() { return pythonExecutable; }

        /** Warmup scripts to execute after instance initialization. */
        public List<String> warmupScripts() { return warmupScripts; }

        /**
         * Whether warmup script failures should be logged as warnings
         * instead of throwing exceptions. Default is {@code true}
         * (backward-compatible; failures are logged but do not prevent startup).
         * Set to {@code false} to make warmup failures immediately visible.
         */
        public boolean lenientWarmup() { return lenientWarmup; }

        /** Explicit venv path, or null for auto-discovery / classpath extraction. */
        public Path venvPath() { return venvPath; }

        /** Environment variables to pass to the Python process (never null). */
        public Map<String, String> env() { return env; }

        static Options defaults() {
            return new Options(30_000, 100_000, 30_000, null, Collections.emptyList(), true,
                    null, Collections.emptyMap());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private long timeoutMs = 30_000;
            private int maxCodeLength = 100_000;
            private long startupTimeoutMs = 30_000;
            private String pythonExecutable = null;
            private final List<String> warmupScripts = new ArrayList<>();
            private boolean lenientWarmup = true;
            private Path venvPath = null;
            private Map<String, String> env = Collections.emptyMap();

            /** Set per-request timeout in milliseconds. */
            public Builder timeoutMs(long value) {
                this.timeoutMs = value;
                return this;
            }

            /** Set maximum code length in characters. */
            public Builder maxCodeLength(int value) {
                this.maxCodeLength = value;
                return this;
            }

            /** Set startup timeout in milliseconds. */
            public Builder startupTimeoutMs(long value) {
                this.startupTimeoutMs = value;
                return this;
            }

            /** Override the Python executable path. */
            public Builder pythonExecutable(String value) {
                this.pythonExecutable = value;
                return this;
            }

            /** Append a warmup script to execute after instance initialization. */
            public Builder warmupScript(String script) {
                this.warmupScripts.add(script);
                return this;
            }

            /** Append multiple warmup scripts at once. */
            public Builder warmupScripts(List<String> scripts) {
                this.warmupScripts.addAll(scripts);
                return this;
            }

            /**
             * Set whether warmup script failures should be logged as warnings
             * instead of throwing exceptions. Default is {@code true}.
             */
            public Builder lenientWarmup(boolean value) {
                this.lenientWarmup = value;
                return this;
            }

            /** Set the explicit venv path (null for auto-discovery). */
            public Builder venvPath(Path value) {
                this.venvPath = value;
                return this;
            }

            /** Set environment variables for the Python process. */
            public Builder env(Map<String, String> value) {
                this.env = value;
                return this;
            }

            public Options build() {
                return new Options(timeoutMs,
                        maxCodeLength, startupTimeoutMs, pythonExecutable,
                        List.copyOf(warmupScripts), lenientWarmup,
                        venvPath, env);
            }
        }
    }


    private Path extractBridge() throws IOException {
        Path bridgeFile = Files.createTempFile("python-embed-bridge-", ".py");
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(BRIDGE_RESOURCE_PATH)) {
            if (is == null) {
                throw new IOException("bridge.py not found on classpath: " + BRIDGE_RESOURCE_PATH);
            }
            Files.copy(is, bridgeFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    try {
                        Files.deleteIfExists(bridgeFile);
                    } catch (IOException ignored) {
                    }
                }, "bridge-cleanup"));
        return bridgeFile;
    }
}
