# Changelog

## [1.0.4] - 2026-11-20

### Changed
- Depends on `python-embed-build-common` 1.0.4: download verification against `SHA256SUMS`, cached release metadata, and `pythonVersion` pinning for `python-embed:setup`.

## [1.0.2] - 2026-06-26

### Added
- `SetupMojo` — `python-embed:setup` goal for venv creation and package installation
- `PropertiesMojo` — `python-embed:properties` goal to expose Python environment properties
- `HelpMojo` — `python-embed:help` goal with plugin documentation
- Maven plugin descriptor (`plugin.xml`) for `io.github.howtis:python-embed-maven-plugin`

[maven-plugin-v1.0.2]: https://github.com/howtis/python-embed/releases/tag/maven-plugin-v1.0.2
