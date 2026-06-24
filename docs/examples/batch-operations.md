# Batch Operations

Demonstrates batch evaluation and execution — sending multiple requests in a single round-trip to reduce protocol overhead. Both `PythonEmbed` (single instance) and `PythonEmbedPool` support batch operations.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/batch-operations/src/main/java/io/github/howtis/pythonembed/examples/BatchOperationsExample.java)

## Key Points

- `batchEval()` evaluates multiple expressions in one MessagePack frame
- `batchExec()` executes multiple statements in one round-trip
- Pool batch operations return `CompletableFuture<List<PythonValue>>`
- Timeout applies to the entire batch, not per-item
- 10-20× faster than sequential `eval()` for many small operations

## Run It

```bash
./gradlew :python-embed-examples:batch-operations:run
```
