# PythonEmbed API

`PythonEmbed` is the core class for embedding CPython in Java. It manages a subprocess running `bridge.py` and communicates via MessagePack over stdin/stdout.

Implements `AutoCloseable` — always use try-with-resources.

## Static Methods

### `create()`
Creates a `PythonEmbed` with default options.

### `create(Options options)`
Creates a `PythonEmbed` with the given options.

### `arg(Object value)`
Converts a Java value to a safe Python literal string. Supports: `null`, `Boolean`, `Number`, `String`, `List`, `Set`, `Map`, `byte[]`, `Instant`.

## Instance Methods

### Evaluation & Execution

| Method | Returns | Description |
|--------|---------|-------------|
| `eval(String code)` | `PythonValue` | Evaluate a Python expression |
| `eval(String code, long timeoutMs)` | `PythonValue` | Evaluate with per-request timeout |
| `eval(Map<String, Object> vars, String code)` | `PythonValue` | Evaluate with Java variables passed to Python |
| `exec(String code)` | `void` | Execute Python statements (shared namespace) |
| `exec(String code, long timeoutMs)` | `void` | Execute with per-request timeout |
| `exec(Map<String, Object> vars, String code)` | `void` | Execute with Java variables |
| `execFile(Path scriptPath)` | `void` | Execute a Python script file |
| `execFile(Path scriptPath, long timeoutMs)` | `void` | Execute file with timeout |

### Batch Operations

| Method | Returns | Description |
|--------|---------|-------------|
| `batchEval(List<String> codes)` | `List<PythonValue>` | Evaluate multiple expressions in one round-trip |
| `batchEval(List<String> codes, long timeoutMs)` | `List<PythonValue>` | Batch eval with timeout |
| `batchExec(List<String> codes)` | `void` | Execute multiple statements in one round-trip |
| `batchExec(List<String> codes, long timeoutMs)` | `void` | Batch exec with timeout |

### Object Handles & Proxies

| Method | Returns | Description |
|--------|---------|-------------|
| `ref(String variableName)` | `PythonHandle` | Get a persistent handle to a Python object |
| `proxy(String variableName, Class<T> iface)` | `T` | Wrap a Python object as a Java interface |
| `proxy(int refId, Class<T> iface)` | `T` | Wrap using a numeric handle ID |
| `forgetHandle(PythonHandle handle)` | `void` | Explicitly release a handle |

### Streaming

| Method | Returns | Description |
|--------|---------|-------------|
| `stream(String code)` | `Iterator<PythonValue>` | Stream a Python generator lazily |
| `stream(String code, long pollTimeoutMs)` | `Iterator<PythonValue>` | Stream with custom poll timeout |

### Callbacks

| Method | Description |
|--------|-------------|
| `registerCallback(String name, CallbackHandler)` | Register a Python→Java callback |
| `registerCallback(String name, Class<A>, CallbackHandler1<A>)` | Typed 1-arg callback |
| `registerCallback(String name, Class<A>,Class<B>, CallbackHandler2<A,B>)` | Typed 2-arg callback |
| `registerCallback(String name, Class<A>,Class<B>,Class<C>, CallbackHandler3<A,B,C>)` | Typed 3-arg callback |
| `registerPushHandler(String name, PushHandler)` | Register a push event handler |
| `registerPushHandler(String name, Class<T>, PushHandler<T>)` | Typed push handler |

### Health & Monitoring

| Method | Returns | Description |
|--------|---------|-------------|
| `ping()` | `boolean` | Quick liveness check |
| `health()` | `HealthInfo` | Detailed health: RSS, refs, GC status |
| `isOpen()` | `boolean` | Whether the embed is running |
| `getPid()` | `long` | Python process PID (-1 if not running) |

### Lifecycle

| Method | Description |
|--------|-------------|
| `close()` | Graceful shutdown |
| `close(long timeout, TimeUnit unit)` | Graceful shutdown with timeout |
| `hardShutdown()` | Force-destroy the Python process |
| `warmup(String script)` | Execute script on already-running instance |

## Options

`PythonEmbed.Options` is built with a fluent builder:

```java
PythonEmbed.Options options = PythonEmbed.Options.builder()
    .timeoutMs(60_000)           // per-request timeout (default: 30s)
    .startupTimeoutMs(10_000)    // process startup timeout
    .maxCodeLength(200_000)      // max code length in chars
    .venvPath(Path.of("/opt/venv"))
    .pythonExecutable("python3")
    .env(Map.of("VAR", "value"))
    .warmupScript("import numpy as np")
    .warmupScripts(List.of("import math", "import json"))
    .lenientWarmup(true)
    .onBeforeClose((py, reason) -> { /* cleanup */ })
    .onAfterClose((py, reason) -> { /* logging */ })
    .build();
```
