package io.github.howtis.pythonembed.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Main entry point for Python environment setup.
 * Detects system Python or downloads python-build-standalone,
 * creates a venv, installs packages, and manages fingerprints
 * for incremental builds.
 */
public final class VenvManager {

    private VenvManager() {
    }

    /**
     * Sets up a Python environment according to the given configuration.
     *
     * @param config the environment configuration
     * @return information about the resulting Python environment
     * @throws IOException if setup fails
     */
    public static PythonEnvironment setup(VenvConfig config) throws IOException {
        Consumer<String> log = config.logger();
        Path venvDir = config.venvDir();
        String resolvedTargetOs = PythonResolver.resolveTargetOs(config.targetOs());

        // Collect packages
        List<String> packages = new ArrayList<>(config.packages());

        // Include msgpack by default (required by python-embed-runtime bridge protocol).
        // Users who do not need the runtime can opt out via VenvConfig.includeMsgpack(false).
        if (config.includeMsgpack() && !packages.contains("msgpack")) {
            packages.add("msgpack");
        }

        Path pyprojectPath = null;

        // Merge packages from requirements.txt
        if (config.requirementsFile() != null) {
            Path reqPath = config.requirementsFile();
            if (!Files.exists(reqPath)) {
                throw new IOException("requirements.txt not found: " + reqPath.toAbsolutePath());
            }
            List<String> reqPackages = RequirementsParser.parse(reqPath, log);
            packages.addAll(reqPackages);
            log.accept("Loaded " + reqPackages.size() + " packages from " + reqPath);
        }

        // Resolve pyproject.toml
        if (config.pyprojectTomlFile() != null) {
            pyprojectPath = config.pyprojectTomlFile();
            if (!Files.exists(pyprojectPath)) {
                throw new IOException("pyproject.toml not found: " + pyprojectPath.toAbsolutePath());
            }
        }

        String pythonVersion = config.pythonVersion();
        log.accept("Output directory: " + venvDir);

        // Compute fingerprint
        String currentPackageHash = FingerprintManager.computePackageHash(
                packages, config.pipIndexUrl(), config.pipExtraArgs(), pyprojectPath);

        // Check stored fingerprint
        FingerprintManager.Fingerprint stored = FingerprintManager.read(venvDir);
        boolean pythonPresent = PythonResolver.findPythonInDir(venvDir, resolvedTargetOs) != null;
        boolean pythonVersionMatch = stored != null && pythonVersion.equals(stored.pythonVersion);
        boolean packagesMatch = stored != null && currentPackageHash.equals(stored.packageHash);
        boolean sourceKnown = stored != null && stored.pythonSource != null;

        if (pythonPresent && pythonVersionMatch && packagesMatch && sourceKnown) {
            log.accept("Python environment is up to date, skipping setup");
            Path pythonExe = PythonResolver.findPythonInDir(venvDir, resolvedTargetOs);
            return new PythonEnvironment(pythonExe, venvDir, stored.pythonSource);
        }

        // Set up Python
        String pythonSource;
        Path pythonExe;
        boolean needFullSetup = !pythonPresent || !pythonVersionMatch || !sourceKnown;

        if (needFullSetup) {
            if (Files.exists(venvDir)) {
                deleteRecursively(venvDir);
            }
            Files.createDirectories(venvDir);

            String systemPython = PythonResolver.findSystemPython(log);
            if (systemPython != null && !systemPython.contains(java.io.File.separator)) {
                // System Python found
                pythonSource = "system";
                log.accept("Creating venv...");
                runCommand(log, systemPython, "-m", "venv", venvDir.toString());
                pythonExe = PythonResolver.resolveVenvPython(venvDir);
            } else {
                // Download python-build-standalone
                pythonSource = "bundled";
                Path cacheDir = Path.of(System.getProperty("user.home"),
                        ".python-embed");
                log.accept("System Python not found. Downloading python-build-standalone...");
                pythonExe = PythonDownloader.download(pythonVersion, resolvedTargetOs, cacheDir, venvDir, log);
            }
        } else {
            pythonExe = PythonResolver.findPythonInDir(venvDir, resolvedTargetOs);
            if (pythonExe == null) {
                throw new IOException("Python executable not found in: " + venvDir);
            }
            pythonSource = stored.pythonSource;
        }

        log.accept("Using Python: " + pythonExe);

        // Install packages
        if (!packages.isEmpty() || pyprojectPath != null) {
            if (packagesMatch) {
                log.accept("Packages up to date, skipping pip install");
            } else {
                pipInstall(log, pythonExe.toString(), packages, config.pipIndexUrl(),
                        config.pipExtraArgs(), pyprojectPath);
            }
        }

        // Write fingerprint
        FingerprintManager.write(venvDir, pythonSource, pythonVersion, currentPackageHash);

        log.accept("Python environment ready at: " + venvDir);
        return new PythonEnvironment(pythonExe, venvDir, pythonSource);
    }

    private static void pipInstall(Consumer<String> log, String pythonCmd, List<String> packages,
                                   String pipIndexUrl, List<String> pipExtraArgs,
                                   Path pyprojectPath) throws IOException {
        if (packages.isEmpty() && pyprojectPath == null) {
            return;
        }
        log.accept("Upgrading pip...");
        runCommand(log, pythonCmd, "-m", "pip", "install", "--upgrade", "pip");

        List<String> args = new ArrayList<>();
        args.add(pythonCmd);
        args.add("-m");
        args.add("pip");
        args.add("install");
        if (pipIndexUrl != null && !pipIndexUrl.isEmpty()) {
            args.add("--index-url");
            args.add(pipIndexUrl);
        }
        args.addAll(pipExtraArgs);
        args.addAll(packages);
        if (pyprojectPath != null) {
            args.add(pyprojectPath.getParent().toString());
        }
        log.accept("Installing: " + args.subList(3, args.size()));
        runCommand(log, args.toArray(new String[0]));
    }

    static void runCommand(Consumer<String> log, String... command) throws IOException {
        log.accept("Running: " + String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                log.accept(line);
            }
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Command interrupted: " + String.join(" ", command), e);
        }

        if (exitCode != 0) {
            String cmd = String.join(" ", command);
            throw new IOException("Command failed (exit " + exitCode + "): " + cmd + "\n" + output);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // ignore
                }
            });
        }
    }
}
