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
    private static final String RELEASES_URL =
            "https://api.github.com/repos/indygreg/python-build-standalone/releases";

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
        String releaseTag = fetchLatestReleaseTag(logger);

        Files.createDirectories(cacheDir);

        String exactAsset = findMatchingAsset(releaseTag, version, targetTriple, logger);
        Path archiveFile = cacheDir.resolve(exactAsset);

        if (!Files.exists(archiveFile)) {
            String downloadUrl = "https://github.com/indygreg/python-build-standalone"
                    + "/releases/download/" + releaseTag + "/" + exactAsset;
            logger.accept("Downloading: " + downloadUrl);
            downloadFile(downloadUrl, archiveFile, logger);
            logger.accept("Downloaded to: " + archiveFile);

            Path shaFile = cacheDir.resolve(exactAsset + ".sha256");
            downloadFile(downloadUrl + ".sha256", shaFile, logger);
            String expectedHash = Files.readString(shaFile).trim().split("\\s+")[0];
            String actualHash = computeFileSha256(archiveFile);
            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                Files.deleteIfExists(archiveFile);
                Files.deleteIfExists(shaFile);
                throw new IOException("SHA256 mismatch for " + exactAsset
                        + ": expected " + expectedHash + ", got " + actualHash);
            }
            logger.accept("SHA256 checksum verified: " + actualHash);
        } else {
            logger.accept("Using cached: " + archiveFile);
        }

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

    private static String fetchLatestReleaseTag(Consumer<String> logger) throws IOException {
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
            if (tagName != null) {
                logger.accept("Latest release tag: " + tagName);
                return tagName.toString();
            }
            throw new IOException("Cannot find tag_name in release JSON");
        }
    }

    private static String findMatchingAsset(String releaseTag, String pythonVersion,
                                            String targetTriple, Consumer<String> logger) throws IOException {
        URI apiUri = URI.create(RELEASES_URL + "/tags/" + releaseTag);
        HttpURLConnection conn = createGitHubConnection(apiUri);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("GitHub API returned HTTP " + status
                    + " for releases/tags/" + releaseTag);
        }

        try (InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Map<?, ?> release = parseJson(json);
            Object assets = release.get("assets");
            if (assets instanceof List) {
                for (Object asset : (List<?>) assets) {
                    if (asset instanceof Map) {
                        Map<?, ?> assetMap = (Map<?, ?>) asset;
                        String name = assetMap.get("name").toString();
                        if (name.startsWith("cpython-" + pythonVersion + ".")
                                && name.contains(targetTriple)
                                && name.endsWith("-install_only.tar.gz")) {
                            logger.accept("Found matching asset: " + name);
                            return name;
                        }
                    }
                }
            }
        }
        throw new IOException("No matching python-build-standalone asset found for "
                + "Python " + pythonVersion + " on " + targetTriple);
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
