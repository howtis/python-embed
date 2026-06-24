package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PythonProcessManagerTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = PythonProcessManager.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) {
        try {
            Field f = PythonProcessManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final PythonProtocol dummyProtocol = new PythonProtocol() {
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
        @Override byte[] buildBatchRequest(int[] ids, java.util.List<java.util.Map<String, Object>> items) { return new byte[0]; }
        @Override void handleResponse(byte[] raw) { }
    };

    private Path tempDir;

    @AfterEach
    void cleanupTempDir() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                        });
            }
        }
    }

    // ------------------------------------------------------------------
    // resolvePythonExecutable()
    // ------------------------------------------------------------------

    @Test
    void resolvePythonExecutable_venvLayout() throws Exception {
        tempDir = Files.createTempDirectory("test-venv-");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path pythonExe;
        if (isWindows) {
            Path scriptsDir = Files.createDirectories(tempDir.resolve("Scripts"));
            pythonExe = scriptsDir.resolve("python.exe");
        } else {
            Path binDir = Files.createDirectories(tempDir.resolve("bin"));
            pythonExe = binDir.resolve("python3");
        }
        Files.createFile(pythonExe);

        String resolved = PythonProcessManager.resolvePythonExecutable(tempDir);
        assertEquals(pythonExe.toAbsolutePath().toString(), resolved);
    }

    @Test
    void resolvePythonExecutable_standaloneLayout() throws Exception {
        tempDir = Files.createTempDirectory("test-standalone-");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path pythonExe;
        if (isWindows) {
            pythonExe = tempDir.resolve("python.exe");
        } else {
            Path binDir = Files.createDirectories(tempDir.resolve("bin"));
            pythonExe = binDir.resolve("python3");
        }
        Files.createFile(pythonExe);

        String resolved = PythonProcessManager.resolvePythonExecutable(tempDir);
        assertEquals(pythonExe.toAbsolutePath().toString(), resolved);
    }

    @Test
    void resolvePythonExecutable_venvLayoutPreferredOverStandalone() throws Exception {
        tempDir = Files.createTempDirectory("test-both-");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path preferredPy;
        if (isWindows) {
            Path scriptsDir = Files.createDirectories(tempDir.resolve("Scripts"));
            preferredPy = scriptsDir.resolve("python.exe");
            Files.createFile(preferredPy);
            Path standalonePy = tempDir.resolve("python.exe");
            Files.createFile(standalonePy);
        } else {
            Path binDir = Files.createDirectories(tempDir.resolve("bin"));
            preferredPy = binDir.resolve("python3");
            Files.createFile(preferredPy);
            Path fallbackPy = binDir.resolve("python");
            Files.createFile(fallbackPy);
        }

        String resolved = PythonProcessManager.resolvePythonExecutable(tempDir);
        assertEquals(preferredPy.toAbsolutePath().toString(), resolved,
                "venv layout should be preferred over standalone");
    }

    @Test
    void resolvePythonExecutable_fallbackWhenNeitherExists() {
        tempDir = Path.of("nonexistent-" + System.nanoTime());
        String resolved = PythonProcessManager.resolvePythonExecutable(tempDir);
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            assertTrue(resolved.endsWith("Scripts/py" + "thon.exe")
                    || resolved.endsWith("Scripts\\py" + "thon.exe"),
                    "should fall back to Scripts/python.exe path: " + resolved);
        } else {
            assertTrue(resolved.endsWith("bin/python3")
                    || resolved.endsWith("bin\\python3"),
                    "should fall back to bin/python3 path: " + resolved);
        }
    }

    @Test
    void resolvePythonExecutable_emptyBasePath() {
        String resolved = PythonProcessManager.resolvePythonExecutable(Path.of(""));
        assertNotNull(resolved);
        assertFalse(resolved.isEmpty());
    }

    // ------------------------------------------------------------------
    // isRunning()
    // ------------------------------------------------------------------

    @Test
    void isRunning_notStarted_returnsFalse() {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        assertFalse(mgr.isRunning());
    }

    @Test
    void isRunning_runningTrueButProcessNull_returnsFalse() {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "running", true);
        assertFalse(mgr.isRunning());
    }

    @Test
    void isRunning_runningFalseProcessAlive_returnsFalse() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createAliveProcess());
        setField(mgr, "running", false);
        assertFalse(mgr.isRunning());
    }

    @Test
    void isRunning_runningTrueProcessAlive_returnsTrue() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createAliveProcess());
        setField(mgr, "running", true);
        assertTrue(mgr.isRunning());
    }

    @Test
    void isRunning_runningTrueProcessDead_returnsFalse() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createDeadProcess());
        setField(mgr, "running", true);
        assertFalse(mgr.isRunning());
    }

    // ------------------------------------------------------------------
    // getPid()
    // ------------------------------------------------------------------

    @Test
    void getPid_processNull_returnsMinusOne() {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        assertEquals(-1L, mgr.getPid());
    }

    @Test
    void getPid_processPresent_returnsPid() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createProcessWithPid(42L));
        assertEquals(42L, mgr.getPid());
    }

    @Test
    void getPid_processPidThrows_returnsMinusOne() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createThrowingPidProcess());
        assertEquals(-1L, mgr.getPid());
    }

    // ------------------------------------------------------------------
    // stdinWriter() - behavior and concurrency
    // ------------------------------------------------------------------

    @Test
    void stdinWriter_writeWhenRunning_succeeds() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", baos);

        PythonProtocol.Writer writer = mgr.stdinWriter();
        writer.write(new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, baos.toByteArray());
    }

    @Test
    void stdinWriter_writeWhenNotRunning_throwsIOException() {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        setField(mgr, "running", false);
        setField(mgr, "stdinStream", baos);

        PythonProtocol.Writer writer = mgr.stdinWriter();
        IOException ex = assertThrows(IOException.class, () -> writer.write(new byte[]{1}));
        assertEquals("Python process is not running", ex.getMessage());
    }

    @Test
    void stdinWriter_writeNullStreamWhenRunning_throwsNPE() {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "running", true);

        PythonProtocol.Writer writer = mgr.stdinWriter();
        assertThrows(NullPointerException.class, () -> writer.write(new byte[]{1}));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void stdinWriter_concurrentWrites_areSynchronized() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", baos);

        PythonProtocol.Writer writer = mgr.stdinWriter();
        int numThreads = 10;
        int writesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            byte marker = (byte) t;
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < writesPerThread; i++) {
                        writer.write(new byte[]{marker});
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertEquals(0, errors.get(), "no write should fail under concurrency");
        assertEquals(numThreads * writesPerThread, baos.toByteArray().length,
                "all bytes should be written");
    }

    @Test
    void stdinWriter_multipleWriters_shareSameLock() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", baos);

        PythonProtocol.Writer w1 = mgr.stdinWriter();
        PythonProtocol.Writer w2 = mgr.stdinWriter();

        w1.write(new byte[]{1});
        w2.write(new byte[]{2});
        assertEquals(2, baos.toByteArray().length);
        assertEquals(1, baos.toByteArray()[0]);
        assertEquals(2, baos.toByteArray()[1]);
    }

    @Test
    void stdinWriter_autoFlushesAfterWrite() throws Exception {
        // stdinWriter is configured with autoFlush=true:
        // every write() is immediately followed by flush()
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        AtomicInteger flushCount = new AtomicInteger(0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream() {
            @Override
            public void flush() throws IOException {
                flushCount.incrementAndGet();
                super.flush();
            }
        };
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", baos);

        PythonProtocol.Writer writer = mgr.stdinWriter();
        writer.write(new byte[]{1, 2, 3});
        writer.write(new byte[]{4, 5});

        assertEquals(2, flushCount.get(),
                "flush() should be called once per write (autoFlush)");
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, baos.toByteArray());
    }

    // ------------------------------------------------------------------
    // close() - default timeouts
    // ------------------------------------------------------------------

    @Test
    void close_processNull_doesNotThrow() {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        assertDoesNotThrow(() -> mgr.close());
    }

    @Test
    void close_processAlreadyDead_doesNotThrow() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createDeadProcess());
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", OutputStream.nullOutputStream());
        setField(mgr, "stdoutStream", InputStream.nullInputStream());
        assertDoesNotThrow(() -> mgr.close());
    }

    @Test
    void close_marksRunningFalse() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createDeadProcess());
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", OutputStream.nullOutputStream());
        setField(mgr, "stdoutStream", InputStream.nullInputStream());

        mgr.close();
        assertFalse((boolean) getField(mgr, "running"), "running should be set to false");
    }

    // ------------------------------------------------------------------
    // close(waitMs, forceWaitMs) - timeout edge cases
    // ------------------------------------------------------------------

    @Test
    void close_zeroTimeouts_doesNotThrow() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createDeadProcess());
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", OutputStream.nullOutputStream());
        setField(mgr, "stdoutStream", InputStream.nullInputStream());

        assertDoesNotThrow(() -> mgr.close(0, 0));
    }

    @Test
    void close_negativeTimeouts_doesNotThrow() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createDeadProcess());
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", OutputStream.nullOutputStream());
        setField(mgr, "stdoutStream", InputStream.nullInputStream());

        assertDoesNotThrow(() -> mgr.close(-1, -1));
    }

    @Test
    void close_largeTimeouts_processDead_doesNotHang() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createDeadProcess());
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", OutputStream.nullOutputStream());
        setField(mgr, "stdoutStream", InputStream.nullInputStream());

        assertDoesNotThrow(() -> mgr.close(60_000, 60_000));
    }

    // ------------------------------------------------------------------
    // hardShutdown()
    // ------------------------------------------------------------------

    @Test
    void hardShutdown_processNull_doesNotThrow() {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        assertDoesNotThrow(() -> mgr.hardShutdown());
    }

    @Test
    void hardShutdown_processAlive_destroysProcess() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        AtomicBoolean destroyed = new AtomicBoolean(false);
        setField(mgr, "process", createDestroyTrackingProcess(destroyed, true));
        setField(mgr, "running", true);

        mgr.hardShutdown();
        assertTrue(destroyed.get(), "process should be destroyed forcibly");
        assertFalse((boolean) getField(mgr, "running"), "running should be set to false");
    }

    @Test
    void hardShutdown_processAlreadyDead_doesNotCallDestroy() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        AtomicBoolean destroyCalled = new AtomicBoolean(false);
        setField(mgr, "process", createDestroyTrackingProcess(destroyCalled, false));
        setField(mgr, "running", true);

        mgr.hardShutdown();
        assertFalse(destroyCalled.get(), "destroy should not be called when process is already dead");
    }

    // ------------------------------------------------------------------
    // close() vs hardShutdown() - state transitions
    // ------------------------------------------------------------------

    @Test
    void closeAfterHardShutdown_doesNotThrow() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        setField(mgr, "process", createDeadProcess());
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", baos);
        setField(mgr, "stdoutStream", InputStream.nullInputStream());

        mgr.hardShutdown();
        assertDoesNotThrow(() -> mgr.close());
    }

    @Test
    void hardShutdownAfterClose_doesNotThrow() throws Exception {
        PythonProcessManager mgr = new PythonProcessManager(dummyProtocol);
        setField(mgr, "process", createDeadProcess());
        setField(mgr, "running", true);
        setField(mgr, "stdinStream", OutputStream.nullOutputStream());
        setField(mgr, "stdoutStream", InputStream.nullInputStream());

        mgr.close();
        assertDoesNotThrow(() -> mgr.hardShutdown());
    }

    // ------------------------------------------------------------------
    // protocol()
    // ------------------------------------------------------------------

    @Test
    void protocol_returnsConstructorArg() {
        PythonProtocol proto = new PythonProtocol() {
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
            @Override byte[] buildBatchRequest(int[] ids, java.util.List<java.util.Map<String, Object>> items) { return new byte[0]; }
            @Override void handleResponse(byte[] raw) { }
        };
        PythonProcessManager mgr = new PythonProcessManager(proto);
        assertSame(proto, mgr.protocol());
    }

    // ------------------------------------------------------------------
    // Mock Process factories
    // ------------------------------------------------------------------

    private static Process createAliveProcess() {
        return new Process() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
            @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            @Override public int waitFor() { return 0; }
            @Override public boolean waitFor(long timeout, TimeUnit unit) { return true; }
            @Override public int exitValue() { throw new IllegalThreadStateException("not exited"); }
            @Override public void destroy() { }
            @Override public boolean isAlive() { return true; }
        };
    }

    private static Process createDeadProcess() {
        return new Process() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
            @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            @Override public int waitFor() { return 0; }
            @Override public boolean waitFor(long timeout, TimeUnit unit) { return true; }
            @Override public int exitValue() { return 0; }
            @Override public void destroy() { }
            @Override public boolean isAlive() { return false; }
        };
    }

    private static Process createProcessWithPid(long pid) {
        return new Process() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
            @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            @Override public int waitFor() { return 0; }
            @Override public int exitValue() { return 0; }
            @Override public void destroy() { }
            @Override public long pid() { return pid; }
            @Override public boolean isAlive() { return true; }
        };
    }

    private static Process createThrowingPidProcess() {
        return new Process() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
            @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            @Override public int waitFor() { return 0; }
            @Override public int exitValue() { return 0; }
            @Override public void destroy() { }
            @Override public long pid() { throw new UnsupportedOperationException("no pid"); }
            @Override public boolean isAlive() { return true; }
        };
    }

    private static Process createDestroyTrackingProcess(AtomicBoolean destroyed, boolean alive) {
        return new Process() {
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
            @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            @Override public int waitFor() { return 0; }
            @Override public int exitValue() { return 0; }
            @Override public void destroy() { }
            @Override public Process destroyForcibly() { destroyed.set(true); return this; }
            @Override public boolean isAlive() { return alive; }
        };
    }
}
