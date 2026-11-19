package io.github.howtis.pythonembed.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Detects and resolves Python executables from the system PATH
 * or from downloaded python-build-standalone distributions.
 */
public final class PythonResolver {

    private PythonResolver() {
    }

    /**
     * Finds a usable system Python command.
     *
     * @param logger consumer for log messages
     * @return the Python command ("python3" or "python"), or null if not found
     */
    public static String findSystemPython(Consumer<String> logger) {
        for (String cmd : new String[]{"python3", "python"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int exitCode = p.waitFor();
                if (exitCode == 0) {
                    logger.accept("System Python found: " + cmd);
                    return cmd;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Reports the version of a Python executable as {@code major.minor.patch}.
     *
     * @param command a command name such as {@code python3}, or a path to an executable
     * @return the version, or {@code null} when the interpreter cannot be queried
     */
    public static String queryVersion(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "-c",
                    "import sys; print('%d.%d.%d' % sys.version_info[:3])");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = p.waitFor();
            if (exitCode == 0 && !output.isEmpty()) {
                return output;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Reports the version of a Python executable.
     *
     * @param executable path to a Python executable
     * @return the version, or {@code null} when the interpreter cannot be queried
     */
    public static String queryVersion(Path executable) {
        return queryVersion(executable.toString());
    }

    /**
     * Tests whether an interpreter version satisfies a requested version.
     *
     * <p>The request is a prefix: {@code 3.12} accepts {@code 3.12.16} but not
     * {@code 3.13.0}, and {@code 3} accepts any {@code 3.x.y}.
     *
     * @param actualVersion version reported by an interpreter, e.g. {@code 3.12.16}
     * @param requestedVersion configured version, e.g. {@code 3.12}
     * @return true when the actual version satisfies the request
     */
    public static boolean satisfiesVersion(String actualVersion, String requestedVersion) {
        if (actualVersion == null || requestedVersion == null || requestedVersion.isEmpty()) {
            return false;
        }
        return actualVersion.equals(requestedVersion) || actualVersion.startsWith(requestedVersion + ".");
    }

    /**
     * Finds a Python executable within a directory tree.
     * On Windows, searches for python.exe; on others, searches for bin/python3.
     *
     * @param dir root directory to search
     * @return path to Python executable, or null if not found
     */
    public static Path findPythonInDir(Path dir) {
        if (!Files.exists(dir)) return null;
        String targetOs = detectTargetOs();
        try {
            Path result = findPythonInDir(dir, targetOs);
            return result;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Finds a Python executable within a directory tree for the specified OS.
     *
     * @param dir root directory to search
     * @param targetOs "windows", "linux", or "macos"
     * @return path to Python executable, or null if not found
     * @throws IOException if directory traversal fails
     */
    public static Path findPythonInDir(Path dir, String targetOs) throws IOException {
        if (!Files.exists(dir)) return null;

        if ("windows".equals(targetOs)) {
            Path exe = dir.resolve("python.exe");
            if (Files.exists(exe)) return exe;
            exe = dir.resolve("Scripts").resolve("python.exe");
            if (Files.exists(exe)) return exe;
        } else {
            Path bin = dir.resolve("bin").resolve("python3");
            if (Files.exists(bin)) return bin;
            bin = dir.resolve("bin").resolve("python");
            if (Files.exists(bin)) return bin;
        }
        return null;
    }

    /**
     * Resolves the Python executable path from a venv directory.
     *
     * @param venvDir path to the venv directory
     * @return path to the python executable in the venv
     */
    public static Path resolveVenvPython(Path venvDir) {
        if (isWindows()) {
            return venvDir.resolve("Scripts/python.exe");
        } else {
            return venvDir.resolve("bin/python3");
        }
    }

    /**
     * Detects the current OS target triple for python-build-standalone downloads.
     *
     * @return "x86_64-pc-windows-msvc", "x86_64-unknown-linux-gnu",
     *         "x86_64-apple-darwin", or "aarch64-apple-darwin"
     */
    public static String detectTargetTriple() {
        String osArch = System.getProperty("os.arch", "").toLowerCase();
        return detectTargetTriple(detectTargetOs(), osArch);
    }

    /**
     * Detects the target triple for python-build-standalone downloads.
     *
     * @param targetOs "windows", "linux", or "macos"
     * @param arch     "x86_64", "amd64", "aarch64", "arm64", etc.
     * @return target triple string for python-build-standalone
     */
    public static String detectTargetTriple(String targetOs, String arch) {
        return switch (targetOs) {
            case "windows" -> "x86_64-pc-windows-msvc";
            case "macos" -> {
                if (arch.contains("aarch64") || arch.contains("arm")) {
                    yield "aarch64-apple-darwin";
                }
                yield "x86_64-apple-darwin";
            }
            default -> {
                if (arch.contains("aarch64") || arch.contains("arm64")) {
                    yield "aarch64-unknown-linux-gnu";
                }
                yield "x86_64-unknown-linux-gnu";
            }
        };
    }

    /**
     * Resolves the target OS string from a configuration value.
     *
     * @param targetOs configured value ("windows", "linux", "macos"), or null for auto-detect
     * @return normalized OS string
     */
    public static String resolveTargetOs(String targetOs) {
        if (targetOs != null && !targetOs.isEmpty()) {
            return targetOs.toLowerCase();
        }
        if (isWindows()) return "windows";
        if (isMac()) return "macos";
        return "linux";
    }

    /**
     * @return true if the current JVM is running on Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * @return true if the current JVM is running on macOS
     */
    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    static String detectTargetOs() {
        if (isWindows()) return "windows";
        if (isMac()) return "macos";
        return "linux";
    }
}
