package io.github.howtis.pythonembed.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Map;

import groovy.json.JsonSlurper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Gradle task that creates a Python environment and installs packages into it.
 *
 * <p>If system Python is available, creates a standard venv.
 * If not, downloads a portable CPython from python-build-standalone
 * and installs packages directly into it, eliminating the system
 * Python installation requirement.
 */
public abstract class VenvTask extends DefaultTask {

    private static final Logger logger = Logger.getLogger(VenvTask.class.getName());
    private static final String FINGERPRINT_FILE = "python-embed.fingerprint";
    private static final String KEY_PYTHON_SOURCE = "python.source";
    private static final String KEY_PYTHON_VERSION = "python.version";
    private static final String KEY_PACKAGES_HASH = "packages.hash";
    private static final String USER_AGENT = "python-embed-gradle-plugin";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MS = 2000;

    private static class Fingerprint {
        String pythonSource;
        String pythonVersion;
        String packageHash;
    }

    /**
     * Path to the output directory that will be packaged as a resource.
     *
     * @return the venv directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getVenvDir();

    /**
     * List of pip packages to install.
     *
     * @return list of pip packages
     */
    @Input
    public abstract ListProperty<String> getPackages();

    /**
     * Optional path to requirements.txt (relative to project root).
     *
     * @return optional path to requirements.txt
     */
    @Optional
    @Input
    public abstract Property<String> getRequirementsFile();

    /**
     * Optional path to pyproject.toml (relative to project root).
     *
     * @return optional path to pyproject.toml
     */
    @Optional
    @Input
    public abstract Property<String> getPyprojectTomlFile();

    /**
     * Python version to use for auto-download (default: "3.12").
     *
     * @return Python version property
     */
    @Input
    public abstract Property<String> getPythonVersion();

    /**
     * Optional pip index URL (e.g., PyTorch CUDA index).
     *
     * @return optional pip index URL
     */
    @Optional
    @Input
    public abstract Property<String> getPipIndexUrl();

    /**
     * Optional extra pip install arguments (e.g., --extra-index-url, -f).
     *
     * @return optional extra pip install arguments
     */
    @Input
    public abstract ListProperty<String> getPipExtraArgs();

    /**
     * Target OS for cross-compilation: "windows", "linux", "macos" (null = auto-detect).
     *
     * @return target OS property
     */
    @Optional
    @Input
    public abstract Property<String> getTargetOs();

    /** Creates or updates the Python virtual environment. */
    @TaskAction
    public void createVenv() {
        List<String> packages = new ArrayList<>(getPackages().get());
        // msgpack is always required by bridge.py, auto-include
        if (!packages.contains("msgpack")) {
            packages.add("msgpack");
        }
        String requirementsFile = getRequirementsFile().getOrNull();
        String pyprojectTomlFile = getPyprojectTomlFile().getOrNull();

        Path projectRoot = getProject().getRootDir().toPath();

        // Merge packages from requirements.txt
        if (requirementsFile != null && !requirementsFile.isEmpty()) {
            Path requirementsPath = projectRoot.resolve(requirementsFile);
            if (!Files.exists(requirementsPath)) {
                throw new GradleException(
                        "requirements.txt not found: " + requirementsPath.toAbsolutePath());
            }
            try {
                List<String> reqPackages = RequirementsParser.parse(requirementsPath);
                packages.addAll(reqPackages);
                logger.info("Loaded " + reqPackages.size() + " packages from " + requirementsFile);
            } catch (IOException e) {
                throw new GradleException("Failed to read requirements.txt: " + requirementsPath, e);
            }
        }

        // Resolve pyproject.toml path for fingerprint and pip (pip understands it natively)
        Path pyprojectPath = null;
        if (pyprojectTomlFile != null && !pyprojectTomlFile.isEmpty()) {
            pyprojectPath = projectRoot.resolve(pyprojectTomlFile);
            if (!Files.exists(pyprojectPath)) {
                throw new GradleException(
                        "pyproject.toml not found: " + pyprojectPath.toAbsolutePath());
            }
        }

        String pipIndexUrl = getPipIndexUrl().getOrNull();
        List<String> pipExtraArgs = getPipExtraArgs().get();
        String pythonVersion = getPythonVersion().get();

        File venvDir = getVenvDir().get().getAsFile();
        logger.info("Output directory: " + venvDir.getAbsolutePath());

        // Compute current package fingerprint (includes raw pyproject.toml content)
        String currentPackageHash = computePackageHash(packages, pipIndexUrl, pipExtraArgs, pyprojectPath);

        // Check stored fingerprint for incremental rebuild
        Fingerprint stored = readFingerprint(venvDir);
        boolean pythonPresent = findPythonInDir(venvDir.toPath()) != null;
        boolean pythonVersionMatch = stored != null && pythonVersion.equals(stored.pythonVersion);
        boolean packagesMatch = stored != null && currentPackageHash.equals(stored.packageHash);
        boolean sourceKnown = stored != null && stored.pythonSource != null;

        if (pythonPresent && pythonVersionMatch && packagesMatch && sourceKnown) {
            logger.info("Python environment is up to date, skipping setup");
            return;
        }

        // Set up Python runtime
        String pythonCmd;
        String pythonSource;
        boolean needFullSetup = !pythonPresent || !pythonVersionMatch || !sourceKnown;

        if (needFullSetup) {
            if (venvDir.exists()) {
                try {
                    deleteRecursively(venvDir.toPath());
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to clean output directory", e);
                }
            }
            String originalPython = findPython();
            boolean isBundled = isBundledPython(originalPython);
            pythonSource = isBundled ? "bundled" : "system";

            if (isBundled) {
                pythonCmd = originalPython;
            } else {
                logger.info("Creating venv...");
                runCommand(originalPython, "-m", "venv", venvDir.getAbsolutePath());
                pythonCmd = resolveVenvPython(venvDir);
            }
        } else {
            pythonCmd = resolvePythonInDir(venvDir);
            pythonSource = stored.pythonSource;
        }

        logger.info("Using Python: " + pythonCmd);

        // Install packages if needed (pip handles pyproject.toml natively)
        if (!packages.isEmpty() || pyprojectPath != null) {
            if (packagesMatch) {
                logger.info("Packages up to date, skipping pip install");
            } else {
                pipInstall(pythonCmd, packages, pipIndexUrl, pipExtraArgs, pyprojectPath);
            }
        }

        // Write fingerprint for next build
        writeFingerprint(venvDir, pythonSource, pythonVersion, currentPackageHash);

        logger.info("Python environment ready at: " + venvDir.getAbsolutePath());
    }

    // ---- internal ----

    /**
     * Finds a usable Python command: system Python first, then bundled.
     * @return the Python command path, or a special "bundled:" prefix for auto-downloaded Python
     */
    private String findPython() {
        // Try system Python first
        for (String cmd : new String[]{"python3", "python"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int exitCode = p.waitFor();
                if (exitCode == 0) {
                    logger.info("System Python found: " + cmd);
                    return cmd;
                }
            } catch (Exception ignored) {
            }
        }

        // Not found - download python-build-standalone
        logger.info("System Python not found. Downloading python-build-standalone...");
        try {
            return downloadPythonStandalone();
        } catch (IOException e) {
            throw new GradleException(
                    "Python not found and auto-download failed. "
                    + "Install Python 3 or check network connectivity.\n"
                    + "Error: " + e.getMessage(), e);
        }
    }

    private boolean isBundledPython(String pythonCmd) {
        // Bundled python paths are absolute (downloaded to cache)
        return pythonCmd.contains(File.separator) || pythonCmd.startsWith("bundled:");
    }

    private String resolvePythonInDir(File dir) {
        Path pythonPath = findPythonInDir(dir.toPath());
        if (pythonPath == null) {
            throw new GradleException("Python executable not found in: " + dir.getAbsolutePath());
        }
        return pythonPath.toString();
    }

    private void pipInstall(String pythonCmd, List<String> packages,
                            String pipIndexUrl, List<String> pipExtraArgs,
                            Path pyprojectPath) {
        if (packages.isEmpty() && pyprojectPath == null) {
            return;
        }
        logger.info("Upgrading pip...");
        runCommand(pythonCmd, "-m", "pip", "install", "--upgrade", "pip");
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
        logger.info("Installing: " + args.subList(3, args.size()));
        runCommand(args.toArray(new String[0]));
    }

    String computePackageHash(List<String> packages, String pipIndexUrl,
                                       List<String> pipExtraArgs, Path pyprojectPath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<String> sortedPackages = new ArrayList<>(packages);
            sortedPackages.sort(null);
            for (String pkg : sortedPackages) {
                md.update(pkg.getBytes(StandardCharsets.UTF_8));
            }
            if (pipIndexUrl != null && !pipIndexUrl.isEmpty()) {
                md.update(pipIndexUrl.getBytes(StandardCharsets.UTF_8));
            }
            List<String> sortedExtraArgs = new ArrayList<>(pipExtraArgs);
            sortedExtraArgs.sort(null);
            for (String arg : sortedExtraArgs) {
                md.update(arg.getBytes(StandardCharsets.UTF_8));
            }
            if (pyprojectPath != null) {
                md.update(Files.readAllBytes(pyprojectPath));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new GradleException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new GradleException("Failed to read pyproject.toml for fingerprint: " + pyprojectPath, e);
        }
    }

    private Fingerprint readFingerprint(File venvDir) {
        Path fp = venvDir.toPath().resolve(FINGERPRINT_FILE);
        if (!Files.exists(fp)) {
            return null;
        }
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(fp, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            logger.warning("Failed to read fingerprint: " + e.getMessage());
            return null;
        }
        Fingerprint f = new Fingerprint();
        f.pythonSource = props.getProperty(KEY_PYTHON_SOURCE);
        f.pythonVersion = props.getProperty(KEY_PYTHON_VERSION);
        f.packageHash = props.getProperty(KEY_PACKAGES_HASH);
        if (f.pythonVersion == null || f.packageHash == null) {
            return null;
        }
        return f;
    }

    private void writeFingerprint(File venvDir, String pythonSource, String pythonVersion,
                                   String packageHash) {
        Properties props = new Properties();
        props.setProperty(KEY_PYTHON_SOURCE, pythonSource);
        props.setProperty(KEY_PYTHON_VERSION, pythonVersion);
        props.setProperty(KEY_PACKAGES_HASH, packageHash);
        Path fp = venvDir.toPath().resolve(FINGERPRINT_FILE);
        try {
            Files.createDirectories(fp.getParent());
            try (var writer = Files.newBufferedWriter(fp, StandardCharsets.UTF_8)) {
                props.store(writer, "python-embed incremental build fingerprint");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write fingerprint: " + fp, e);
        }
    }

    /**
     * Downloads python-build-standalone, extracts it, and returns the path
     * to the python executable.
     */
    private String downloadPythonStandalone() throws IOException {
        String version = getPythonVersion().get();
        String targetTriple = detectTargetTriple();
        String releaseTag = fetchLatestReleaseTag();

        // Cache directory
        Path cacheDir = Path.of(System.getProperty("user.home"),
                ".gradle", "python-embed");
        Files.createDirectories(cacheDir);

        // Find exact asset name from release
        String exactAsset = findMatchingAsset(releaseTag, version, targetTriple);
        Path archiveFile = cacheDir.resolve(exactAsset);

        // Download if not cached
        if (!Files.exists(archiveFile)) {
            String downloadUrl = "https://github.com/indygreg/python-build-standalone"
                    + "/releases/download/" + releaseTag + "/" + exactAsset;
            logger.info("Downloading: " + downloadUrl);
            downloadFile(downloadUrl, archiveFile);
            logger.info("Downloaded to: " + archiveFile);

            // Verify SHA256 checksum
            Path shaFile = cacheDir.resolve(exactAsset + ".sha256");
            downloadFile(downloadUrl + ".sha256", shaFile);
            String expectedHash = Files.readString(shaFile).trim().split("\\s+")[0];
            String actualHash = computeFileSha256(archiveFile);
            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                Files.deleteIfExists(archiveFile);
                Files.deleteIfExists(shaFile);
                throw new IOException("SHA256 mismatch for " + exactAsset
                        + ": expected " + expectedHash + ", got " + actualHash);
            }
            logger.fine("SHA256 checksum verified: " + actualHash);
        } else {
            logger.info("Using cached: " + archiveFile);
        }

        // Extract to temp first, then flatten python/install/ to output
        File outputDir = getVenvDir().get().getAsFile();
        if (outputDir.exists()) {
            deleteRecursively(outputDir.toPath());
        }

        Path tmpExtract = Files.createTempDirectory("python-standalone-extract-");
        try {
            logger.info("Extracting to temporary: " + tmpExtract);
            extractTarGz(archiveFile, tmpExtract);

            // Find python/install/ inside the extracted tree
            Path installDir = tmpExtract.resolve("python").resolve("install");
            if (!Files.exists(installDir)) {
                installDir = tmpExtract.resolve("python");
            }
            if (!Files.exists(installDir)) {
                installDir = tmpExtract;
            }

            // Copy installDir contents to outputDir (flatten one level)
            logger.info("Flattening to: " + outputDir);
            copyDirectory(installDir, outputDir.toPath());

            // Make executables runnable on Unix (respect target OS for cross-compilation)
            if (!isTargetWindows()) {
                makeExecutables(outputDir.toPath());
            }
        } finally {
            deleteRecursively(tmpExtract);
        }

        Path pythonExe = findPythonInDir(outputDir.toPath());
        if (pythonExe == null) {
            throw new GradleException("Python executable not found in extracted archive");
        }
        return pythonExe.toString();
    }

    Path findPythonInDir(Path dir) {
        // After flattening, the layout depends on the target OS:
        //   Windows: python.exe at root (bundled) or Scripts/python.exe (venv)
        //   Unix:    bin/python3 or bin/python
        if (isTargetWindows()) {
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

    String detectTargetTriple() {
        String targetOs = resolveTargetOs();
        String arch = System.getProperty("os.arch", "").toLowerCase();
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

    private HttpURLConnection createGitHubConnection(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);

        String githubToken = System.getenv("GITHUB_TOKEN");
        if (githubToken == null) {
            githubToken = System.getenv("PYTHON_EMBED_GITHUB_TOKEN");
        }
        if (githubToken != null && !githubToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + githubToken);
        }

        return conn;
    }

    private String computeFileSha256(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
        byte[] buffer = new byte[8192];
        try (InputStream is = Files.newInputStream(file)) {
            int n;
            while ((n = is.read(buffer)) > 0) {
                md.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private String fetchLatestReleaseTag() throws IOException {
        URI apiUri = URI.create(
                "https://api.github.com/repos/indygreg/python-build-standalone/releases/latest");
        HttpURLConnection conn = createGitHubConnection(apiUri);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("GitHub API returned HTTP " + status
                    + " for releases/latest");
        }

        try (InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Object parsed = new JsonSlurper().parseText(json);
            if (parsed instanceof Map) {
                Map<?, ?> release = (Map<?, ?>) parsed;
                Object tagName = release.get("tag_name");
                if (tagName != null) {
                    return tagName.toString();
                }
            }
            throw new IOException("Cannot find tag_name in release JSON");
        }
    }

    private String findMatchingAsset(String releaseTag, String pythonVersion,
                                      String targetTriple) throws IOException {
        URI apiUri = URI.create(
                "https://api.github.com/repos/indygreg/python-build-standalone"
                + "/releases/tags/" + releaseTag);
        HttpURLConnection conn = createGitHubConnection(apiUri);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("GitHub API returned HTTP " + status
                    + " for releases/tags/" + releaseTag);
        }

        try (InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Object parsed = new JsonSlurper().parseText(json);
            if (parsed instanceof Map) {
                Map<?, ?> release = (Map<?, ?>) parsed;
                Object assets = release.get("assets");
                if (assets instanceof List) {
                    for (Object asset : (List<?>) assets) {
                        if (asset instanceof Map) {
                            Map<?, ?> assetMap = (Map<?, ?>) asset;
                            String name = assetMap.get("name").toString();
                            if (name.startsWith("cpython-" + pythonVersion + ".")
                                    && name.contains(targetTriple)
                                    && name.endsWith("-install_only.tar.gz")) {
                                return name;
                            }
                        }
                    }
                }
            }
        }
        throw new IOException("No matching python-build-standalone asset found for "
                + "Python " + pythonVersion + " on " + targetTriple);
    }

    private void downloadFile(String url, Path target) throws IOException {
        IOException lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                    logger.info("Retry " + attempt + "/" + (MAX_RETRY_ATTEMPTS - 1)
                            + " in " + (delay / 1000) + "s...");
                    Thread.sleep(delay);
                }

                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL()
                        .openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(300_000);
                conn.setInstanceFollowRedirects(true);

                int status = conn.getResponseCode();
                if (status != 200) {
                    throw new IOException("HTTP " + status + " for " + url);
                }

                try (InputStream is = conn.getInputStream()) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (SocketTimeoutException e) {
                lastException = new IOException("Download timed out: " + url, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastException = new IOException("Download interrupted: " + url, e);
            } catch (IOException e) {
                lastException = e;
            }
        }
        throw new IOException("Download failed after " + MAX_RETRY_ATTEMPTS
                + " attempts: " + url, lastException);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /** Returns the effective target OS: user-configured value or auto-detected build OS. */
    private String resolveTargetOs() {
        String target = getTargetOs().getOrNull();
        if (target != null && !target.isEmpty()) {
            return target.toLowerCase();
        }
        if (isWindows()) return "windows";
        if (isMac()) return "macos";
        return "linux";
    }

    /** True when the effective target OS is Windows. */
    private boolean isTargetWindows() {
        return "windows".equals(resolveTargetOs());
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dst = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void makeExecutables(Path dir) throws IOException {
        // Make python and pip executables in bin/ runnable
        Path binDir = dir.resolve("bin");
        if (Files.exists(binDir)) {
            Files.list(binDir).forEach(f -> {
                if (f.getFileName().toString().startsWith("python")
                        || f.getFileName().toString().startsWith("pip")) {
                    f.toFile().setExecutable(true);
                }
            });
        }
        // Also check root level python on Windows
        Path pythonExe = dir.resolve("python.exe");
        if (Files.exists(pythonExe)) {
            pythonExe.toFile().setExecutable(true);
        }
    }

    // ---- tar.gz extraction (no external dependencies) ----

    void extractTarGz(Path tarGzFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (InputStream fis = Files.newInputStream(tarGzFile);
             GZIPInputStream gzis = new GZIPInputStream(fis)) {

            byte[] buffer = new byte[8192];
            byte[] header = new byte[512];

            while (true) {
                // Read header block
                int headerBytes = readFully(gzis, header);
                if (headerBytes < 512) break;
                if (isAllZeros(header)) break; // end of archive

                String name = new String(header, 0, 100, StandardCharsets.UTF_8)
                        .replaceAll("\0.*", "").trim();
                if (name.isEmpty()) break;

                long size = parseOctal(new String(header, 124, 12, StandardCharsets.UTF_8));
                int typeFlag = header[156] & 0xFF;

                // ZipSlip protection: normalize and validate path is within targetDir
                // Strip leading separator and convert backslashes to forward slashes
                // for cross-platform compatibility (tar always uses '/' as separator)
                String safeName = name.replace('\\', '/');
                while (safeName.startsWith("/")) {
                    safeName = safeName.substring(1);
                }
                Path entryTarget = targetDir.resolve(safeName).normalize();
                if (!entryTarget.startsWith(targetDir.normalize())) {
                    throw new IOException(
                            "Tar entry path traversal detected: '" + name
                            + "' would extract to '" + entryTarget + "' outside target '" + targetDir + "'");
                }

                if (typeFlag == '5') {
                    // Directory
                    Files.createDirectories(entryTarget);
                } else if (typeFlag == 0 || typeFlag == '0') {
                    // Regular file
                    Path parent = entryTarget.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (var fos = Files.newOutputStream(entryTarget)) {
                        long remaining = size;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(remaining, buffer.length);
                            int n = gzis.read(buffer, 0, toRead);
                            if (n < 0) throw new IOException("Unexpected EOF in tar entry");
                            fos.write(buffer, 0, n);
                            remaining -= n;
                        }
                    }
                }
                // Skip padding after data
                long padding = (512 - (size % 512)) % 512;
                if (padding > 0) {
                    gzis.skip(padding);
                }
            }
        }
    }

    private int readFully(InputStream is, byte[] buf) throws IOException {
        int offset = 0;
        int remaining = buf.length;
        while (remaining > 0) {
            int n = is.read(buf, offset, remaining);
            if (n < 0) break;
            offset += n;
            remaining -= n;
        }
        return offset;
    }

    private boolean isAllZeros(byte[] buf) {
        for (byte b : buf) {
            if (b != 0) return false;
        }
        return true;
    }

    private long parseOctal(String s) {
        long result = 0;
        for (char c : s.trim().toCharArray()) {
            if (c >= '0' && c <= '7') {
                result = result * 8 + (c - '0');
            } else {
                break;
            }
        }
        return result;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        logger.warning("Failed to delete: " + p);
                    }
                });
    }

    private String resolveVenvPython(File venvDir) {
        if (isWindows()) {
            return new File(venvDir, "Scripts/python.exe").getAbsolutePath();
        } else {
            return new File(venvDir, "bin/python3").getAbsolutePath();
        }
    }

    private void runCommand(String... command) {
        try {
            logger.info("Running: " + String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.fine(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String cmd = String.join(" ", command);
                String errMsg = "Command failed (exit " + exitCode + "): " + cmd + "\n" + output;
                throw new GradleException(errMsg);
            }
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("Failed to run: " + String.join(" ", command), e);
        }
    }
}
