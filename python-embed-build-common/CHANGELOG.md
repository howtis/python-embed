# Changelog

## [1.0.2] - 2026-06-26

### Added
- `VenvManager` — shared venv setup logic extracted from Gradle plugin
- `VenvConfig` — configuration record for venv setup
- `PythonDownloader` — downloads python-build-standalone releases from GitHub
- `PythonResolver` — detects system Python and resolves python executable paths
- `FingerprintManager` — incremental rebuild via package hash fingerprinting
- `TarGzExtractor` — custom tar.gz extractor with path traversal protection
- `RequirementsParser` — parses pip requirements.txt files

[build-common-v1.0.2]: https://github.com/howtis/python-embed/releases/tag/build-common-v1.0.2
