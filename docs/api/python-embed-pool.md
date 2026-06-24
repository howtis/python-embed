# PythonEmbedPool API

`PythonEmbedPool` manages a pool of `PythonEmbed` instances for concurrent Python execution. All methods return `CompletableFuture` for async usage.

Implements `AutoCloseable`.

## Static Methods

### `builder()`
Returns a new `Builder` for fluent configuration.

## Builder Configuration

```java
PythonEmbedPool pool = PythonEmbedPool.builder()
    .minPool(2)                       // min idle instances (default: 2)
    .maxPool(8)                       // max instances (default: 8)
    .idleTimeoutMs(60_000)            // idle removal timeout (default: 60s)
    .healthCheckIntervalMs(30_000)    // health ping interval (default: 30s)
    .options(PythonEmbed.Options.defaults())
    .onInstanceRemoved((embed, reason) -> { /* logging */ })
    .build();
```

## Instance Methods

### Evaluation & Execution

| Method | Returns | Description |
|--------|---------|-------------|
| `eval(String code)` | `CompletableFuture<PythonValue>` | Async evaluate expression |
| `eval(String code, long timeoutMs)` | `CompletableFuture<PythonValue>` | With timeout |
| `eval(Map<String, Object> vars, String code)` | `CompletableFuture<PythonValue>` | With variables |
| `exec(String code)` | `CompletableFuture<Void>` | Async execute statements |
| `exec(String code, long timeoutMs)` | `CompletableFuture<Void>` | With timeout |
| `exec(Map<String, Object> vars, String code)` | `CompletableFuture<Void>` | With variables |
| `execFile(Path scriptPath)` | `CompletableFuture<Void>` | Execute file |
| `execFile(Path scriptPath, long timeoutMs)` | `CompletableFuture<Void>` | File with timeout |

### Batch Operations

| Method | Returns | Description |
|--------|---------|-------------|
| `batchEval(List<String> codes)` | `CompletableFuture<List<PythonValue>>` | Async batch eval |
| `batchEval(List<String> codes, long timeoutMs)` | `CompletableFuture<List<PythonValue>>` | With timeout |
| `batchExec(List<String> codes)` | `CompletableFuture<Void>` | Async batch exec |
| `batchExec(List<String> codes, long timeoutMs)` | `CompletableFuture<Void>` | With timeout |

### Streaming

| Method | Returns | Description |
|--------|---------|-------------|
| `stream(String code)` | `CompletableFuture<Iterator<PythonValue>>` | Async stream |
| `stream(String code, long pollTimeoutMs)` | `CompletableFuture<Iterator<PythonValue>>` | With poll timeout |

### Object Handles & Proxies

| Method | Returns | Description |
|--------|---------|-------------|
| `ref(String variableName)` | `CompletableFuture<PythonHandle>` | Get handle |
| `proxy(PythonHandle handle, Class<T> iface)` | `T` | From handle |
| `proxy(int refId, Class<T> iface)` | `T` | From ref ID |
| `proxy(String variableName, Class<T> iface)` | `T` | From variable name |

### Callbacks

| Method | Description |
|--------|-------------|
| `registerCallback(String name, CallbackHandler)` | Register on all instances |
| `registerCallback(String name, Class<A>, CallbackHandler1<A>)` | Typed 1-arg |
| `registerCallback(String name, Class<A>,Class<B>, CallbackHandler2<A,B>)` | Typed 2-arg |
| `registerCallback(String name, Class<A>,Class<B>,Class<C>, CallbackHandler3<A,B,C>)` | Typed 3-arg |
| `registerPushHandler(String name, PushHandler)` | Register push handler |
| `registerPushHandler(String name, Class<T>, PushHandler<T>)` | Typed push handler |

### Monitoring & Lifecycle

| Method | Returns | Description |
|--------|---------|-------------|
| `size()` | `int` | Current number of instances |
| `activeCount()` | `int` | Instances currently in use |
| `minPool()` | `int` | Configured minimum |
| `maxPool()` | `int` | Configured maximum |
| `healthCheck()` | `int` | Run health check, returns removed count |
| `close()` | `void` | Graceful shutdown |
| `close(long timeout, TimeUnit unit)` | `void` | Shutdown with timeout |
