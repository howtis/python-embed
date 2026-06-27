# Changelog

## [1.0.3] - 2026-06-27

### Added
- Sticky Session for `ref()` handles: `PythonEmbed.hasActiveHandles()` ensures instances with live handles are excluded from idle scale-down

### Deprecated
- `PythonEmbedPool.proxy(int refId, Class)` and `PythonEmbed.proxy(int refId, Class)` — use `proxy(PythonHandle, Class)` or `proxy(String, Class)` instead

## [1.0.2] - 2026-06-24

### Changed
- Independent module versioning with `runtime-v*` publish tags
- Version extracted from git tags in publish workflow

## [1.0.1] - 2026-06-23

### Changed
- Add default_encoder fallback to msgpack.packb in write_frame for broader type compatibility
- Include MIT LICENSE file in all JAR artifacts

## [1.0.0] - 2026-06-23

### Added
- `PythonEmbed` runtime with eval, exec, execFile, and toJson APIs
- `PythonEmbedPool` with auto-scaling and async `CompletableFuture` API
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

[runtime-v1.0.3]: https://github.com/howtis/python-embed/releases/tag/runtime-v1.0.3
[runtime-v1.0.2]: https://github.com/howtis/python-embed/releases/tag/runtime-v1.0.2
[runtime-v1.0.1]: https://github.com/howtis/python-embed/releases/tag/v1.0.1
[runtime-v1.0.0]: https://github.com/howtis/python-embed/releases/tag/v1.0.0
