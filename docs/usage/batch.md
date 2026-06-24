# Batch Operations

Send multiple eval/exec requests in a single round-trip for maximum throughput.

## batchEval

Evaluate multiple expressions in one request:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    List<PythonValue> results = py.batchEval(List.of(
        "sum([1, 2, 3])",
        "max(10, 20, 30)",
        "'hello'.upper()"
    ));

    System.out.println(results.get(0).asInt());    // 6
    System.out.println(results.get(1).asInt());    // 30
    System.out.println(results.get(2).asString()); // "HELLO"
}
```

## batchExec

Execute multiple statements in one request:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.batchExec(List.of(
        "x = 10",
        "y = 20",
        "z = x + y"
    ));

    int z = py.eval("z").asInt();  // 30
}
```

## Why Batch?

Each `eval()`/`exec()` call involves:
1. Serialize request (MessagePack)
2. Write to stdin (flush)
3. Read from stdout
4. Deserialize response (MessagePack)

Batch operations combine all requests into one MessagePack frame, eliminating N-1 round-trips.

## Batch with Pool

```java
try (PythonEmbedPool pool = PythonEmbedPool.builder()
        .minPool(2).maxPool(8).options(PythonEmbed.Options.defaults())
        .build()) {

    CompletableFuture<List<PythonValue>> batch = pool.batchEval(List.of(
        "x * 2",
        "x + 1",
        "x * x"
    ));

    List<PythonValue> results = batch.get();
}
```

Pool batch operations pick an available instance and execute the batch on it.

## Timeouts

Default timeout is shared across the entire batch:

```java
// Wait up to 60 seconds for the whole batch
List<PythonValue> results = py.batchEval(codes, 60_000);
```
