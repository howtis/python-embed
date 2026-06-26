# Changelog

## [1.0.3] - 2026-06-26

### Changed
- Extracted shared build logic into `python-embed-build-common` (VenvManager, PythonDownloader, FingerprintManager, etc.)
- Removed `RequirementsParser` — now provided by build-common
- Cache path standardized: `~/.gradle/python-embed` → `~/.python-embed`
- Added `implementation 'io.github.howtis:python-embed-build-common'` dependency

### Added
- `pyprojectTomlFile` property for pyproject.toml-based dependency installation
- `pipIndexUrl` and `pipExtraArgs` properties in `VenvTask`
- `@Optional` annotations on all task properties for Gradle up-to-date checks
- `sourcesJar` for Maven Central compliance
- Java toolchain configuration (JDK 17)

### Fixed
- FingerprintManager consistency improvements for incremental rebuild
- Javadoc improvements across extension and task classes

## [1.0.2] - 2026-06-24

### Added
- `targetOs` property for cross-compilation in Gradle plugin (windows, linux, macos)

### Fixed
- Cross-platform compatibility in `VenvTask.extractTarGz`: backslashes converted to forward slashes in tar entry paths

### Changed
- Upgrade `com.gradle.plugin-publish` 1.3.1 → 2.1.1 with compatibility declaration
- Add Gradle wrapper to plugin subproject for CI independence
- Independent module versioning with `plugin-v*` publish tags
- Version extracted from git tags in publish workflow

## [1.0.1] - 2026-06-23

### Changed
- Include MIT LICENSE file in all JAR artifacts

## [1.0.0] - 2026-06-23

### Added
- Gradle plugin for venv creation and package installation
- Python auto-download via python-build-standalone when system Python is absent
- Incremental venv rebuild on dependency changes
- Embedded venv (JAR extraction) and external path modes
- `PythonEmbedExtension` for plugin configuration (pythonPath, packages, requirementsFile, targetOs)
- `VenvTask` with pip install, dependency hash tracking, and platform detection

[plugin-v1.0.3]: https://github.com/howtis/python-embed/releases/tag/plugin-v1.0.3
[plugin-v1.0.2]: https://github.com/howtis/python-embed/releases/tag/plugin-v1.0.2
[plugin-v1.0.1]: https://github.com/howtis/python-embed/releases/tag/v1.0.1
[plugin-v1.0.0]: https://github.com/howtis/python-embed/releases/tag/v1.0.0
