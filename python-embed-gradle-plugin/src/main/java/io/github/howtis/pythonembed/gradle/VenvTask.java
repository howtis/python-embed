package io.github.howtis.pythonembed.gradle;

import io.github.howtis.pythonembed.build.FingerprintManager;
import io.github.howtis.pythonembed.build.PythonResolver;
import io.github.howtis.pythonembed.build.TarGzExtractor;
import io.github.howtis.pythonembed.build.VenvConfig;
import io.github.howtis.pythonembed.build.VenvManager;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gradle task that creates a Python environment and installs packages into it.
 *
 * <p>Delegates to {@link VenvManager} in {@code python-embed-build-common}
 * for the core venv setup logic.
 */
public abstract class VenvTask extends DefaultTask {

    private static final Logger logger = Logger.getLogger(VenvTask.class.getName());

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
        String requirementsFile = getRequirementsFile().getOrNull();
        String pyprojectTomlFile = getPyprojectTomlFile().getOrNull();
        String pipIndexUrl = getPipIndexUrl().getOrNull();
        List<String> pipExtraArgs = getPipExtraArgs().get();
        String pythonVersion = getPythonVersion().get();
        String targetOs = getTargetOs().getOrNull();
        Path projectRoot = getProject().getRootDir().toPath();
        Path venvDir = getVenvDir().get().getAsFile().toPath();

        Path requirementsPath = null;
        Path pyprojectPath = null;

        if (requirementsFile != null && !requirementsFile.isEmpty()) {
            requirementsPath = projectRoot.resolve(requirementsFile);
        }
        if (pyprojectTomlFile != null && !pyprojectTomlFile.isEmpty()) {
            pyprojectPath = projectRoot.resolve(pyprojectTomlFile);
        }

        VenvConfig config = VenvConfig.builder()
                .packages(packages)
                .pythonVersion(pythonVersion)
                .requirementsFile(requirementsPath)
                .pyprojectTomlFile(pyprojectPath)
                .pipIndexUrl(pipIndexUrl)
                .pipExtraArgs(pipExtraArgs)
                .venvDir(venvDir)
                .targetOs(targetOs)
                .logger(msg -> logger.info(msg))
                .build();

        try {
            VenvManager.setup(config);
        } catch (IOException e) {
            throw new GradleException("Python environment setup failed: " + e.getMessage(), e);
        }
    }

    // ---- delegation methods for backward-compatibility (used by tests) ----

    String computePackageHash(List<String> packages, String pipIndexUrl,
                               List<String> pipExtraArgs, Path pyprojectPath) {
        try {
            return FingerprintManager.computePackageHash(packages, pipIndexUrl, pipExtraArgs, pyprojectPath);
        } catch (IOException e) {
            throw new GradleException("Failed to compute package hash", e);
        }
    }

    Path findPythonInDir(Path dir) {
        String targetOs = resolveTargetOs();
        try {
            return PythonResolver.findPythonInDir(dir, targetOs);
        } catch (IOException e) {
            return null;
        }
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

    void extractTarGz(Path tarGzFile, Path targetDir) throws IOException {
        TarGzExtractor.extract(tarGzFile, targetDir);
    }

    private String resolveTargetOs() {
        String target = getTargetOs().getOrNull();
        if (target != null && !target.isEmpty()) {
            return target.toLowerCase();
        }
        if (PythonResolver.isWindows()) return "windows";
        if (PythonResolver.isMac()) return "macos";
        return "linux";
    }
}
