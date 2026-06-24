# Installation

## Gradle Plugin

Add the plugin to `build.gradle`:

```groovy
plugins {
    id 'io.github.howtis.python-embed' version '1.0.2'
}

dependencies {
    implementation 'io.github.howtis:python-embed-runtime:1.0.2'
}
```

## Plugin Configuration

The `pythonEmbed` extension controls Python environment setup at build time:

```groovy
pythonEmbed {
    packages = [
        'numpy',
        'torch',
        'msgpack'
    ]
    requirementsFile = 'requirements.txt'  // alternative to packages
}
```

### How It Works

1. Checks for `python3 --version` (system Python)
2. If found → `python -m venv` + `pip install` packages
3. If not found → downloads [python-build-standalone](https://github.com/astral-sh/python-build-standalone) (~50 MB, cached) and installs packages directly
4. Packages the venv as a JAR resource for runtime extraction

### Extension Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `packages` | `List<String>` | `[]` | Pip packages to install |
| `requirementsFile` | `String` | — | Path to `requirements.txt` (relative to project root) |
| `pyprojectTomlFile` | `String` | — | Path to `pyproject.toml` (relative to project root) |
| `pythonVersion` | `String` | `3.12` | Python version (for python-build-standalone) |
| `pipIndexUrl` | `String` | — | Custom pip index URL (e.g., PyTorch CUDA) |
| `pipExtraArgs` | `List<String>` | `[]` | Extra pip install arguments (e.g., `-f`, `--extra-index-url`) |
| `venvOutputDir` | `Directory` | `build/python-venv` | Output directory for the venv |
| `targetOs` | `String` | auto-detected | Cross-compile for `windows`, `linux`, or `macos` |

The venv is rebuilt incrementally — only when dependencies change.

## Spring Boot Starter

For Spring Boot 3.x applications, add the starter:

```groovy
dependencies {
    implementation 'io.github.howtis:python-embed-spring-boot-starter:1.0.2'
}
```

This provides zero-code auto-configuration. See the [Spring Boot guide](spring-boot.md).

## Requirements

- JDK 17+
- Python 3.8+ (auto-downloaded if absent via python-build-standalone)
- Gradle 8.x
