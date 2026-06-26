package io.github.howtis.pythonembed.build;

import java.nio.file.Path;

/**
 * Immutable result of a Python environment setup.
 *
 * @param pythonExecutable path to the Python executable
 * @param venvDir path to the venv root directory
 * @param source "system" if system Python was used, "bundled" if python-build-standalone was downloaded
 */
public record PythonEnvironment(Path pythonExecutable, Path venvDir, String source) {
}
