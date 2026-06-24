# Changelog

## [1.0.2] - 2026-06-24

Modules are now independently versioned with per-module changelogs:
- [python-embed-runtime](python-embed-runtime/CHANGELOG.md) (1.0.2) — independent versioning with `runtime-v*` publish tags
- [python-embed-spring-boot-starter](python-embed-spring-boot-starter/CHANGELOG.md) (1.0.2) — Configuration Metadata, missing option properties, independent versioning
- [python-embed-gradle-plugin](python-embed-gradle-plugin/CHANGELOG.md) (1.0.2) — `targetOs` cross-compilation, cross-platform fix, plugin-publish 2.1.1, independent versioning

## [1.0.1] - 2026-06-23

### Changed
- Add default_encoder fallback to msgpack.packb in write_frame for broader type compatibility
- Include MIT LICENSE file in all JAR artifacts

## [1.0.0] - 2026-06-23

### Added
- Embed CPython in JVM via subprocess + MessagePack binary protocol
- `PythonEmbed` runtime with eval, exec, execFile, and toJson APIs
- `PythonEmbedPool` with auto-scaling and async `CompletableFuture` API
- Spring Boot 3.x auto-configuration (`python-embed-spring-boot-starter`)
- SINGLE and POOL modes with Actuator `HealthIndicator`
- Gradle plugin (`python-embed-gradle-plugin`) for venv creation and package installation
- Python auto-download via python-build-standalone when system Python is absent
- Object handles with numeric ID referencing for long-lived Python objects
- Generator/streaming support via Java `Iterator`
- Python-to-Java callbacks via `_bridge.call()` and `_bridge.push()`
- Batch execution (`batchEval`/`batchExec`) for multiple requests in one round-trip
- Proxy objects (`PythonProxy`) with dynamic Java interface implementation
- Builder API with fluent construction for `PythonEmbed` and `PythonEmbedPool`
- Type-safe argument conversion (`arg()`): null, Boolean, Number, String, List, Map, Set, byte[], datetime
- Python log forwarding to SLF4J via `python.*` logger namespace
- Periodic health check with RSS memory, ref count, and GC status reporting
- Close hook support for resource cleanup
- 13 example applications in `python-embed-examples`

[1.0.2]: See per-module changelogs above
[1.0.1]: https://github.com/howtis/python-embed/releases/tag/v1.0.1
[1.0.0]: https://github.com/howtis/python-embed/releases/tag/v1.0.0
