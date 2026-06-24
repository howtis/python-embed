# Streaming

Stream Python generator/yield results lazily via Java `Iterator` — no memory explosion for large datasets.

## Basic Streaming

```java
try (PythonEmbed py = PythonEmbed.create()) {
    Iterator<PythonValue> stream = py.stream("range(1_000_000)");
    while (stream.hasNext()) {
        PythonValue item = stream.next();
        System.out.println(item.asInt());
    }
}
```

Each `next()` call resumes the generator and yields the next value. Only one value is in memory at a time.

## Custom Poll Timeout

Control how long to wait between stream iterations:

```java
// Wait up to 5 seconds between items (default: 30s)
Iterator<PythonValue> stream = py.stream("my_generator()", 5000);
```

## Complex Generator Example

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("""
        def fibonacci(n):
            a, b = 0, 1
            for _ in range(n):
                yield a
                a, b = b, a + b
    """);

    Iterator<PythonValue> fib = py.stream("fibonacci(10)");
    while (fib.hasNext()) {
        System.out.println(fib.next().asInt());
    }
    // 0 1 1 2 3 5 8 13 21 34
}
```

## Streaming with NumPy

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("import numpy as np");

    Iterator<PythonValue> batches = py.stream("""
        (batch.tolist() for batch in np.array_split(np.arange(1000), 10))
    """);

    while (batches.hasNext()) {
        List<Integer> batch = batches.next().asList(Integer.class);
        System.out.println("Batch: " + batch.size());
    }
}
```

## Important Notes

- The stream holds the PythonEmbed instance until the iterator is exhausted — don't close the embed early
- Stream results are typed via `PythonValue` like any eval result
- Generators that never finish will exhaust the timeout and throw an exception
