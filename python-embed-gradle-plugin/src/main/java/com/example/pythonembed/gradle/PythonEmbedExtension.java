package com.example.pythonembed.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration extension for the pythonEmbed plugin.
 *
 * <pre>{@code
 * pythonEmbed {
 *     pythonVersion = '3.12'
 *     packages = ['numpy==1.26.4', 'torch==2.4.0']
 *     pipIndexUrl = 'https://download.pytorch.org/whl/cu118'
 *     pipExtraArgs = ['--extra-index-url', 'https://example.com/simple']
 *     venvOutputDir = layout.buildDirectory.dir('my-venv')
 * }
 * }</pre>
 */
public abstract class PythonEmbedExtension {

    /** Python version to use (default: "3.12"). */
    public abstract Property<String> getPythonVersion();

    /** List of pip packages to install (default: empty). */
    public abstract ListProperty<String> getPackages();

    /** Optional pip index URL (e.g., PyTorch CUDA index). */
    public abstract Property<String> getPipIndexUrl();

    /** Optional extra pip install arguments (e.g., --extra-index-url, -f). */
    public abstract ListProperty<String> getPipExtraArgs();

    /**
     * Output directory for the Python virtual environment.
     *
     * <p>If not set, defaults to {@code build/python-venv} and the venv is embedded
     * in the JAR as a classpath resource. If set to a path outside the build
     * directory, the venv is kept external and referenced via a generated
     * properties file at runtime.
     */
    public abstract DirectoryProperty getVenvOutputDir();

    /** Optional path to requirements.txt (relative to project root). */
    public abstract Property<String> getRequirementsFile();

    /** Optional path to pyproject.toml (relative to project root). */
    public abstract Property<String> getPyprojectTomlFile();

    public PythonEmbedExtension() {
        getPythonVersion().convention("3.12");
        getPackages().convention(java.util.List.of());
        getPipExtraArgs().convention(java.util.List.of());
    }
}
