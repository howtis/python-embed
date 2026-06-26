package io.github.howtis.pythonembed;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pool of {@link PythonEmbed} instances that auto-scales between
 * {@code minPool} and {@code maxPool} based on saturation.
 *
 * <p>When all current instances are busy and {@code currentSize < maxPool},
 * a new instance is created (saturation-based scale up). Instances above
 * {@code minPool} that remain idle for longer than {@code idleTimeoutMs}
 * are removed (idle-based scale down).
 *
 * <p>Tasks are submitted via a shared {@link ThreadPoolExecutor} with
 * {@code maxPool} threads. When all threads and instances are saturated,
 * the {@link ThreadPoolExecutor.CallerRunsPolicy} provides backpressure
 * by running tasks in the caller's thread.
 *
 * <pre>{@code
 * // Fixed-size pool:
 * try (PythonEmbedPool pool = PythonEmbedPool.builder().maxPool(4).build()) {
 *     CompletableFuture<PythonValue> f = pool.eval("sum([1, 2, 3])");
 *     System.out.println(f.get().asInt());
 * }
 *
 * // Dynamic pool:
 * try (PythonEmbedPool pool = PythonEmbedPool.builder()
 *         .minPool(2).maxPool(8).build()) {
 *     pool.exec("x = 42");
 *     CompletableFuture<PythonValue> f = pool.eval("x * 2");
 * }
 *
 * // Dynamic pool with options and custom idle timeout:
 * PythonEmbed.Options opts = PythonEmbed.Options.builder()
 *         .timeoutMs(60_000).build();
 * try (PythonEmbedPool pool = PythonEmbedPool.builder()
 *         .minPool(1).maxPool(4).idleTimeoutMs(30_000).options(opts).build()) {
 *     pool.eval("...");
 * }
 * }</pre>
 */
public class PythonEmbedPool implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(PythonEmbedPool.class.getName());
    private static final long MAINTENANCE_INTERVAL_MS = 10_000;

    private final int minPool;
    private final int maxPool;
    private final long idleTimeoutMs;
    private final long healthCheckIntervalMs;
    private final PythonEmbed.Options options;

    private final ThreadPoolExecutor executor;
    private final ConcurrentLinkedDeque<PooledInstance> instances;
    private final ScheduledExecutorService maintenanceExecutor;
    private final AtomicInteger currentSize;
    private final Semaphore instanceSemaphore;
    private final ReentrantLock instanceLock = new ReentrantLock();
    private final Condition instanceAvailable = instanceLock.newCondition();
    private volatile long lastHealthCheckAt;
    private volatile boolean closed;

    private final Map<String, CallbackHandler> callbackHandlers = new ConcurrentHashMap<>();
    private final Map<String, PushHandler> pushHandlers = new ConcurrentHashMap<>();
    private final Map<String, Class<?>[]> callbackArgTypes = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> pushValueTypes = new ConcurrentHashMap<>();
    private final BiConsumer<PythonEmbed, CloseReason> onInstanceRemoved;
    private final Thread poolCleanupHook;

    /**
     * Wraps a {@link PythonEmbed} with pool bookkeeping fields.
     */
    static class PooledInstance {
        final PythonEmbed embed;
        volatile boolean busy;
        volatile long lastUsedAt;
        volatile boolean removed;
        volatile boolean dirty;

        PooledInstance(PythonEmbed embed) {
            this.embed = embed;
            this.lastUsedAt = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    /**
     * Returns a new {@link Builder} for constructing a {@link PythonEmbedPool}.
     *
     * <p>Set venv path and environment variables via {@link PythonEmbed.Options.Builder}
     *
     * <p>Defaults:
     * <ul>
     *   <li>{@code minPool = 1}</li>
     *   <li>{@code maxPool = 1} (fixed-size pool; set higher for auto-scaling)</li>
     *   <li>{@code idleTimeoutMs = 60_000}</li>
     *   <li>{@code healthCheckIntervalMs = 30_000}</li>
     *   <li>{@code options = }{@link PythonEmbed.Options#defaults()}</li>
     * </ul>
     *
     * <p>{@code maxPool} is the only truly required setting -- without it
     * you get a single-instance pool.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int minPool = 1;
        private int maxPool = 1;
        private long idleTimeoutMs = 60_000;
        private long healthCheckIntervalMs = 30_000;
        private PythonEmbed.Options options = PythonEmbed.Options.defaults();
        private BiConsumer<PythonEmbed, CloseReason> onInstanceRemoved = null;

        public Builder minPool(int v) { this.minPool = v; return this; }
        public Builder maxPool(int v) { this.maxPool = v; return this; }
        public Builder idleTimeoutMs(long v) { this.idleTimeoutMs = v; return this; }
        public Builder healthCheckIntervalMs(long v) { this.healthCheckIntervalMs = v; return this; }
        public Builder options(PythonEmbed.Options v) { this.options = v; return this; }

        /**
         * Register a callback invoked when a pool instance is removed
         * (due to close, idle scale-down, or health check failure).
         *
         * @param callback receives the instance and the reason for removal
         */
        public Builder onInstanceRemoved(BiConsumer<PythonEmbed, CloseReason> callback) {
            this.onInstanceRemoved = callback;
            return this;
        }

        /**
         * Builds a {@link PythonEmbedPool} and pre-warms it with
         * {@code minPool} instances.
         *
         * @return a ready-to-use pool
         * @throws IllegalArgumentException if {@code minPool < 1},
         *         {@code maxPool < minPool}, {@code idleTimeoutMs < 0},
         *         or {@code healthCheckIntervalMs < 0}
         */
        public PythonEmbedPool build() {
            return new PythonEmbedPool(this);
        }
    }

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    private PythonEmbedPool(Builder b) {
        if (b.minPool < 1) {
            throw new IllegalArgumentException("minPool must be >= 1, got " + b.minPool);
        }
        if (b.maxPool < b.minPool) {
            throw new IllegalArgumentException(
                    "maxPool (" + b.maxPool + ") must be >= minPool (" + b.minPool + ")");
        }
        if (b.idleTimeoutMs < 0) {
            throw new IllegalArgumentException("idleTimeoutMs must be >= 0");
        }
        if (b.healthCheckIntervalMs < 0) {
            throw new IllegalArgumentException("healthCheckIntervalMs must be >= 0");
        }

        this.minPool = b.minPool;
        this.maxPool = b.maxPool;
        this.idleTimeoutMs = b.idleTimeoutMs;
        this.healthCheckIntervalMs = b.healthCheckIntervalMs;
        this.options = b.options;
        this.onInstanceRemoved = b.onInstanceRemoved;

        this.instances = new ConcurrentLinkedDeque<>();
        this.currentSize = new AtomicInteger(0);
        this.instanceSemaphore = new Semaphore(b.maxPool);

        // Shared executor: core = maxPool so all threads stay alive.
        // SynchronousQueue with CallerRunsPolicy provides backpressure.
        this.executor = new ThreadPoolExecutor(
                b.maxPool, b.maxPool,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        // Periodic maintenance scheduler (idle cleanup + health check)
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "python-embed-pool-maintenance");
            t.setDaemon(true);
            return t;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        this.maintenanceExecutor = scheduler;

        // Create first instance synchronously for immediate availability
        PythonEmbed firstEmbed = createEmbed();
        instances.add(new PooledInstance(firstEmbed));
        currentSize.incrementAndGet();

        // Create remaining minPool-1 instances in background
        if (b.minPool > 1) {
            maintenanceExecutor.execute(this::ensureMinPool);
        }

        this.maintenanceExecutor.scheduleWithFixedDelay(
                this::runMaintenance,
                MAINTENANCE_INTERVAL_MS,
                MAINTENANCE_INTERVAL_MS,
                TimeUnit.MILLISECONDS);

        // Ensure all Python subprocesses are cleaned up on JVM exit
        poolCleanupHook = new Thread(this::close, "python-embed-pool-cleanup");
        Runtime.getRuntime().addShutdownHook(poolCleanupHook);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Evaluates a Python expression asynchronously.
     *
     * @param code Python expression to evaluate
     * @return a future that completes with the result
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<PythonValue> eval(String code) {
        return submitWithEmbed(embed -> embed.eval(code));
    }

    /**
     * Evaluates a Python expression with a per-call timeout override.
     *
     * @param code Python expression to evaluate
     * @param timeoutMs timeout in milliseconds for this call;
     *                  when &lt;= 0, uses the configured default timeout
     * @return a future that completes with the result
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<PythonValue> eval(String code, long timeoutMs) {
        return submitWithEmbed(embed -> embed.eval(code, timeoutMs));
    }

    /**
     * Executes Python statements asynchronously.
     * State is preserved across calls (shared namespace per instance).
     *
     * @param code Python statements to execute
     * @return a future that completes when execution finishes
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Void> exec(String code) {
        return submitWithEmbed(embed -> {
            embed.exec(code);
            return null;
        });
    }

    /**
     * Executes Python statements with a per-call timeout override.
     *
     * @param code Python statements to execute
     * @param timeoutMs timeout in milliseconds for this call;
     *                  when &lt;= 0, uses the configured default timeout
     * @return a future that completes when execution finishes
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Void> exec(String code, long timeoutMs) {
        return submitWithEmbed(embed -> {
            embed.exec(code, timeoutMs);
            return null;
        });
    }

    /**
     * Executes a Python script from a file asynchronously.
     *
     * @param scriptPath path to the Python script file
     * @return a future that completes when execution finishes
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Void> execFile(Path scriptPath) {
        return submitWithEmbed(embed -> {
            try {
                embed.execFile(scriptPath);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
            return null;
        });
    }

    /**
     * Executes a Python script from a file with a per-call timeout override.
     *
     * @param scriptPath path to the Python script file
     * @param timeoutMs timeout in milliseconds for this call;
     *                  when &lt;= 0, uses the configured default timeout
     * @return a future that completes when execution finishes
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Void> execFile(Path scriptPath, long timeoutMs) {
        return submitWithEmbed(embed -> {
            try {
                embed.execFile(scriptPath, timeoutMs);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
            return null;
        });
    }

    /**
     * Evaluates a Python expression with variable bindings asynchronously.
     *
     * @param variables map of variable names to Java values
     * @param code Python expression to evaluate
     * @return a future that completes with the result
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<PythonValue> eval(Map<String, Object> variables, String code) {
        return submitWithEmbed(embed -> embed.eval(variables, code));
    }

    /**
     * Evaluates a Python expression with variable bindings and a per-call
     * timeout override.
     *
     * @param variables map of variable names to Java values
     * @param code Python expression to evaluate
     * @param timeoutMs timeout in milliseconds for this call;
     *                  when &lt;= 0, uses the configured default timeout
     * @return a future that completes with the result
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<PythonValue> eval(Map<String, Object> variables, String code, long timeoutMs) {
        return submitWithEmbed(embed -> embed.eval(variables, code, timeoutMs));
    }

    /**
     * Executes Python statements with variable bindings asynchronously.
     *
     * @param variables map of variable names to Java values
     * @param code Python statements to execute
     * @return a future that completes when execution finishes
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Void> exec(Map<String, Object> variables, String code) {
        return submitWithEmbed(embed -> {
            embed.exec(variables, code);
            return null;
        });
    }

    /**
     * Executes Python statements with variable bindings and a per-call
     * timeout override.
     *
     * @param variables map of variable names to Java values
     * @param code Python statements to execute
     * @param timeoutMs timeout in milliseconds for this call;
     *                  when &lt;= 0, uses the configured default timeout
     * @return a future that completes when execution finishes
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Void> exec(Map<String, Object> variables, String code, long timeoutMs) {
        return submitWithEmbed(embed -> {
            embed.exec(variables, code, timeoutMs);
            return null;
        });
    }

    /**
     * Evaluates multiple Python expressions asynchronously in a single batch.
     *
     * @param codes Python expressions to evaluate
     * @return a future that completes with a list of results, one per expression
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<List<PythonValue>> batchEval(List<String> codes) {
        return submitWithEmbed(embed -> embed.batchEval(codes));
    }

    /**
     * Evaluates multiple Python expressions asynchronously in a single batch
     * with a per-call timeout override.
     *
     * @param codes Python expressions to evaluate
     * @param timeoutMs timeout in milliseconds for the entire batch;
     *                  when &lt;= 0, uses the configured default timeout
     * @return a future that completes with a list of results, one per expression
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<List<PythonValue>> batchEval(List<String> codes, long timeoutMs) {
        return submitWithEmbed(embed -> embed.batchEval(codes, timeoutMs));
    }

    /**
     * Executes multiple Python statements asynchronously in a single batch.
     *
     * @param codes Python statements to execute
     * @return a future that completes when execution finishes
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Void> batchExec(List<String> codes) {
        return submitWithEmbed(embed -> {
            embed.batchExec(codes);
            return null;
        });
    }

    /**
     * Executes multiple Python statements asynchronously in a single batch
     * with a per-call timeout override.
     *
     * @param codes Python statements to execute
     * @param timeoutMs timeout in milliseconds for the entire batch;
     *                  when &lt;= 0, uses the configured default timeout
     * @return a future that completes when execution finishes
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Void> batchExec(List<String> codes, long timeoutMs) {
        return submitWithEmbed(embed -> {
            embed.batchExec(codes, timeoutMs);
            return null;
        });
    }

    /**
     * Streams results from a Python generator or iterable expression
     * asynchronously.
     *
     * <p>The returned iterator blocks on {@code next()} until the next
     * item arrives from the Python process. The underlying
     * {@link PythonEmbed} instance remains locked for the lifetime of
     * the iterator. Callers must exhaust or explicitly close the
     * iterator to release the instance back to the pool.
     *
     * @param code a Python expression that evaluates to an iterable
     * @return a future that completes with an iterator over the streamed values
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Iterator<PythonValue>> stream(String code) {
        checkOpen();
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Stream holds the instance for its entire lifetime.
                // We don't release it in a finally block here;
                // the iterator wrapper handles release on exhaustion/close.
                PooledInstance pi = acquireInstance();
                Iterator<PythonValue> raw = pi.embed.stream(code);
                return new PooledIterator<>(raw, pi, this);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Streams results with a per-item poll timeout override.
     *
     * @param code a Python expression that evaluates to an iterable
     * @param pollTimeoutMs timeout in milliseconds per poll;
     *                      when &lt;= 0, uses the configured default timeout
     * @return a future that completes with an iterator over the streamed values
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<Iterator<PythonValue>> stream(String code, long pollTimeoutMs) {
        checkOpen();
        return CompletableFuture.supplyAsync(() -> {
            try {
                PooledInstance pi = acquireInstance();
                Iterator<PythonValue> raw = pi.embed.stream(code, pollTimeoutMs);
                return new PooledIterator<>(raw, pi, this);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Creates a handle to a Python variable asynchronously.
     *
     * <p><b>Important:</b> The returned handle is bound to a specific
     * {@link PythonEmbed} instance. If that instance is removed during
     * idle scale-down, the handle becomes invalid. Callers should
     * complete their work with the handle promptly.
     *
     * @param variableName the name of an existing Python variable
     * @return a future that completes with the handle
     * @throws IllegalStateException if the pool is closed
     */
    public CompletableFuture<PythonHandle> ref(String variableName) {
        return submitWithEmbed(embed -> embed.ref(variableName));
    }

    /**
     * Creates a dynamic proxy that wraps a Python object identified by
     * its {@link PythonHandle} as a Java interface. Method calls are
     * routed directly to the owning {@link PythonEmbed} instance.
     *
     * <p>Unlike {@link #proxy(int, Class)}, this method does not acquire
     * a pool instance per call -- the proxy is pinned to the handle's
     * owning Python process, ensuring correct refId resolution.
     *
     * @param <T>            the interface type
     * @param handle         the Python object handle (must not be released)
     * @param interfaceClass the Java interface to proxy
     * @return a dynamic proxy implementing the given interface
     * @throws IllegalArgumentException if {@code interfaceClass} is not an interface
     */
    @SuppressWarnings("unchecked")
    public <T> T proxy(PythonHandle handle, Class<T> interfaceClass) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(
                    "proxy() requires an interface, got: " + interfaceClass.getName());
        }
        handle.checkNotReleased();
        PythonEmbed embed = handle.owner();
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }
                    return new PythonProxy(
                            embed.protocol, embed.writer, handle.refId(),
                            embed.options.timeoutMs())
                            .invokePython(method, args);
                });
    }

    /**
     * Creates a dynamic proxy that wraps a Python object as a Java interface,
     * with each method call acquiring and releasing a pool instance.
     *
     *
     * <pre>{@code
     * PythonHandle handle = pool.ref("my_object").get();
     * MyInterface obj = pool.proxy(handle, MyInterface.class);
     * String result = obj.process("input");
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
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }
                    PooledInstance pi = acquireInstance();
                    try {
                        PythonEmbed embed = pi.embed;
                        return new PythonProxy(
                                embed.protocol, embed.writer, refId,
                                embed.options.timeoutMs())
                                .invokePython(method, args);
                    } finally {
                        releaseInstance(pi);
                    }
                });
    }

    /**
     * Shortcut that wraps a Python variable as a Java interface proxy
     * in a single call.
     *
     * <p>The proxy is pinned to the Python process that owns the variable,
     * ensuring correct refId resolution across all method calls.
     *
     * <pre>{@code
     * pool.exec("class Calc:\n    def add(self, a, b): return a + b\ncalc = Calc()");
     * Calculator c = pool.proxy("calc", Calculator.class);
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
    public <T> T proxy(String variableName, Class<T> interfaceClass) {
        PythonHandle handle = ref(variableName).join();
        return proxy(handle, interfaceClass);
    }

    /**
     * Registers a callback handler on all current and future instances.
     * When the pool scales up, new instances automatically receive
     * all previously registered handlers.
     *
     * @param name    the name Python uses to identify this handler
     * @param handler the handler that receives args and returns a value
     */
    public void registerCallback(String name, CallbackHandler handler) {
        if (closed) throw new IllegalStateException("Pool is closed");
        callbackHandlers.put(name, handler);
        callbackArgTypes.remove(name);
        for (PooledInstance pi : instances) {
            pi.embed.registerCallback(name, handler);
        }
    }

    /**
     * Registers a callback handler with automatic argument type conversion
     * on all current and future instances.
     *
     * @param name     the name Python uses to identify this handler
     * @param argTypes the expected Java types for each argument
     * @param handler  the handler that receives converted args
     */
    public void registerCallback(String name, Class<?>[] argTypes, CallbackHandler handler) {
        if (closed) throw new IllegalStateException("Pool is closed");
        callbackHandlers.put(name, handler);
        callbackArgTypes.put(name, argTypes);
        for (PooledInstance pi : instances) {
            pi.embed.registerCallback(name, argTypes, handler);
        }
    }

    /**
     * Registers a push handler on all current and future instances.
     * When the pool scales up, new instances automatically receive
     * all previously registered handlers.
     *
     * @param name    the name Python uses to identify this handler
     * @param handler the handler that receives name and value
     */
    public void registerPushHandler(String name, PushHandler handler) {
        if (closed) throw new IllegalStateException("Pool is closed");
        pushHandlers.put(name, handler);
        pushValueTypes.remove(name);
        for (PooledInstance pi : instances) {
            pi.embed.registerPushHandler(name, handler);
        }
    }

    /**
     * Registers a push handler with automatic value type conversion
     * on all current and future instances.
     *
     * @param <T>       the type of the push value
     * @param name      the name Python uses to identify this handler
     * @param valueType the expected Java type of the push value
     * @param handler   the handler that receives converted name and value
     */
    @SuppressWarnings("unchecked")
    public <T> void registerPushHandler(String name, Class<T> valueType, PushHandler<T> handler) {
        if (closed) throw new IllegalStateException("Pool is closed");
        PushHandler wrapped = (n, v) -> {
            try {
                handler.accept(n, PythonValue.convertValue(v, valueType));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Push handler '" + name + "' threw", e);
            }
        };
        pushHandlers.put(name, wrapped);
        pushValueTypes.put(name, valueType);
        for (PooledInstance pi : instances) {
            pi.embed.registerPushHandler(name, valueType, wrapped);
        }
    }

    /**
     * Registers a callback handler with a single typed argument
     * on all current and future instances.
     *
     * @param <A>     the type of the single argument
     * @param name    the name Python uses to identify this handler
     * @param aType   the expected Java type of the argument
     * @param handler the handler that receives the typed argument
     */
    @SuppressWarnings("unchecked")
    public <A> void registerCallback(String name, Class<A> aType, CallbackHandler1<A> handler) {
        registerCallback(name, new Class[]{aType}, args -> handler.handle((A) args[0]));
    }

    /**
     * Registers a callback handler with two typed arguments
     * on all current and future instances.
     *
     * @param <A>     the type of the first argument
     * @param <B>     the type of the second argument
     * @param name    the name Python uses to identify this handler
     * @param aType   the expected Java type of the first argument
     * @param bType   the expected Java type of the second argument
     * @param handler the handler that receives the typed arguments
     */
    @SuppressWarnings("unchecked")
    public <A, B> void registerCallback(String name, Class<A> aType, Class<B> bType, CallbackHandler2<A, B> handler) {
        registerCallback(name, new Class[]{aType, bType}, args -> handler.handle((A) args[0], (B) args[1]));
    }

    /**
     * Registers a callback handler with three typed arguments
     * on all current and future instances.
     *
     * @param <A>     the type of the first argument
     * @param <B>     the type of the second argument
     * @param <C>     the type of the third argument
     * @param name    the name Python uses to identify this handler
     * @param aType   the expected Java type of the first argument
     * @param bType   the expected Java type of the second argument
     * @param cType   the expected Java type of the third argument
     * @param handler the handler that receives the typed arguments
     */
    @SuppressWarnings("unchecked")
    public <A, B, C> void registerCallback(String name, Class<A> aType, Class<B> bType, Class<C> cType, CallbackHandler3<A, B, C> handler) {
        registerCallback(name, new Class[]{aType, bType, cType}, args -> handler.handle((A) args[0], (B) args[1], (C) args[2]));
    }


    /**
     * Returns the current number of PythonEmbed instances in the pool.
     */
    public int size() {
        return currentSize.get();
    }

    /**
     * Returns the number of currently busy (in-use) instances.
     */
    public int activeCount() {
        int count = 0;
        for (PooledInstance pi : instances) {
            if (pi.busy) count++;
        }
        return count;
    }

    /**
     * Returns the configured minimum pool size.
     */
    public int minPool() {
        return minPool;
    }

    /**
     * Returns the configured maximum pool size.
     */
    public int maxPool() {
        return maxPool;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void close() {
        close(5, TimeUnit.SECONDS);
    }

    /**
     * Gracefully shuts down the pool with a configurable timeout.
     * <p>
     * When {@code timeout > 0}, the method first waits for all in-flight
     * tasks to complete before closing instances. Use {@code timeout = 0}
     * for immediate forced shutdown.
     *
     * @param timeout the maximum time to wait for in-flight tasks
     *                and close each instance
     * @param unit the time unit of the timeout argument
     */
    public void close(long timeout, TimeUnit unit) {
        if (closed) return;
        closed = true;

        // Remove the JVM-shutdown cleanup hook
        try {
            Runtime.getRuntime().removeShutdownHook(poolCleanupHook);
        } catch (Exception ignored) {
            // Hook may have already run or been removed
        }

        // Phase 1: Wait for in-flight tasks to complete
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        awaitInFlightTasks(deadline);

        // Phase 2: Stop maintenance executor
        maintenanceExecutor.shutdown();
        try {
            if (!maintenanceExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            maintenanceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        // Phase 3: Close all instances with the remaining timeout
        CloseReason reason = CloseReason.USER;
        for (PooledInstance pi : instances) {
            notifyInstanceRemoved(pi.embed, reason);
            long remaining = deadline - System.currentTimeMillis();
            try {
                pi.embed.close(Math.max(remaining, 0), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing pooled PythonEmbed", e);
            }
        }
        instances.clear();
        currentSize.set(0);
    }

    /**
     * Waits for all in-flight tasks to complete before shutdown.
     * <p>
     * Blocks on {@code instanceAvailable} condition until every instance
     * is idle or the deadline expires. If timeout is zero (deadline already
     * past), returns immediately without waiting.
     */
    private void awaitInFlightTasks(long deadline) {
        instanceLock.lock();
        try {
            while (true) {
                boolean allIdle = true;
                for (PooledInstance pi : instances) {
                    if (pi.busy) {
                        allIdle = false;
                        break;
                    }
                }
                if (allIdle) return;

                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return;

                try {
                    instanceAvailable.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            instanceLock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Internal: instance management
    // ------------------------------------------------------------------

    /**
     * Acquires an available PythonEmbed instance, blocking if all are
     * busy and the pool is at max capacity.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Acquire a semaphore permit (limits to maxPool concurrent users)</li>
     *   <li>Scan for an idle instance -- if found, mark busy and return</li>
     *   <li>If none idle and currentSize &lt; maxPool, create a new instance
     *       (saturation-based scale up)</li>
     *   <li>If at maxPool, spin-yield until an instance is released
     *       (rare, only when semaphore and instance state briefly diverge)</li>
     * </ol>
     */
    PooledInstance acquireInstance() {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }

        try {
            instanceSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw PythonExecutionException.wrap("acquireInstance", e);
        }

        while (true) {
            // 1. Try to find an idle instance
            for (PooledInstance pi : instances) {
                if (!pi.busy && pi.embed.isOpen()) {
                    synchronized (pi) {
                        if (!pi.busy) {
                            pi.busy = true;
                            pi.lastUsedAt = System.currentTimeMillis();
                            return pi;
                        }
                    }
                }
            }

            // 2. All instances busy -- try to create a new one (saturation-based scale up)
            int current = currentSize.get();
            if (current < maxPool) {
                if (currentSize.compareAndSet(current, current + 1)) {
                    try {
                        PythonEmbed embed = createEmbed();
                        PooledInstance pi = new PooledInstance(embed);
                        pi.busy = true;
                        pi.lastUsedAt = System.currentTimeMillis();
                        instances.add(pi);
                        return pi;
                    } catch (Exception e) {
                        currentSize.decrementAndGet();
                        instanceSemaphore.release();
                        throw e;
                    }
                }
                // CAS failed -- another thread created an instance; retry to find it
                continue;
            }

            // 3. At maxPool but no idle instance found yet.
            //    This is a rare transient race: the semaphore says a slot
            //    is free, but the corresponding instance hasn't been marked
            //    idle yet. Wait for a release signal with a short timeout
            //    to avoid a pure busy-wait.
            instanceLock.lock();
            try {
                instanceAvailable.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                instanceSemaphore.release();
                throw PythonExecutionException.wrap("acquireInstance", e);
            } finally {
                instanceLock.unlock();
            }
        }
    }

    /**
     * Returns an instance to the pool, marking it as idle.
     */
    void releaseInstance(PooledInstance pi) {
        pi.busy = false;
        if (pi.dirty) {
            replaceInstance(pi);
            return;
        }
        pi.lastUsedAt = System.currentTimeMillis();
        instanceSemaphore.release();
        instanceLock.lock();
        try {
            instanceAvailable.signal();
        } finally {
            instanceLock.unlock();
        }
    }

    /**
     * Replaces a dirty (cancelled) instance with a fresh one.
     * <p>
     * Called from {@link #releaseInstance(PooledInstance)} when an
     * instance has been marked dirty due to task cancellation.
     * The old instance's Python process is closed, and a new
     * instance is created to maintain pool capacity.
     */
    private void replaceInstance(PooledInstance dirty) {
        instances.remove(dirty);
        notifyInstanceRemoved(dirty.embed, CloseReason.CANCELLED);
        try {
            dirty.embed.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error closing cancelled instance", e);
        }

        if (closed) {
            currentSize.decrementAndGet();
        } else {
            try {
                PythonEmbed embed = createEmbed();
                instances.add(new PooledInstance(embed));
            } catch (Exception e) {
                currentSize.decrementAndGet();
                logger.log(Level.WARNING, "Failed to create replacement instance after cancel", e);
            }
        }

        instanceSemaphore.release();
        instanceLock.lock();
        try {
            instanceAvailable.signal();
        } finally {
            instanceLock.unlock();
        }
    }

    /**
     * Creates a new PythonEmbed instance with stored options and
     * registers all pending callback/push handlers.
     */
    private PythonEmbed createEmbed() {
        PythonEmbed embed = PythonEmbed.create(options);
        for (Map.Entry<String, CallbackHandler> e : callbackHandlers.entrySet()) {
            String name = e.getKey();
            Class<?>[] argTypes = callbackArgTypes.get(name);
            if (argTypes != null) {
                embed.registerCallback(name, argTypes, e.getValue());
            } else {
                embed.registerCallback(name, e.getValue());
            }
        }
        for (Map.Entry<String, PushHandler> e : pushHandlers.entrySet()) {
            String name = e.getKey();
            Class<?> valueType = pushValueTypes.get(name);
            if (valueType != null) {
                embed.registerPushHandler(name, valueType, e.getValue());
            } else {
                embed.registerPushHandler(name, e.getValue());
            }
        }
        return embed;
    }

    /**
     * Runs periodic maintenance: idle instance cleanup and health checks.
     */
    void runMaintenance() {
        if (closed) return;

        cleanupIdleInstances();

        if (healthCheckIntervalMs > 0) {
            long now = System.currentTimeMillis();
            if (now - lastHealthCheckAt >= healthCheckIntervalMs) {
                healthCheck();
                lastHealthCheckAt = now;
            }
        }

        ensureMinPool();
    }


    /**
     * Periodically removes idle instances above minPool.
     */
    private void notifyInstanceRemoved(PythonEmbed embed, CloseReason reason) {
        if (onInstanceRemoved != null) {
            try {
                onInstanceRemoved.accept(embed, reason);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in onInstanceRemoved callback", e);
            }
        }
    }

    private void cleanupIdleInstances() {
        long now = System.currentTimeMillis();
        Iterator<PooledInstance> it = instances.iterator();
        while (it.hasNext()) {
            PooledInstance pi = it.next();
            if (currentSize.get() <= minPool) break;
            if (!pi.busy && (now - pi.lastUsedAt) >= idleTimeoutMs) {
                synchronized (pi) {
                    if (!pi.busy && (now - pi.lastUsedAt) >= idleTimeoutMs) {
                        if (pi.removed) continue;
                        pi.removed = true;
                        if (currentSize.decrementAndGet() >= minPool) {
                            it.remove();
                            instanceSemaphore.release();
                            notifyInstanceRemoved(pi.embed, CloseReason.POOL_CLEANUP);
                            try {
                                pi.embed.close();
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Error closing idle instance", e);
                            }
                        } else {
                            currentSize.incrementAndGet(); // rollback
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Runs a health check on all idle instances and removes unhealthy ones.
     *
     * <p>An instance is considered unhealthy if it fails to respond to
     * a ping within the configured timeout.
     *
     * @return the number of unhealthy instances removed
     */
    public int healthCheck() {
        if (closed) return 0;

        int removed = 0;
        Iterator<PooledInstance> it = instances.iterator();
        while (it.hasNext()) {
            PooledInstance pi = it.next();
            if (pi.busy) continue;

            boolean healthy = true;
            try {
                healthy = pi.embed.ping();
                if (healthy) {
                    try {
                        HealthInfo hi = pi.embed.health();
                        logger.info("Health check OK: memoryRss=" + hi.memoryRssKb()
                                + "KB, refs=" + hi.refCount()
                                + ", gcEnabled=" + hi.gcEnabled()
                                + ", gcCounts=" + hi.gcCounts());
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Health check OK but detail collection failed", e);
                    }
                } else {
                    logger.warning("Health check failed for instance: ping returned unexpected response");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Health check failed for instance", e);
                healthy = false;
            }

            if (!healthy) {
                synchronized (pi) {
                    if (!pi.busy && !pi.removed) {
                        pi.removed = true;
                        it.remove();
                        removed++;                        
                        currentSize.decrementAndGet();
                        instanceSemaphore.release();
                        notifyInstanceRemoved(pi.embed, CloseReason.HEALTH_CHECK);
                        try {
                            pi.embed.close();
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error closing unhealthy instance", e);
                        }
                    }
                }
            }
        }
        if (removed > 0) {
            logger.info("Health check removed " + removed + " unhealthy instance(s)");
        }
        return removed;
    }

    /**
     * Ensures the pool has at least {@code minPool} instances,
     * creating new ones to replace any that were removed by health checks
     * or unexpected crashes.
     */
    private void ensureMinPool() {
        if (closed) return;

        while (currentSize.get() < minPool) {
            try {
                PythonEmbed embed = createEmbed();
                instances.add(new PooledInstance(embed));
                currentSize.incrementAndGet();
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "Failed to create instance for minPool guarantee", e);
                break;
            }
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }
    }

    /**
     * Submits a task that acquires a pool instance, executes the given
     * function, and releases the instance. Handles interrupt detection
     * and dirty flagging for task cancellation.
     *
     * @param <T> the result type of the task
     * @param task the function to execute with a pooled PythonEmbed
     * @return a future that completes with the task's result
     */
    private <T> CompletableFuture<T> submitWithEmbed(Function<PythonEmbed, T> task) {
        checkOpen();
        return CompletableFuture.supplyAsync(() -> {
            PooledInstance pi = null;
            try {
                pi = acquireInstance();
                return task.apply(pi.embed);
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted() && pi != null) {
                    pi.dirty = true;
                }
                throw new CompletionException(e);
            } finally {
                if (pi != null) {
                    releaseInstance(pi);
                }
            }
        }, executor);
    }

    // ------------------------------------------------------------------
    // Internal: stream iterator wrapper
    // ------------------------------------------------------------------

    /**
     * Wraps a raw PythonValue iterator so that the pooled instance is
     * released back to the pool when the iterator is exhausted.
     */
    private static class PooledIterator<T> implements Iterator<T> {
        private final Iterator<T> delegate;
        private final PooledInstance instance;
        private final PythonEmbedPool pool;
        private boolean released;

        PooledIterator(Iterator<T> delegate, PooledInstance instance, PythonEmbedPool pool) {
            this.delegate = delegate;
            this.instance = instance;
            this.pool = pool;
        }

        @Override
        public boolean hasNext() {
            boolean has = delegate.hasNext();
            if (!has) {
                release();
            }
            return has;
        }

        @Override
        public T next() {
            try {
                return delegate.next();
            } catch (Exception e) {
                release();
                throw e;
            }
        }

        private void release() {
            if (!released) {
                released = true;
                pool.releaseInstance(instance);
            }
        }
    }
}
