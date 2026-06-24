# Pool

`PythonEmbedPool` provides concurrent Python execution with an auto-scaling instance pool.

## Basic Pool Usage

```java
try (PythonEmbedPool pool = PythonEmbedPool.builder()
        .minPool(2)
        .maxPool(8)
        .idleTimeoutMs(30_000)
        .options(PythonEmbed.Options.defaults())
        .build()) {

    // Async evaluation — returns CompletableFuture
    CompletableFuture<PythonValue> f1 = pool.eval("sum([1, 2, 3])");
    CompletableFuture<PythonValue> f2 = pool.eval("np.arange(5).tolist()");

    // Wait for results
    int sum = f1.get().asInt();
    List<Object> arr = f2.get().asList();

    // Async exec
    pool.exec("x = 42").get();
}
```

## Builder Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `minPool` | 2 | Minimum number of idle instances kept ready |
| `maxPool` | 8 | Maximum number of instances |
| `idleTimeoutMs` | 60,000 | Time before idle instances are removed |
| `healthCheckIntervalMs` | 30,000 | How often to ping idle instances |
| `options` | `Options.defaults()` | `PythonEmbed.Options` for each instance |

## Pool Monitoring

```java
int size = pool.size();              // current instances
int active = pool.activeCount();     // instances currently in use
int min = pool.minPool();            // configured minimum
int max = pool.maxPool();            // configured maximum
```

## Auto-Scaling Behavior

- When `eval()`/`exec()` is called and no idle instance is available, the pool creates a new instance (up to `maxPool`)
- Idle instances beyond `minPool` are removed after `idleTimeoutMs`
- Unhealthy instances are automatically replaced (maintains `minPool` guarantee)
- Health checks ping each idle instance at `healthCheckIntervalMs`

## Streaming from Pool

```java
CompletableFuture<Iterator<PythonValue>> future = pool.stream("range(1_000_000)");
Iterator<PythonValue> stream = future.get();
while (stream.hasNext()) {
    PythonValue item = stream.next();
    // process lazily
}
```

The iterator holds its pool instance until the stream is exhausted or `close()` is called. The instance is automatically returned to the pool.

## Batch from Pool

```java
CompletableFuture<List<PythonValue>> batch = pool.batchEval(List.of(
    "x * 2",
    "x + 1",
    "x * x"
));
List<PythonValue> results = batch.get();
```
