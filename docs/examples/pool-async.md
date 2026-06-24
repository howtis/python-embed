# Pool Async

Demonstrates `PythonEmbedPool` — auto-scaling pool with async `CompletableFuture` API. Shows pool builder configuration, async eval/exec/batchEval, pool monitoring, and parallel execution.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/pool-async/src/main/java/io/github/howtis/pythonembed/examples/PoolAsyncExample.java)

## Key Points

- `PythonEmbedPool.builder()` fluent configuration: min/max pool, idle timeout
- Async API: all methods return `CompletableFuture`
- `pool.eval()` / `pool.exec()` — async evaluation/execution
- `pool.batchEval()` — async batch evaluation
- Pool monitoring: `size()`, `activeCount()`, `minPool()`, `maxPool()`
- Parallel execution — multiple concurrent requests handled simultaneously

## Run It

```bash
./gradlew :python-embed-examples:pool-async:run
```
