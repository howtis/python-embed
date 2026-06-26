package io.github.howtis.pythonembed.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * Manages Python environment fingerprints for incremental builds.
 */
public final class FingerprintManager {

    static final String FINGERPRINT_FILE = "python-embed.fingerprint";
    static final String KEY_PYTHON_SOURCE = "python.source";
    static final String KEY_PYTHON_VERSION = "python.version";
    static final String KEY_PACKAGES_HASH = "packages.hash";

    private FingerprintManager() {
    }

    /**
     * Computes a SHA-256 hash of the package configuration for change detection.
     *
     * @param packages list of pip package specifications
     * @param pipIndexUrl optional pip index URL
     * @param pipExtraArgs optional extra pip arguments
     * @param pyprojectPath optional pyproject.toml file path
     * @return hex-encoded SHA-256 hash
     * @throws IOException if pyproject.toml cannot be read
     */
    public static String computePackageHash(List<String> packages, String pipIndexUrl,
                                            List<String> pipExtraArgs, Path pyprojectPath) throws IOException {
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
            throw new IOException("SHA-256 not available", e);
        }
    }

    /**
     * Reads the stored fingerprint from a venv directory.
     *
     * @param venvDir path to the venv directory
     * @return the fingerprint, or null if not found or invalid
     */
    public static Fingerprint read(Path venvDir) {
        Path fp = venvDir.resolve(FINGERPRINT_FILE);
        if (!Files.exists(fp)) {
            return null;
        }
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(fp, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            return null;
        }
        String pythonSource = props.getProperty(KEY_PYTHON_SOURCE);
        String pythonVersion = props.getProperty(KEY_PYTHON_VERSION);
        String packageHash = props.getProperty(KEY_PACKAGES_HASH);
        if (pythonSource == null || pythonVersion == null || packageHash == null) {
            return null;
        }
        return new Fingerprint(pythonSource, pythonVersion, packageHash);
    }

    /**
     * Writes a fingerprint to the venv directory.
     *
     * @param venvDir path to the venv directory
     * @param pythonSource "system" or "bundled"
     * @param pythonVersion Python version string
     * @param packageHash package configuration hash
     * @throws UncheckedIOException if writing fails
     */
    public static void write(Path venvDir, String pythonSource, String pythonVersion,
                             String packageHash) {
        Properties props = new Properties();
        props.setProperty(KEY_PYTHON_SOURCE, pythonSource);
        props.setProperty(KEY_PYTHON_VERSION, pythonVersion);
        props.setProperty(KEY_PACKAGES_HASH, packageHash);
        Path fp = venvDir.resolve(FINGERPRINT_FILE);
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
     * Immutable fingerprint data.
     */
    public static class Fingerprint {
        public final String pythonSource;
        public final String pythonVersion;
        public final String packageHash;

        Fingerprint(String pythonSource, String pythonVersion, String packageHash) {
            this.pythonSource = pythonSource;
            this.pythonVersion = pythonVersion;
            this.packageHash = packageHash;
        }
    }
}
