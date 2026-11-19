# Changelog

## [1.0.3] - 2026-11-20

### Fixed
- `PythonDownloader` no longer requests a per-asset `.sha256` file. python-build-standalone stopped publishing those at the 2024-03 release, so every download failed with HTTP 404; verification now reads the release's `SHA256SUMS` asset instead.
- Release metadata (tag plus asset names) is cached in `~/.python-embed/release-metadata.properties`, and a single GitHub API request now supplies both. Builds survive the anonymous 60-requests-per-hour limit and offline runs instead of failing with HTTP 403.
- Download URLs and the API base point at `astral-sh/python-build-standalone`; the previous `indygreg/` repository redirects.
- Cached archives are verified too, not just fresh downloads. A freshly downloaded archive is never used unverified.
- `pythonVersion` is now honoured: system Python is used only when it satisfies the request, otherwise python-build-standalone is downloaded. The fingerprint records the version the venv actually runs, and `fingerprint.format` invalidates fingerprints written under the old meaning.

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
