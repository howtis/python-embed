# Examples

13 working examples demonstrating PythonEmbed features. Each example is a standalone subproject you can run directly.

## Running Examples

```bash
./gradlew :python-embed-examples:basic-eval:run
```

Or run all examples at once (CI):

```bash
./gradlew :python-embed-examples:runAllExamples
```

## Example List

| Example | What It Demonstrates |
|---------|---------------------|
| [Basic Eval](basic-eval.md) | `eval()`, `exec()`, `PythonValue`, `arg()` |
| [Batch Operations](batch-operations.md) | `batchEval()`, `batchExec()` — single + pool |
| [Callback Bridge](callback-bridge.md) | Python→Java callbacks via `_bridge.call()` |
| [Configuration](configuration.md) | `PythonEmbed.Options` builder, env vars, warmup |
| [Error Handling](error-handling.md) | `PythonExecutionException`, traceback, cause codes |
| [Health Monitor](health-monitor.md) | `ping()`, `health()`, pool monitoring |
| [Maven Example](maven-example.md) | Maven plugin setup, basic eval, numpy |
| [Numpy Basic](numpy-basic.md) | NumPy arrays, operations, broadcasting |
| [Object Handle](object-handle.md) | `ref()`, handle lifecycle, cross-call references |
| [Pandas Dataframe](pandas-dataframe.md) | DataFrame creation, compute, pass to Java |
| [Pool Async](pool-async.md) | `PythonEmbedPool`, async eval, concurrent execution |
| [Proxy Object](proxy-object.md) | `proxy()` — wrap Python objects as Java interfaces |
| [Spring Boot Example](spring-boot-example.md) | Spring Boot auto-configuration, REST endpoint |
| [Streaming](streaming.md) | Generator streaming via `stream()`, large datasets |
