package io.github.howtis.pythonembed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Manages a persistent CPython REPL process that communicates via stdin/stdout
 * using the binary MessagePack protocol (length-prefixed frames).
 */
class PythonProcessManager {

    private static final Logger logger = Logger.getLogger(PythonProcessManager.class.getName());

    private Process process;
    private OutputStream stdinStream;
    private InputStream stdoutStream;
    private BufferedReader stderrReader;
    private ExecutorService stdoutReader;
    private ExecutorService stderrLogger;
    private final PythonProtocol protocol;
    private volatile boolean running;

    PythonProcessManager(PythonProtocol protocol) {
        this.protocol = protocol;
    }

    /**
     * Starts the CPython process running bridge.py from the given venv path.
     *
     * @param venvPath path to the venv root directory
     * @param bridgeScriptPath path to bridge.py (on the filesystem)
     * @param env environment variables to pass to the Python process (may be empty or null)
     */
    void start(Path venvPath, Path bridgeScriptPath, Map<String, String> env) throws IOException {
        start(venvPath, bridgeScriptPath, env, 100_000, null, 30_000);
    }

    /**
     * Starts the CPython process.
     *
     * @param venvPath path to the venv root directory
     * @param bridgeScriptPath path to bridge.py
     * @param env environment variables
     * @param maxCodeLength maximum code length to pass to bridge.py
     * @param pythonExecutableOverride if non-null, use this as the Python executable
     * @param startupTimeoutMs timeout for process startup in milliseconds
     */
    void start(Path venvPath, Path bridgeScriptPath, Map<String, String> env,
               int maxCodeLength, String pythonExecutableOverride, long startupTimeoutMs)
            throws IOException {
        String pythonExe;
        if (pythonExecutableOverride != null && !pythonExecutableOverride.isEmpty()) {
            pythonExe = pythonExecutableOverride;
            logger.info(() -> "Using overridden Python executable: " + pythonExe);
        } else {
            pythonExe = resolvePythonExecutable(venvPath);
        }
        String bridgeArg = bridgeScriptPath.toAbsolutePath().toString();

        ProcessBuilder pb = new ProcessBuilder(pythonExe, "-u", bridgeArg, "--binary",
                "--max-code-length", String.valueOf(maxCodeLength));
        logger.info(() -> "Starting Python process (binary MessagePack mode): "
                + pythonExe + " -u " + bridgeArg + " --binary"
                + " --max-code-length " + maxCodeLength);
        pb.redirectErrorStream(false);

        // Add venv site-packages to PYTHONPATH so msgpack is importable
        Map<String, String> pbEnv = pb.environment();
        if (venvPath != null) {
            Path sitePackages = findVenvSitePackages(venvPath);
            if (sitePackages != null) {
                String existing = pbEnv.getOrDefault("PYTHONPATH", "");
                String newPath = sitePackages.toAbsolutePath().toString();
                String pythonPath = existing.isEmpty() ? newPath : newPath + java.io.File.pathSeparator + existing;
                pbEnv.put("PYTHONPATH", pythonPath);
                logger.info(() -> "Python process PYTHONPATH: " + pythonPath);
            }
        }

        // Apply environment variables (may override PYTHONPATH)
        if (env != null && !env.isEmpty()) {
            pbEnv.putAll(env);
            logger.info(() -> "Python process env vars: " + env.keySet());
        }

        process = pb.start();
        running = true;

        stdinStream = process.getOutputStream();
        stdoutStream = process.getInputStream();

        // Raw byte streams with length-prefixed MessagePack frames
        stdoutReader = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "python-stdout-reader");
            t.setDaemon(true);
            return t;
        });
        stdoutReader.submit(this::readBinaryStdoutLoop);

        stderrReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        stderrLogger = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "python-stderr-logger");
            t.setDaemon(true);
            return t;
        });
        stderrLogger.submit(this::readStderrLoop);

        logger.info("Python process started (PID: " + process.pid() + ")");
    }

    /**
     * Returns the PythonProtocol for sending eval/exec requests.
     */
    PythonProtocol protocol() {
        return protocol;
    }

    /**
     * Returns a Writer that sends bytes to the Python process stdin.
     */
    PythonProtocol.Writer stdinWriter() {
        return data -> {
            synchronized (this) {
                if (!running) {
                    throw new IOException("Python process is not running");
                }
                stdinStream.write(data);
                stdinStream.flush();
            }
        };
    }

    /**
     * Checks if the process is still alive.
     */
    boolean isRunning() {
        return running && process != null && process.isAlive();
    }

    /**
     * Returns the OS-level PID of the Python process, or -1 if
     * the process has not been started or the PID is unavailable.
     */
    long getPid() {
        if (process == null) return -1;
        try {
            return process.pid();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Gracefully shuts down the Python process using default timeouts
     * (5 seconds graceful wait, 2 seconds force-wait).
     */
    void close() {
        close(5_000, 2_000);
    }

    /**
     * Force-terminates the Python process immediately, without
     * attempting a graceful exit. Intended for JVM shutdown hooks
     * where in-flight I/O may already be broken.
     */
    void hardShutdown() {
        running = false;
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        protocol.cancelAll(new RuntimeException("Python process terminated (JVM shutdown)"));
    }

    /**
     * Gracefully shuts down the Python process with configurable timeouts.
     *
     * @param waitMs maximum time to wait for graceful exit in milliseconds
     * @param forceWaitMs maximum time to wait after force-destroy in milliseconds
     */
    void close(long waitMs, long forceWaitMs) {
        // Synchronized to match the block in stdinWriter(), ensuring
        // that any thread currently writing to stdin will either see
        // running==false or complete its write before we proceed to
        // shut down the process.
        synchronized (this) {
            running = false;
        }

        // Try graceful exit first
        if (process != null && process.isAlive()) {
            try {
                protocol.sendExit(this::writeBytesDirect);
            } catch (Exception ignored) {
            }
        }

        shutdownExecutor(stdoutReader);
        shutdownExecutor(stderrLogger);

        if (process != null) {
            try {
                process.waitFor(waitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(forceWaitMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        try {
            if (stdinStream != null) stdinStream.close();
            if (stdoutStream != null) stdoutStream.close();
            if (stderrReader != null) stderrReader.close();
        } catch (IOException ignored) {
        }

        // Cancel any remaining pending futures. This is done after
        // process.waitFor() and stream cleanup to ensure all
        // outstanding I/O operations have been resolved first.
        protocol.cancelAll(new RuntimeException("Python process closed"));
    }

    // ---- internal ----

    /** Read length-prefixed MessagePack frames from stdout. */
    private void readBinaryStdoutLoop() {
        try {
            while (running) {
                // Read 4-byte big-endian length
                byte[] lenBuf = new byte[4];
                int read = stdoutStream.readNBytes(lenBuf, 0, 4);
                if (read < 4) {
                    break; // EOF
                }
                int length = ((lenBuf[0] & 0xFF) << 24)
                        | ((lenBuf[1] & 0xFF) << 16)
                        | ((lenBuf[2] & 0xFF) << 8)
                        | (lenBuf[3] & 0xFF);

                if (length <= 0) {
                    logger.warning("Received zero-length frame, skipping");
                    continue;
                }

                byte[] data = new byte[length];
                read = stdoutStream.readNBytes(data, 0, length);
                if (read < length) {
                    logger.warning("Incomplete frame: expected " + length + " bytes, got " + read);
                    break;
                }

                protocol.handleResponse(data);
            }
        } catch (IOException e) {
            if (running) {
                logger.log(Level.WARNING, "stdout read error", e);
                protocol.cancelAll(e);
            }
        }
    }

    private void readStderrLoop() {
        try {
            String line;
            while (running && (line = stderrReader.readLine()) != null) {
                logger.warning("[python-stderr] " + line);
            }
        } catch (IOException e) {
            if (running) {
                logger.log(Level.WARNING, "stderr read error", e);
            }
        }
    }

    private void writeBytesDirect(byte[] data) {
        try {
            stdinStream.write(data);
            stdinStream.flush();
        } catch (IOException e) {
            logger.log(Level.FINE, "write to stdin failed (process may have exited)", e);
        }
    }

    private void shutdownExecutor(ExecutorService executor) {
        if (executor == null) return;
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    /**
     * Finds the site-packages directory inside a venv.
     *
     * <p>On Unix: {@code lib/python3.X/site-packages}
     * On Windows: {@code Lib/site-packages}
     *
     * @param venvPath root directory of the venv
     * @return path to site-packages, or null if not found
     */
    static Path findVenvSitePackages(Path venvPath) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            Path sitePkgs = venvPath.resolve("Lib").resolve("site-packages");
            if (Files.isDirectory(sitePkgs)) {
                return sitePkgs;
            }
            return null;
        } else {
            Path libDir = venvPath.resolve("lib");
            if (!Files.isDirectory(libDir)) {
                return null;
            }
            try (Stream<Path> entries = Files.list(libDir)) {
                return entries
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("python"))
                        .map(p -> p.resolve("site-packages"))
                        .filter(Files::isDirectory)
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to scan venv lib directory", e);
                return null;
            }
        }
    }

    /**
     * Resolves the Python executable within a directory.
     *
     * <p>Supports two layouts:
     * <ul>
     *   <li><b>venv layout</b> (system Python):
     *       Windows: {@code Scripts/python.exe}, Unix: {@code bin/python3}</li>
     *   <li><b>python-build-standalone layout</b> (bundled, flattened):
     *       Windows: {@code python.exe} at root, Unix: {@code bin/python3}</li>
     * </ul>
     */
    static String resolvePythonExecutable(Path basePath) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            Path venvPy = basePath.resolve("Scripts").resolve("python.exe");
            if (venvPy.toFile().exists()) {
                return venvPy.toAbsolutePath().toString();
            }
            Path standalonePy = basePath.resolve("python.exe");
            if (standalonePy.toFile().exists()) {
                return standalonePy.toAbsolutePath().toString();
            }
            return venvPy.toAbsolutePath().toString();
        } else {
            Path python3 = basePath.resolve("bin").resolve("python3");
            if (python3.toFile().exists()) {
                return python3.toAbsolutePath().toString();
            }
            Path python = basePath.resolve("bin").resolve("python");
            if (python.toFile().exists()) {
                return python.toAbsolutePath().toString();
            }
            return python3.toAbsolutePath().toString();
        }
    }
}
