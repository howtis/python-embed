package io.github.howtis.pythonembed.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
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
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Downloads python-build-standalone releases from GitHub and extracts them
 * into the output directory.
 */
public final class PythonDownloader {

    private static final String USER_AGENT = "python-embed-build-common";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MS = 2000;
    private static final String REPOSITORY = "astral-sh/python-build-standalone";
    private static final String RELEASES_URL =
            "https://api.github.com/repos/" + REPOSITORY + "/releases";
    private static final String DOWNLOAD_BASE =
            "https://github.com/" + REPOSITORY + "/releases/download/";
    private static final String METADATA_CACHE_FILE = "release-metadata.properties";
    private static final String SHA256SUMS_FILE = "SHA256SUMS";

    private PythonDownloader() {
    }

    /**
     * Downloads python-build-standalone and returns the path to the python executable
     * within the output directory.
     *
     * @param version Python version (e.g. "3.12")
     * @param targetOs "windows", "linux", or "macos"
     * @param cacheDir directory for caching downloaded archives
     * @param outputDir directory to extract the Python distribution into
     * @param logger consumer for log messages
     * @return path to the python executable in the output directory
     * @throws IOException if download or extraction fails
     */
    public static Path download(String version, String targetOs, Path cacheDir,
                                 Path outputDir, Consumer<String> logger) throws IOException {
        String targetTriple = detectTargetTriple(targetOs);

        Files.createDirectories(cacheDir);

        ReleaseMetadata metadata = resolveReleaseMetadata(cacheDir, logger);
        String exactAsset = findMatchingAsset(metadata.assets(), version, targetTriple);
        logger.accept("Found matching asset: " + exactAsset);

        Path archiveFile = cacheDir.resolve(exactAsset);
        boolean freshlyDownloaded = false;
        if (!Files.exists(archiveFile)) {
            String downloadUrl = DOWNLOAD_BASE + metadata.tag() + "/" + exactAsset;
            logger.accept("Downloading: " + downloadUrl);
            downloadFile(downloadUrl, archiveFile, logger);
            freshlyDownloaded = true;
            logger.accept("Downloaded to: " + archiveFile);
        } else {
            logger.accept("Using cached: " + archiveFile);
        }

        verifyArchive(archiveFile, exactAsset, metadata.tag(), cacheDir, freshlyDownloaded, logger);

        // Clean output directory
        deleteRecursively(outputDir, logger);
        Files.createDirectories(outputDir);

        // Extract to temp first, then flatten
        Path tmpExtract = Files.createTempDirectory("python-standalone-extract-");
        try {
            logger.accept("Extracting to temporary: " + tmpExtract);
            TarGzExtractor.extract(archiveFile, tmpExtract);

            Path installDir = tmpExtract.resolve("python").resolve("install");
            if (!Files.exists(installDir)) {
                installDir = tmpExtract.resolve("python");
            }
            if (!Files.exists(installDir)) {
                installDir = tmpExtract;
            }

            logger.accept("Flattening to: " + outputDir);
            copyDirectory(installDir, outputDir);

            if (!"windows".equals(targetOs)) {
                makeExecutables(outputDir);
            }
        } finally {
            deleteRecursively(tmpExtract, logger);
        }

        Path pythonExe = PythonResolver.findPythonInDir(outputDir, targetOs);
        if (pythonExe == null) {
            throw new IOException("Python executable not found in extracted archive");
        }
        return pythonExe;
    }

    private static String detectTargetTriple(String targetOs) {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return PythonResolver.detectTargetTriple(targetOs, arch);
    }

    /**
     * Resolves the latest python-build-standalone release, preferring the GitHub API
     * and falling back to the on-disk metadata cache when the API is unavailable —
     * for example when the anonymous limit of 60 requests per hour is exhausted.
     *
     * <p>A single request supplies both the tag and the asset list, so a build costs
     * one API call rather than two.
     */
    static ReleaseMetadata resolveReleaseMetadata(Path cacheDir, Consumer<String> logger) throws IOException {
        Path cacheFile = cacheDir.resolve(METADATA_CACHE_FILE);
        try {
            ReleaseMetadata fetched = fetchLatestRelease(logger);
            writeReleaseMetadataCache(cacheFile, fetched);
            return fetched;
        } catch (IOException e) {
            ReleaseMetadata cached = readReleaseMetadataCache(cacheFile);
            if (cached != null) {
                logger.accept("WARNING: GitHub API unavailable (" + e.getMessage()
                        + "); using cached release metadata for " + cached.tag());
                return cached;
            }
            throw new IOException("Cannot resolve python-build-standalone release: "
                    + e.getMessage() + ". Set GITHUB_TOKEN to raise the GitHub API rate limit, or seed "
                    + cacheFile, e);
        }
    }

    private static ReleaseMetadata fetchLatestRelease(Consumer<String> logger) throws IOException {
        URI apiUri = URI.create(RELEASES_URL + "/latest");
        HttpURLConnection conn = createGitHubConnection(apiUri);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("GitHub API returned HTTP " + status + " for releases/latest");
        }

        try (InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Map<?, ?> release = parseJson(json);
            Object tagName = release.get("tag_name");
            if (tagName == null) {
                throw new IOException("Cannot find tag_name in release JSON");
            }

            List<String> assets = new ArrayList<>();
            Object assetsObj = release.get("assets");
            if (assetsObj instanceof List) {
                for (Object asset : (List<?>) assetsObj) {
                    if (asset instanceof Map) {
                        Object name = ((Map<?, ?>) asset).get("name");
                        if (name != null) {
                            assets.add(name.toString());
                        }
                    }
                }
            }
            if (assets.isEmpty()) {
                throw new IOException("No assets listed for release " + tagName);
            }

            logger.accept("Latest release tag: " + tagName);
            return new ReleaseMetadata(tagName.toString(), List.copyOf(assets));
        }
    }

    static String findMatchingAsset(List<String> assets, String pythonVersion,
                                    String targetTriple) throws IOException {
        for (String name : assets) {
            if (name.startsWith("cpython-" + pythonVersion + ".")
                    && name.contains(targetTriple)
                    && name.endsWith("-install_only.tar.gz")) {
                return name;
            }
        }
        throw new IOException("No matching python-build-standalone asset found for "
                + "Python " + pythonVersion + " on " + targetTriple);
    }

    /**
     * Verifies an archive against the release's {@code SHA256SUMS}.
     *
     * <p>python-build-standalone stopped publishing per-asset {@code .sha256} files at
     * the 2024-03 release; since 2024-10 a single {@code SHA256SUMS} asset carries
     * every digest.
     *
     * <p>A freshly downloaded archive is never used unverified. A cached archive is
     * verified when the checksum list is reachable, and skipped with a warning only
     * when it is not, so offline builds keep working.
     */
    private static void verifyArchive(Path archive, String assetName, String releaseTag,
                                      Path cacheDir, boolean freshlyDownloaded,
                                      Consumer<String> logger) throws IOException {
        Path sumsFile = cacheDir.resolve(SHA256SUMS_FILE + "-" + releaseTag);
        if (!Files.exists(sumsFile)) {
            String sumsUrl = DOWNLOAD_BASE + releaseTag + "/" + SHA256SUMS_FILE;
            try {
                logger.accept("Downloading checksums: " + sumsUrl);
                downloadFile(sumsUrl, sumsFile, logger);
            } catch (IOException e) {
                Files.deleteIfExists(sumsFile);
                if (freshlyDownloaded) {
                    Files.deleteIfExists(archive);
                    throw new IOException("Cannot verify freshly downloaded archive: "
                            + "SHA256SUMS unavailable for " + releaseTag, e);
                }
                logger.accept("WARNING: SHA256SUMS unavailable and archive is cached; "
                        + "skipping verification");
                return;
            }
        }

        String expectedHash = parseSha256Sums(Files.readString(sumsFile), assetName);
        if (expectedHash == null) {
            if (freshlyDownloaded) {
                Files.deleteIfExists(archive);
                throw new IOException("No checksum entry for " + assetName
                        + " in SHA256SUMS of " + releaseTag);
            }
            logger.accept("WARNING: no checksum entry for " + assetName
                    + "; skipping verification of cached archive");
            return;
        }

        String actualHash = computeFileSha256(archive);
        if (!expectedHash.equalsIgnoreCase(actualHash)) {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(sumsFile);
            throw new IOException("SHA256 mismatch for " + assetName
                    + ": expected " + expectedHash + ", got " + actualHash);
        }
        logger.accept("SHA256 checksum verified: " + actualHash);
    }

    /**
     * Finds the checksum for {@code assetName} in a {@code SHA256SUMS} file.
     * Each line is {@code <hex>  <name>}, optionally {@code <hex> *<name>}.
     *
     * @return the lowercase hex digest, or {@code null} when the asset is absent
     */
    static String parseSha256Sums(String content, String assetName) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length < 2) {
                continue;
            }
            String name = parts[1].trim();
            if (name.startsWith("*")) {
                name = name.substring(1);
            }
            if (name.equals(assetName)) {
                return parts[0].toLowerCase();
            }
        }
        return null;
    }

    static void writeReleaseMetadataCache(Path cacheFile, ReleaseMetadata metadata) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("tag=").append(metadata.tag()).append('\n');
        for (String asset : metadata.assets()) {
            sb.append("asset=").append(asset).append('\n');
        }
        Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
    }

    static ReleaseMetadata readReleaseMetadataCache(Path cacheFile) {
        if (!Files.exists(cacheFile)) {
            return null;
        }
        try {
            String tag = null;
            List<String> assets = new ArrayList<>();
            for (String line : Files.readAllLines(cacheFile, StandardCharsets.UTF_8)) {
                if (line.startsWith("tag=")) {
                    tag = line.substring("tag=".length()).trim();
                } else if (line.startsWith("asset=")) {
                    assets.add(line.substring("asset=".length()));
                }
            }
            if (tag == null || tag.isEmpty() || assets.isEmpty()) {
                return null;
            }
            return new ReleaseMetadata(tag, List.copyOf(assets));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A resolved python-build-standalone release tag and the names of its assets.
     */
    record ReleaseMetadata(String tag, List<String> assets) {
    }

    private static void downloadFile(String url, Path target, Consumer<String> logger) throws IOException {
        IOException lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                    logger.accept("Retry " + attempt + "/" + (MAX_RETRY_ATTEMPTS - 1)
                            + " in " + (delay / 1000) + "s...");
                    Thread.sleep(delay);
                }

                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
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

    private static HttpURLConnection createGitHubConnection(URI uri) throws IOException {
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

    @SuppressWarnings("unchecked")
    private static Map<?, ?> parseJson(String json) throws IOException {
        // Minimal JSON parser for GitHub API responses (avoids external deps).
        // Only handles the subset needed: nested objects, arrays, strings, numbers.
        return (Map<?, ?>) parseValue(new JsonTokenizer(json));
    }

    private static Object parseValue(JsonTokenizer t) throws IOException {
        t.skipWhitespace();
        if (t.peek() == '{') {
            return parseObject(t);
        } else if (t.peek() == '[') {
            return parseArray(t);
        } else if (t.peek() == '"') {
            return parseString(t);
        } else {
            return parseLiteral(t);
        }
    }

    private static Map<String, Object> parseObject(JsonTokenizer t) throws IOException {
        t.expect('{');
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (t.peek() == '}') {
            t.advance();
            return map;
        }
        while (true) {
            t.skipWhitespace();
            String key = parseString(t);
            t.skipWhitespace();
            t.expect(':');
            map.put(key, parseValue(t));
            t.skipWhitespace();
            if (t.peek() == '}') {
                t.advance();
                return map;
            }
            t.expect(',');
        }
    }

    private static List<Object> parseArray(JsonTokenizer t) throws IOException {
        t.expect('[');
        List<Object> list = new java.util.ArrayList<>();
        if (t.peek() == ']') {
            t.advance();
            return list;
        }
        while (true) {
            t.skipWhitespace();
            list.add(parseValue(t));
            t.skipWhitespace();
            if (t.peek() == ']') {
                t.advance();
                return list;
            }
            t.expect(',');
        }
    }

    private static String parseString(JsonTokenizer t) throws IOException {
        t.expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = t.advance();
            if (c == '"') break;
            if (c == '\\') {
                char escaped = t.advance();
                switch (escaped) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        String hex = new String(new char[]{
                                t.advance(), t.advance(), t.advance(), t.advance()});
                        sb.append((char) Integer.parseInt(hex, 16));
                        break;
                    default: sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Object parseLiteral(JsonTokenizer t) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (!t.eof() && !Character.isWhitespace(t.peek())
                && t.peek() != ',' && t.peek() != '}' && t.peek() != ']') {
            sb.append(t.advance());
        }
        String token = sb.toString();
        if ("true".equals(token)) return true;
        if ("false".equals(token)) return false;
        if ("null".equals(token)) return null;
        try {
            if (token.contains(".")) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            throw new IOException("Unexpected JSON token: " + token);
        }
    }

    private static String computeFileSha256(Path file) throws IOException {
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

    @SuppressWarnings("unchecked")
    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(src -> {
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
    }

    private static void makeExecutables(Path dir) throws IOException {
        Path binDir = dir.resolve("bin");
        if (Files.exists(binDir)) {
            try (Stream<Path> stream = Files.list(binDir)) {
                stream.forEach(f -> {
                    String name = f.getFileName().toString();
                    if (name.startsWith("python") || name.startsWith("pip")) {
                        f.toFile().setExecutable(true);
                    }
                });
            }
        }
        Path pythonExe = dir.resolve("python.exe");
        if (Files.exists(pythonExe)) {
            pythonExe.toFile().setExecutable(true);
        }
    }

    private static void deleteRecursively(Path path, Consumer<String> logger) {
        if (!Files.exists(path)) return;
        try {
            try (Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        logger.accept("Failed to delete: " + p);
                    }
                });
            }
        } catch (IOException e) {
            logger.accept("Failed to delete directory: " + path);
        }
    }

    /**
     * Minimal streaming JSON tokenizer.
     */
    private static final class JsonTokenizer {
        private final String input;
        private int pos;

        JsonTokenizer(String input) {
            this.input = input;
            this.pos = 0;
        }

        boolean eof() {
            return pos >= input.length();
        }

        char peek() {
            return input.charAt(pos);
        }

        char advance() throws IOException {
            if (eof()) throw new IOException("Unexpected end of JSON");
            return input.charAt(pos++);
        }

        void expect(char expected) throws IOException {
            if (eof() || advance() != expected) {
                throw new IOException("Expected '" + expected + "' at position " + (pos - 1));
            }
        }

        void skipWhitespace() {
            while (!eof() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
    }
}
