# Quickstart

Get PythonEmbed running in your project in 5 minutes.

## 1. Add the Gradle Plugin

Add to `build.gradle`:

```groovy
plugins {
    id 'io.github.howtis.python-embed' version '1.0.1'
}

dependencies {
    implementation 'io.github.howtis:python-embed-runtime:1.0.1'
}

pythonEmbed {
    packages = ['numpy', 'msgpack']
}
```

The plugin automatically downloads Python (if needed), creates a venv, and installs packages at build time.

## 2. Run Your First Python Code

```java
import io.github.howtis.pythonembed.PythonEmbed;

public class HelloPython {
    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            py.exec("import numpy as np");

            // Evaluate expressions
            int sum = py.eval("sum([1, 2, 3])").asInt();
            System.out.println("Sum: " + sum);  // 6

            // Execute statements
            py.exec("x = 42");
            int x = py.eval("x").asInt();
            System.out.println("x = " + x);  // 42

            // Work with numpy
            double mean = py.eval("np.mean([1, 2, 3, 4, 5])").asDouble();
            System.out.println("mean: " + mean);  // 3.0
        }
    }
}
```

## 3. Understand the Basics

- `eval()` — evaluate a Python expression, returns a `PythonValue`
- `exec()` — execute Python statements (no return value)
- `PythonValue` — typed access to Python results: `asInt()`, `asDouble()`, `asString()`, `asList()`, `asMap()`, etc.
- `arg()` — safely inject Java values into Python strings (no injection risk)

## Next Steps

- **[Installation](installation.md)** — Detailed setup and configuration
- **[Usage: Basics](usage/basics.md)** — `eval`, `exec`, `PythonValue`, safe parameter injection
- **[Usage: Pool](usage/pool.md)** — Concurrent execution with `PythonEmbedPool`
- **[Examples](examples/index.md)** — 13 working examples you can run
