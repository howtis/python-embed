# Changelog

## [1.0.3] - 2026-06-25

### Fixed
- `NoClassDefFoundError` on `HealthIndicator` when Spring Boot Actuator is not on the classpath

## [1.0.2] - 2026-06-24

### Added
- Spring Boot Configuration Metadata for IDE autocompletion (`spring-configuration-metadata.json`)
- Missing option properties for pool settings (minPool, maxPool, maxIdle, keepAlive, startupTimeout, healthCheckIntervalMs)

### Changed
- Independent module versioning with `starter-v*` publish tags
- Version extracted from git tags in publish workflow

## [1.0.1] - 2026-06-23

### Changed
- Include MIT LICENSE file in all JAR artifacts

## [1.0.0] - 2026-06-23

### Added
- Spring Boot 3.x auto-configuration (`PythonEmbedAutoConfiguration`)
- SINGLE mode with `PythonEmbed` bean
- POOL mode with `PythonEmbedPool` bean
- Actuator `HealthIndicator` for Python process health
- `@ConfigurationProperties` binding for all pool and runtime settings
- Auto-configuration conditional on `python-embed-runtime` on classpath

[starter-v1.0.3]: https://github.com/howtis/python-embed/releases/tag/starter-v1.0.3
[starter-v1.0.2]: https://github.com/howtis/python-embed/releases/tag/starter-v1.0.2
[starter-v1.0.1]: https://github.com/howtis/python-embed/releases/tag/v1.0.1
[starter-v1.0.0]: https://github.com/howtis/python-embed/releases/tag/v1.0.0
