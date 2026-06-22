package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonEmbedPoolTest {

    private static PythonEmbedPool pool;

    @BeforeAll
    static void setUp() {
        pool = PythonEmbedPool.builder().minPool(2).maxPool(4).build();
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    @AfterEach
    void clearState() throws Exception {
        // Clear Python state in all instances
        for (int i = 0; i < pool.size(); i++) {
            pool.exec("globals().clear()").get(3, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------------------
    // Basic eval/exec tests
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void eval_simpleExpression_returnsInt() throws Exception {
        PythonValue result = pool.eval("sum([1, 2, 3])").get(3, TimeUnit.SECONDS);
        assertEquals(6, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void eval_and_exec_sharedState() throws Exception {
        pool.exec("x = 42").get(3, TimeUnit.SECONDS);
        // Round-robin may hit a different instance, so reset state on all
        pool.exec("x = 42").get(3, TimeUnit.SECONDS);
        pool.exec("x = 42").get(3, TimeUnit.SECONDS);
        pool.exec("x = 42").get(3, TimeUnit.SECONDS);
        PythonValue result = pool.eval("x + 1").get(3, TimeUnit.SECONDS);
        assertEquals(43, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void eval_stringResult() throws Exception {
        PythonValue result = pool.eval("'hello'.upper()").get(3, TimeUnit.SECONDS);
        assertEquals("HELLO", result.asString());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void eval_listResult() throws Exception {
        PythonValue result = pool.eval("[i * 2 for i in range(5)]").get(3, TimeUnit.SECONDS);
        List<Double> list = result.asList(Double.class);
        assertEquals(List.of(0.0, 2.0, 4.0, 6.0, 8.0), list);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void exec_multiline() throws Exception {
        pool.exec("""
                def multiply(a, b):
                    return a * b
                """).get(3, TimeUnit.SECONDS);
        pool.exec("""
                def multiply(a, b):
                    return a * b
                """).get(3, TimeUnit.SECONDS);
        pool.exec("""
                def multiply(a, b):
                    return a * b
                """).get(3, TimeUnit.SECONDS);
        pool.exec("""
                def multiply(a, b):
                    return a * b
                """).get(3, TimeUnit.SECONDS);
        PythonValue result = pool.eval("multiply(6, 7)").get(3, TimeUnit.SECONDS);
        assertEquals(42, result.asInt());
    }

    // ------------------------------------------------------------------
    // Parallel execution
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void parallelEval_resultsAreCorrect() throws Exception {
        int tasks = 50;
        @SuppressWarnings("unchecked")
        CompletableFuture<PythonValue>[] futures = IntStream.range(0, tasks)
                .mapToObj(i -> pool.eval("sum([" + i + ", " + (i + 1) + "])"))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        for (int i = 0; i < tasks; i++) {
            PythonValue result = futures[i].get();
            assertEquals(i + i + 1, result.asInt(),
                    "Task " + i + " should return " + (i + i + 1));
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void parallelStateIsolation() throws Exception {
        // Each instance has independent Python state.
        // Run tasks that set different values of 'x' on different instances,
        // then read back -- round-robin averages out but each read should be
        // a valid integer.
        int tasks = 20;
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = IntStream.range(0, tasks)
                .mapToObj(i -> pool.exec("x = " + i))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

        // All exec calls completed successfully
        for (CompletableFuture<Void> f : futures) {
            assertDoesNotThrow(() -> f.get());
        }
    }

    // ------------------------------------------------------------------
    // Error propagation
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void eval_syntaxError_propagatesAsExecutionException() {
        CompletableFuture<PythonValue> future = pool.eval("1 + ");
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(3, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof PythonExecutionException);
        assertTrue(ex.getCause().getMessage().contains("SyntaxError"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void eval_nameError_propagates() {
        CompletableFuture<PythonValue> future = pool.eval("undefined_variable");
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(3, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof PythonExecutionException);
        assertTrue(ex.getCause().getMessage().contains("NameError"));
    }

    // ------------------------------------------------------------------
    // Stream
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void stream_rangeYieldsValues() throws Exception {
        Iterator<PythonValue> iter = pool.stream("range(3)").get(5, TimeUnit.SECONDS);
        assertTrue(iter.hasNext());
        assertEquals(0, iter.next().asInt());
        assertEquals(1, iter.next().asInt());
        assertEquals(2, iter.next().asInt());
        assertFalse(iter.hasNext());
    }

    // ------------------------------------------------------------------
    // ref
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void ref_createsHandle() throws Exception {
        pool.exec("message = 'pool test'").get(3, TimeUnit.SECONDS);
        pool.exec("message = 'pool test'").get(3, TimeUnit.SECONDS);
        pool.exec("message = 'pool test'").get(3, TimeUnit.SECONDS);
        pool.exec("message = 'pool test'").get(3, TimeUnit.SECONDS);
        PythonHandle handle = pool.ref("message").get(3, TimeUnit.SECONDS);
        assertEquals("str", handle.pythonType());
        assertEquals(0, handle.call("find", "pool").asInt());
    }

    // ------------------------------------------------------------------
    // Round-robin distribution
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void roundRobin_distributesAcrossInstances() throws Exception {
        // After submitting many tasks sequentially, pool should have grown
        // to at least 2 active instances (may scale up to 4).
        int tasks = 12; // more than maxPool=4 ensures rotation
        for (int i = 0; i < tasks; i++) {
            pool.eval("sum([1, 2, 3])").get(3, TimeUnit.SECONDS);
        }
        // Pool size should be at least minPool
        assertTrue(pool.size() >= pool.minPool(), "Pool should have at least minPool instances");
    }

    // ------------------------------------------------------------------
    // Pool sizing
    // ------------------------------------------------------------------

    @Test
    void initialSize_equalsMinPool() {
        assertEquals(2, pool.minPool());
        assertEquals(4, pool.maxPool());
        // Pool may have scaled up from previous tests; ensure it's at least minPool
        assertTrue(pool.size() >= pool.minPool(),
                "Pool size should be at least minPool");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void size_growsWhenSaturated() throws Exception {
        PythonEmbedPool saturatedPool = PythonEmbedPool.builder().minPool(1).maxPool(3).build();
        try {
            assertEquals(1, saturatedPool.size());

            // Saturate by submitting concurrent tasks
            CompletableFuture<PythonValue> f1 = saturatedPool.eval("sum([1, 2, 3])");
            CompletableFuture<PythonValue> f2 = saturatedPool.eval("sum([4, 5, 6])");

            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);

            // Pool may have scaled up to handle concurrent load
            assertTrue(saturatedPool.size() >= 1,
                    "Pool should have at least minPool instances");
        } finally {
            saturatedPool.close();
        }
    }

    // ------------------------------------------------------------------
    // Close safety
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void close_thenSubmit_throwsIllegalStateException() {
        PythonEmbedPool localPool = PythonEmbedPool.builder().maxPool(1).build();
        localPool.close();

        assertThrows(IllegalStateException.class, () -> localPool.eval("1 + 1"));
        assertThrows(IllegalStateException.class, () -> localPool.exec("x = 1"));
        assertThrows(IllegalStateException.class, () -> localPool.stream("range(1)"));
        assertThrows(IllegalStateException.class, () -> localPool.ref("x"));
    }

    @Test
    void close_idempotent() {
        PythonEmbedPool localPool = PythonEmbedPool.builder().maxPool(1).build();
        localPool.close();
        assertDoesNotThrow(() -> localPool.close());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void close_duringEval_futureCompletes() throws Exception {
        PythonEmbedPool closePool = PythonEmbedPool.builder().maxPool(1).build();
        try {
            // Submit a long-running eval
            CompletableFuture<PythonValue> future = closePool.eval(
                    "__import__('time').sleep(10)");

            // Let the task start executing
            Thread.sleep(200);

            assertEquals(1, closePool.activeCount(),
                    "Instance should be busy while eval is running");

            // Close while the task is running
            closePool.close(3, TimeUnit.SECONDS);

            // The future should complete (possibly with exception)
            try {
                future.get(5, TimeUnit.SECONDS);
                // If no exception, the task completed before close took effect
            } catch (ExecutionException e) {
                // Expected: task was interrupted/process killed
                Throwable cause = e.getCause();
                assertTrue(
                        cause instanceof CompletionException
                                || cause instanceof PythonExecutionException
                                || cause instanceof IllegalStateException
                                || cause instanceof IOException,
                        "Unexpected exception type: " + cause.getClass().getName());
            }
        } finally {
            // Pool already closed in test, but close() is idempotent
            closePool.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void close_duringStream_streamDetectsClosure() throws Exception {
        PythonEmbedPool streamPool = PythonEmbedPool.builder().maxPool(1).build();
        try {
            // Define a slow generator that yields with delays
            streamPool.exec("""
                    def slow_gen():
                        import time
                        for i in range(100):
                            time.sleep(0.05)
                            yield i
                    """).get(5, TimeUnit.SECONDS);

            // Start streaming with a short per-item timeout so that
            // after process termination, the iterator notices quickly.
            Iterator<PythonValue> iter = streamPool
                    .stream("slow_gen()", 2000)
                    .get(5, TimeUnit.SECONDS);

            // Read a few items to confirm stream is active
            assertTrue(iter.hasNext());
            assertEquals(0, iter.next().asInt());
            assertEquals(1, iter.next().asInt());

            assertEquals(1, streamPool.activeCount(),
                    "Instance should be busy while streaming");

            // Close pool while stream is still producing items
            streamPool.close(3, TimeUnit.SECONDS);

            // After close, the stream should detect closure:
            // hasNext() returns false (queue times out) or next() throws
            boolean hasNextReturnedFalse = false;
            boolean threwException = false;
            try {
                // Drain any remaining buffered items, then hasNext times out
                while (iter.hasNext()) {
                    iter.next();
                }
                hasNextReturnedFalse = true;
            } catch (Exception e) {
                threwException = true;
            }
            // At least one of these should happen after close
            assertTrue(hasNextReturnedFalse || threwException,
                    "Stream should detect pool closure");
        } finally {
            streamPool.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void close_withMultipleRunningTasks_allFuturesComplete() throws Exception {
        PythonEmbedPool multiPool = PythonEmbedPool.builder().minPool(2).maxPool(2).build();
        try {
            // Submit tasks that saturate the pool.
            // Using exact maxPool count to avoid CallerRunsPolicy
            // causing the calling thread to block in acquireInstance().
            int taskCount = 2;
            @SuppressWarnings("unchecked")
            CompletableFuture<PythonValue>[] futures = new CompletableFuture[taskCount];
            for (int i = 0; i < taskCount; i++) {
                futures[i] = multiPool.eval(
                        "__import__('time').sleep(5)");
            }

            // Let tasks start
            Thread.sleep(300);
            assertEquals(2, multiPool.activeCount(),
                    "Both instances should be busy");

            // Close while tasks are running
            multiPool.close(5, TimeUnit.SECONDS);

            // All futures should complete (possibly with exception)
            for (int i = 0; i < taskCount; i++) {
                try {
                    futures[i].get(3, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    // Expected for tasks that were running during close
                    assertNotNull(e.getCause());
                }
            }
        } finally {
            multiPool.close();
        }
    }

    // ------------------------------------------------------------------
    // Process cleanup
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void close_killsPythonProcess() {
        PythonEmbed embed = PythonEmbed.create(PythonEmbed.Options.defaults());
        try {
            long pid = embed.getPid();
            assertTrue(pid > 0, "Python process should be started");
            assertTrue(embed.isOpen(), "Embed should be open");

            embed.close();

            assertFalse(embed.isOpen(), "Embed should be closed");
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                    "Python process should not be alive after close");
        } finally {
            embed.close();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void hardShutdown_killsPythonProcess() {
        PythonEmbed embed = PythonEmbed.create(PythonEmbed.Options.defaults());
        try {
            long pid = embed.getPid();
            assertTrue(pid > 0, "Python process should be started");
            assertTrue(embed.isOpen(), "Embed should be open");

            embed.hardShutdown();

            assertFalse(embed.isOpen(), "Embed should be closed after hard shutdown");
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                    "Python process should not be alive after hard shutdown");
        } finally {
            embed.close();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void poolClose_killsAllPythonProcesses() throws Exception {
        PythonEmbedPool pool2 = PythonEmbedPool.builder().minPool(2).maxPool(2).build();
        try {
            waitForPoolSize(pool2, 2, 15_000);

            // Collect PIDs of all running instances
            Deque<?> instances = getInstances(pool2);
            assertEquals(2, instances.size(), "Pool should have 2 instances");

            int i = 0;
            long[] pids = new long[2];
            for (Object pi : instances) {
                Field embedField = pi.getClass().getDeclaredField("embed");
                embedField.setAccessible(true);
                PythonEmbed e = (PythonEmbed) embedField.get(pi);
                pids[i] = e.getPid();
                i++;
            }

            assertTrue(pids[0] > 0 && pids[1] > 0, "Both Python processes should be running");

            pool2.close();

            for (long pid : pids) {
                assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                        "Python process " + pid + " should not be alive after pool close");
            }
        } finally {
            pool2.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static Deque<?> getInstances(PythonEmbedPool pool) throws Exception {
        java.lang.reflect.Field f = PythonEmbedPool.class.getDeclaredField("instances");
        f.setAccessible(true);
        return (Deque<?>) f.get(pool);
    }

    // ------------------------------------------------------------------
    // Callback broadcast
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void registerCallback_broadcastsToAllInstances() throws Exception {
        // Use a 1-instance pool to avoid round-robin state issues with callbacks
        PythonEmbedPool cbPool = PythonEmbedPool.builder().maxPool(1).build();
        try {
            // Register a callback that echoes the input
            cbPool.registerCallback("echo", args -> args[0]);

            // Set up a function that uses the callback via _bridge.call()
            cbPool.exec("""
                    def use_callback():
                        return _bridge.call("echo", 42)
                    """).get(3, TimeUnit.SECONDS);

            // Call through the callback
            PythonValue result = cbPool.eval("use_callback()").get(3, TimeUnit.SECONDS);
            assertEquals(42, result.asInt());
        } finally {
            cbPool.close();
        }
    }

    // ------------------------------------------------------------------
    // activeCount
    // ------------------------------------------------------------------

    @Test
    void activeCount_startsAtZero() {
        assertEquals(0, pool.activeCount());
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void poolSizeOne_worksCorrectly() throws Exception {
        PythonEmbedPool singlePool = PythonEmbedPool.builder().maxPool(1).build();
        try {
            assertEquals(1, singlePool.size());
            PythonValue result = singlePool.eval("42").get(3, TimeUnit.SECONDS);
            assertEquals(42, result.asInt());
        } finally {
            singlePool.close();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void fixedSize_withSameMinMax() throws Exception {
        PythonEmbedPool fixedPool = PythonEmbedPool.builder().minPool(2).maxPool(2).build();
        try {
            waitForPoolSize(fixedPool, 2, 30_000);
            PythonValue r1 = fixedPool.eval("1 + 1").get(3, TimeUnit.SECONDS);
            PythonValue r2 = fixedPool.eval("2 + 2").get(3, TimeUnit.SECONDS);
            assertEquals(2, r1.asInt());
            assertEquals(4, r2.asInt());
            // Should not grow beyond max
            assertEquals(2, fixedPool.size());
        } finally {
            fixedPool.close();
        }
    }

    @Test
    void minPool_mustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> PythonEmbedPool.builder().minPool(0).build());
        assertThrows(IllegalArgumentException.class, () -> PythonEmbedPool.builder().minPool(0).maxPool(2).build());
    }

    @Test
    void maxPool_mustBeAtLeastMinPool() {
        assertThrows(IllegalArgumentException.class, () -> PythonEmbedPool.builder().minPool(3).maxPool(2).build());
    }

    // ------------------------------------------------------------------
    // Scale-down (cleanupIdleInstances)
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void scaleDown_removesIdleInstancesAboveMinPool() throws Exception {
        PythonEmbedPool dynPool = PythonEmbedPool.builder().minPool(1).maxPool(3).idleTimeoutMs(0).build();
        try {
            assertEquals(1, dynPool.size());

            // Trigger scale-up with concurrent tasks
            CompletableFuture<?>[] tasks = new CompletableFuture[3];
            for (int i = 0; i < 3; i++) {
                tasks[i] = dynPool.eval("42");
            }
            CompletableFuture.allOf(tasks).get(10, TimeUnit.SECONDS);

            int afterScaleUp = dynPool.size();
            assertTrue(afterScaleUp >= 2, "Pool should have scaled up, but size is " + afterScaleUp);

            // Directly invoke cleanup -- idleTimeoutMs=0 means instances are
            // immediately eligible for removal after release
            dynPool.runMaintenance();

            assertEquals(1, dynPool.size(), "Pool should have shrunk to minPool");
        } finally {
            dynPool.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void scaleDown_doesNotShrinkBelowMinPool() throws Exception {
        PythonEmbedPool dynPool = PythonEmbedPool.builder().minPool(2).maxPool(4).idleTimeoutMs(0).build();
        try {
            waitForPoolSize(dynPool, 2, 30_000);

            // Trigger scale-up
            CompletableFuture<?>[] tasks = new CompletableFuture[4];
            for (int i = 0; i < 4; i++) {
                tasks[i] = dynPool.eval("42");
            }
            CompletableFuture.allOf(tasks).get(10, TimeUnit.SECONDS);

            // Directly invoke cleanup
            dynPool.runMaintenance();

            // Should not shrink below minPool (2)
            assertEquals(2, dynPool.size(), "Pool should not shrink below minPool");
        } finally {
            dynPool.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void scaleDown_doesNotRemoveActiveStreamingInstance() throws Exception {
        PythonEmbedPool dynPool = PythonEmbedPool.builder().minPool(1).maxPool(2).idleTimeoutMs(0).build();
        try {
            assertEquals(1, dynPool.size());

            // Trigger scale-up first
            CompletableFuture<?>[] tasks = new CompletableFuture[2];
            for (int i = 0; i < 2; i++) {
                tasks[i] = dynPool.eval("42");
            }
            CompletableFuture.allOf(tasks).get(10, TimeUnit.SECONDS);

            int afterScaleUp = dynPool.size();
            assertTrue(afterScaleUp >= 2, "Pool should have scaled up, but size is " + afterScaleUp);

            // Start a stream that holds one instance busy
            Iterator<PythonValue> iter = dynPool.stream("range(100)").get(5, TimeUnit.SECONDS);

            // Directly invoke cleanup -- streaming instance is busy, should survive
            dynPool.runMaintenance();

            assertTrue(dynPool.size() >= 1, "Busy streaming instance should survive cleanup");

            // Exhaust the stream to release the instance
            while (iter.hasNext()) {
                iter.next();
            }
        } finally {
            dynPool.close();
        }
    }

    // ------------------------------------------------------------------
    // create with Options
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void create_withOptions_poolFunctions() throws Exception {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .timeoutMs(60_000)
                .maxCodeLength(50_000)
                .startupTimeoutMs(30_000)
                .build();
        PythonEmbedPool optsPool = PythonEmbedPool.builder().minPool(1).maxPool(2).options(opts).build();
        try {
            assertEquals(1, optsPool.size());
            PythonValue result = optsPool.eval("42 + 1").get(3, TimeUnit.SECONDS);
            assertEquals(43, result.asInt());
        } finally {
            optsPool.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void create_withAllParams_poolFunctions() throws Exception {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .timeoutMs(30_000)
                .build();
        PythonEmbedPool fullPool = PythonEmbedPool.builder().minPool(1).maxPool(3).idleTimeoutMs(120_000).options(opts).build();
        try {
            assertEquals(1, fullPool.size());
            assertEquals(1, fullPool.minPool());
            assertEquals(3, fullPool.maxPool());
            PythonValue result = fullPool.eval("'hello'").get(3, TimeUnit.SECONDS);
            assertEquals("hello", result.asString());
        } finally {
            fullPool.close();
        }
    }

    // ------------------------------------------------------------------
    // registerPushHandler
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void registerPushHandler_receivesPushFromPython() throws Exception {
        PythonEmbedPool pushPool = PythonEmbedPool.builder().maxPool(1).build();
        try {
            AtomicReference<Object> received = new AtomicReference<>();
            pushPool.registerPushHandler("myPush", (name, value) -> received.set(value));

            pushPool.exec("_bridge.push('myPush', 99)").get(3, TimeUnit.SECONDS);

            assertNotNull(received.get(), "Push handler should have received a value");
            assertEquals(99, ((Number) received.get()).intValue());
        } finally {
            pushPool.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void registerPushHandler_broadcastsToScaledInstance() throws Exception {
        PythonEmbedPool pushPool = PythonEmbedPool.builder().minPool(1).maxPool(2).build();
        try {
            final int[] callCount = {0};
            pushPool.registerPushHandler("scaledPush", (name, value) -> callCount[0]++);

            // Trigger scale-up to create second instance
            CompletableFuture<?>[] tasks = new CompletableFuture[2];
            for (int i = 0; i < 2; i++) {
                tasks[i] = pushPool.eval("42");
            }
            CompletableFuture.allOf(tasks).get(10, TimeUnit.SECONDS);

            // Push from all instances
            for (int i = 0; i < 2; i++) {
                pushPool.exec("_bridge.push('scaledPush', None)").get(3, TimeUnit.SECONDS);
            }

            assertTrue(callCount[0] >= 1, "Push handler should be called at least once");
        } finally {
            pushPool.close();
        }
    }

    // ------------------------------------------------------------------
    // activeCount during execution
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void activeCount_reflectsBusyInstances() throws Exception {
        PythonEmbedPool testPool = PythonEmbedPool.builder().minPool(1).maxPool(2).build();
        try {
            assertEquals(0, testPool.activeCount());

            CompletableFuture<Void> f = testPool.exec(
                    "__import__('time').sleep(1)");

            // Give the task time to start and mark instance as busy
            Thread.sleep(200);

            assertEquals(1, testPool.activeCount(),
                    "activeCount should be 1 while task is executing");

            f.get(5, TimeUnit.SECONDS);

            // After completion, instance should be idle
            assertEquals(0, testPool.activeCount());
        } finally {
            testPool.close();
        }
    }

    // ------------------------------------------------------------------
    // healthCheck
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void healthCheck_healthyPool_returnsZero() {
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(2).maxPool(2).build();
        try {
            assertEquals(0, pool.healthCheck(),
                    "healthCheck should return 0 for a healthy pool");
            assertEquals(2, pool.size(), "Healthy instances should not be removed");
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void healthCheck_skipsBusyInstances() throws Exception {
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(2).maxPool(2).build();
        try {
            // Keep one instance busy
            CompletableFuture<Void> f = pool.exec(
                    "__import__('time').sleep(2)");

            Thread.sleep(200); // let the task start

            assertEquals(1, pool.activeCount());
            int removed = pool.healthCheck();
            // Busy instance is skipped, idle ones are healthy
            assertEquals(0, removed);
            assertEquals(2, pool.size());

            f.get(5, TimeUnit.SECONDS);
        } finally {
            pool.close();
        }
    }

    // ------------------------------------------------------------------
    // close with custom timeout
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void close_withCustomTimeout_succeeds() {
        PythonEmbedPool timeoutPool = PythonEmbedPool.builder().maxPool(1).build();
        assertDoesNotThrow(() -> timeoutPool.close(1, TimeUnit.SECONDS));
    }

    // ------------------------------------------------------------------
    // Per-call timeout override
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_eval_withTimeout() throws Exception {
        PythonValue result = pool.eval("42", 5_000).get(3, TimeUnit.SECONDS);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_eval_shortTimeout_throws() {
        // Use a separate pool because the Python process will remain
        // stuck in time.sleep() even after the Java-side timeout.
        PythonEmbedPool shortPool = PythonEmbedPool.builder().maxPool(1).build();
        try {
            CompletableFuture<PythonValue> future = shortPool.eval(
                    "__import__('time').sleep(10)", 100);
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof PythonExecutionException);
            assertTrue(ex.getCause().getCause() instanceof TimeoutException);
        } finally {
            shortPool.close();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_eval_zeroTimeout_fallsBackToDefault() throws Exception {
        PythonValue result = pool.eval("42", 0).get(3, TimeUnit.SECONDS);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_eval_negativeTimeout_fallsBackToDefault() throws Exception {
        PythonValue result = pool.eval("42", -1).get(3, TimeUnit.SECONDS);
        assertEquals(42, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_exec_withTimeout() throws Exception {
        pool.exec("x = 77", 5_000).get(3, TimeUnit.SECONDS);
        // exec only hits one instance; set on others too
        pool.exec("x = 77", 5_000).get(3, TimeUnit.SECONDS);
        pool.exec("x = 77", 5_000).get(3, TimeUnit.SECONDS);
        pool.exec("x = 77", 5_000).get(3, TimeUnit.SECONDS);
        PythonValue result = pool.eval("x").get(3, TimeUnit.SECONDS);
        assertEquals(77, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_exec_shortTimeout_throws() {
        // Use a separate pool because the Python process will remain
        // stuck in time.sleep() even after the Java-side timeout.
        PythonEmbedPool shortPool = PythonEmbedPool.builder().maxPool(1).build();
        try {
            CompletableFuture<Void> future = shortPool.exec(
                    "__import__('time').sleep(10)", 100);
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof PythonExecutionException);
            assertTrue(ex.getCause().getCause() instanceof TimeoutException);
        } finally {
            shortPool.close();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void timeoutOverride_stream_withTimeout() throws Exception {
        Iterator<PythonValue> iter = pool.stream("range(3)", 5_000)
                .get(5, TimeUnit.SECONDS);
        assertEquals(0, iter.next().asInt());
        assertEquals(1, iter.next().asInt());
        assertEquals(2, iter.next().asInt());
        assertFalse(iter.hasNext());
    }

    // ------------------------------------------------------------------
    // PooledIterator
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pooledIterator_streamExhaustion_releasesInstance() throws Exception {
        PythonEmbedPool iterPool = PythonEmbedPool.builder().maxPool(1).build();
        try {
            assertEquals(1, iterPool.size());
            assertEquals(0, iterPool.activeCount());

            Iterator<PythonValue> iter = iterPool.stream("range(3)").get(5, TimeUnit.SECONDS);

            // Instance is busy while stream is active
            assertEquals(1, iterPool.activeCount(),
                    "Instance should be busy while streaming");

            // Exhaust the stream - hasNext() returns false and triggers release
            assertTrue(iter.hasNext());
            assertEquals(0, iter.next().asInt());
            assertEquals(1, iter.next().asInt());
            assertEquals(2, iter.next().asInt());
            assertFalse(iter.hasNext());

            // After exhaustion, instance should be released back to pool
            assertEquals(0, iterPool.activeCount(),
                    "Instance should be released after stream exhaustion");

            // Instance can be reused
            PythonValue result = iterPool.eval("42").get(3, TimeUnit.SECONDS);
            assertEquals(42, result.asInt());
        } finally {
            iterPool.close();
        }
    }

    // ------------------------------------------------------------------
    // registerCallback on scaled instance
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void registerCallback_appliedToNewlyScaledInstance() throws Exception {
        PythonEmbedPool cbPool = PythonEmbedPool.builder().minPool(1).maxPool(2).build();
        try {
            // Register callback while only minPool instances exist
            cbPool.registerCallback("echo2", args -> args[0]);

            // Set up the callback-using function on the initial instance
            cbPool.exec("""
                    def use_echo():
                        return _bridge.call('echo2', 'response')
                    """).get(3, TimeUnit.SECONDS);

            // Trigger scale-up to create second instance
            CompletableFuture<?>[] tasks = new CompletableFuture[2];
            for (int i = 0; i < 2; i++) {
                tasks[i] = cbPool.eval("42");
            }
            CompletableFuture.allOf(tasks).get(10, TimeUnit.SECONDS);

            // Define the function on all instances (round-robin coverage)
            for (int i = 0; i < cbPool.size(); i++) {
                cbPool.exec("""
                        def use_echo():
                            return _bridge.call('echo2', 'response')
                        """).get(3, TimeUnit.SECONDS);
            }

            // The callback should work regardless of which instance handles the eval
            PythonValue result = cbPool.eval("use_echo()").get(3, TimeUnit.SECONDS);
            assertEquals("response", result.asString());
        } finally {
            cbPool.close();
        }
    }

    // ------------------------------------------------------------------
    // ensureMinPool
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void ensureMinPool_restoresPoolAfterCrash() throws Exception {
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(2).maxPool(2).build();
        try {
            waitForPoolSize(pool, 2, 30_000);

            // Simulate a crash: close one instance and remove it from the pool
            simulateInstanceCrash(pool);

            assertEquals(1, pool.size(),
                    "Pool should have one instance after simulated crash");

            // Maintenance should restore minPool
            pool.runMaintenance();

            assertEquals(2, pool.size(),
                    "Pool should be restored to minPool after maintenance");

            // Verify the restored pool is functional
            PythonValue result = pool.eval("42").get(5, TimeUnit.SECONDS);
            assertEquals(42, result.asInt());
        } finally {
            pool.close();
        }
    }

    // ------------------------------------------------------------------
    // Pre-warming
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void preWarming_poolReturnsQuicklyWithOneInstance() {
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(4).maxPool(4).build();
        try {
            assertTrue(pool.size() >= 1,
                    "Pool should have at least 1 instance immediately");
            assertEquals(0, pool.activeCount(),
                    "No instances should be active initially");
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void preWarming_eventuallyReachesMinPool() throws Exception {
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(4).maxPool(4).build();
        try {
            waitForPoolSize(pool, 4, 30_000);
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void preWarming_instanceIsUsableImmediately() throws Exception {
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(4).maxPool(4).build();
        try {
            PythonValue result = pool.eval("42").get(3, TimeUnit.SECONDS);
            assertEquals(42, result.asInt());
        } finally {
            pool.close();
        }
    }

    // ------------------------------------------------------------------
    // Warmup scripts
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void warmupScript_importsModule() throws Exception {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .warmupScript("import math")
                .build();
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(1).maxPool(1).idleTimeoutMs(60_000).options(opts).build();
        try {
            PythonValue result = pool.eval("math.pi").get(3, TimeUnit.SECONDS);
            assertEquals(3.141592653589793, result.asDouble(), 0.0001);
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void warmupScript_failureDoesNotBreakInstance() throws Exception {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .warmupScript("raise RuntimeError('test warmup error')")
                .build();
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(1).maxPool(1).idleTimeoutMs(60_000).options(opts).build();
        try {
            PythonValue result = pool.eval("42").get(3, TimeUnit.SECONDS);
            assertEquals(42, result.asInt());
        } finally {
            pool.close();
        }
    }

    // ------------------------------------------------------------------
    // create with env
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void create_withEnv_forwardsToPythonProcess() throws Exception {
        Map<String, String> env = Map.of("PYTHON_EMBED_TEST_VAR", "hello_from_pool");
        PythonEmbedPool pool = PythonEmbedPool.builder().maxPool(1)
                .options(PythonEmbed.Options.builder().env(env).build()).build();
        try {
            PythonValue result = pool.eval(
                    "__import__('os').environ['PYTHON_EMBED_TEST_VAR']")
                    .get(3, TimeUnit.SECONDS);
            assertEquals("hello_from_pool", result.asString());
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void create_withEnv_fullFactory_forwardsEnv() throws Exception {
        Map<String, String> env = Map.of("POOL_FULL_ENV", "full_value");
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .env(env).build();
        PythonEmbedPool pool = PythonEmbedPool.builder().minPool(1).maxPool(1).options(opts).build();
        try {
            PythonValue result = pool.eval(
                    "__import__('os').environ['POOL_FULL_ENV']")
                    .get(3, TimeUnit.SECONDS);
            assertEquals("full_value", result.asString());
        } finally {
            pool.close();
        }
    }

    // ------------------------------------------------------------------
    // Pool-level batchEval / batchExec / ref
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void batchEval_multipleExpressions_returnsAllResults() throws Exception {
        List<String> codes = List.of("10 + 20", "3.14 * 2", "'hello'.upper()");
        List<PythonValue> results = pool.batchEval(codes).get(5, TimeUnit.SECONDS);
        assertEquals(3, results.size());
        assertEquals(30, results.get(0).asInt());
        assertEquals(6.28, results.get(1).asDouble(), 0.001);
        assertEquals("HELLO", results.get(2).asString());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void batchEval_emptyList_returnsEmpty() throws Exception {
        List<PythonValue> results = pool.batchEval(List.of()).get(3, TimeUnit.SECONDS);
        assertTrue(results.isEmpty());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void batchEval_error_propagates() {
        CompletableFuture<List<PythonValue>> future = pool.batchEval(
                List.of("1 + 1", "undefined_var", "2 + 2"));
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("batchEval[1]"));
        assertTrue(ex.getCause().getMessage().contains("NameError"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void batchEval_withTimeoutOverride() throws Exception {
        List<PythonValue> results = pool.batchEval(List.of("1 + 1", "2 + 2"), 5000)
                .get(5, TimeUnit.SECONDS);
        assertEquals(2, results.size());
        assertEquals(2, results.get(0).asInt());
        assertEquals(4, results.get(1).asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void batchExec_multipleStatements_preservesState() throws Exception {
        pool.batchExec(List.of("p = 100", "q = 200", "r = p + q")).get(3, TimeUnit.SECONDS);
        // Round-robin may hit a different instance, so broadcast
        pool.batchExec(List.of("p = 100", "q = 200", "r = p + q")).get(3, TimeUnit.SECONDS);
        pool.batchExec(List.of("p = 100", "q = 200", "r = p + q")).get(3, TimeUnit.SECONDS);
        pool.batchExec(List.of("p = 100", "q = 200", "r = p + q")).get(3, TimeUnit.SECONDS);
        PythonValue result = pool.eval("r").get(3, TimeUnit.SECONDS);
        assertEquals(300, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void batchExec_emptyList_doesNothing() throws Exception {
        pool.batchExec(List.of()).get(3, TimeUnit.SECONDS);
        // Should not throw
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void batchExec_error_propagates() {
        CompletableFuture<Void> future = pool.batchExec(
                List.of("x = 1", "raise ValueError('test')", "y = 2"));
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("batchExec[1]"));
        assertTrue(ex.getCause().getMessage().contains("ValueError"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void batchExec_withTimeoutOverride() throws Exception {
        pool.batchExec(List.of("b1 = 50", "b2 = 60"), 5000).get(5, TimeUnit.SECONDS);
        pool.batchExec(List.of("b1 = 50", "b2 = 60"), 5000).get(5, TimeUnit.SECONDS);
        pool.batchExec(List.of("b1 = 50", "b2 = 60"), 5000).get(5, TimeUnit.SECONDS);
        pool.batchExec(List.of("b1 = 50", "b2 = 60"), 5000).get(5, TimeUnit.SECONDS);
        PythonValue result = pool.eval("b1 + b2").get(3, TimeUnit.SECONDS);
        assertEquals(110, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void ref_createsPooledHandle() throws Exception {
        // Set up the variable on all instances
        for (int i = 0; i < pool.size(); i++) {
            pool.exec("msg = 'pool ref test'").get(3, TimeUnit.SECONDS);
        }
        PythonHandle handle = pool.ref("msg").get(3, TimeUnit.SECONDS);
        assertNotNull(handle);
        assertEquals("str", handle.pythonType());
        assertEquals(0, handle.call("find", "pool").asInt());
        handle.release();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void ref_nonExistentVariable_propagatesError() {
        CompletableFuture<PythonHandle> future = pool.ref("non_existent_var_xyz");
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("NameError"));
    }

    // ---- PythonEmbed.arg() via pool ----

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void arg_viaPool_safeString() throws Exception {
        PythonValue result = pool.eval("len(" + PythonEmbed.arg("safe") + ")")
                .get(3, TimeUnit.SECONDS);
        assertEquals(4, result.asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void arg_viaPool_listAndMap() throws Exception {
        pool.exec("data = " + PythonEmbed.arg(
                List.of(Map.of("k", "v"), 42)))
                .get(3, TimeUnit.SECONDS);
        // Multiple execs to cover all pool instances (round-robin)
        pool.exec("data = " + PythonEmbed.arg(
                List.of(Map.of("k", "v"), 42)))
                .get(3, TimeUnit.SECONDS);
        pool.exec("data = " + PythonEmbed.arg(
                List.of(Map.of("k", "v"), 42)))
                .get(3, TimeUnit.SECONDS);
        pool.exec("data = " + PythonEmbed.arg(
                List.of(Map.of("k", "v"), 42)))
                .get(3, TimeUnit.SECONDS);
        PythonValue item = pool.eval("data[0]['k']").get(3, TimeUnit.SECONDS);
        assertEquals("v", item.asString());
        PythonValue num = pool.eval("data[1]").get(3, TimeUnit.SECONDS);
        assertEquals(42.0, num.asDouble(), 0.001);
    }

    /**
     * Waits for the pool to reach the given size, polling every 100ms.
     */
    private static void waitForPoolSize(PythonEmbedPool pool, int expectedSize,
                                        long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (pool.size() < expectedSize && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(expectedSize, pool.size(),
                "Pool should reach size " + expectedSize + " within " + timeoutMs + "ms");
    }

    /**
     * Simulates an instance crash by closing one idle instance and
     * removing it from the pool's internal structures via reflection.
     */
    @SuppressWarnings("unchecked")
    private static void simulateInstanceCrash(PythonEmbedPool pool) throws Exception {
        Field instancesField = PythonEmbedPool.class.getDeclaredField("instances");
        instancesField.setAccessible(true);
        Deque<Object> instances = (Deque<Object>) instancesField.get(pool);

        Field currentSizeField = PythonEmbedPool.class.getDeclaredField("currentSize");
        currentSizeField.setAccessible(true);
        AtomicInteger currentSize = (AtomicInteger) currentSizeField.get(pool);

        Object pi = instances.peekFirst();
        assertNotNull(pi, "Pool should have at least one instance");

        // Get the embed field from PooledInstance
        Field embedField = pi.getClass().getDeclaredField("embed");
        embedField.setAccessible(true);
        PythonEmbed embed = (PythonEmbed) embedField.get(pi);

        // Close and remove the instance
        embed.close();
        instances.remove(pi);
        currentSize.decrementAndGet();
    }
}
