# PythonEmbed


[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Run Python code from Java — with full CPython compatibility and zero setup.

PythonEmbed embeds a real CPython interpreter in your JVM application via a subprocess + [MessagePack](https://msgpack.org/) binary protocol — just pure Java and CPython. All C extensions (numpy, scipy, torch) work out of the box.

## Quick Start

Add the Gradle plugin to `build.gradle`:

```groovy
plugins {
    id 'io.github.howtis.python-embed' version '1.0.1'
}

pythonEmbed {
    packages = ['numpy', 'msgpack']
}
```

Then use it in your Java code:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("import numpy as np");
    int sum = py.eval("sum([1, 2, 3])").asInt();
    System.out.println(sum);  // 6
}
```

That's it. The Gradle plugin handles Python installation, venv creation, and package installation at build time.

## Features

- **Full CPython compatibility** — numpy, scipy, torch, matplotlib, and all C extensions work
- **Zero setup** — one Gradle plugin line + a package list, no manual Python installation needed
- **Crash isolation** — Python segfaults never kill the JVM; each Python process runs independently
- **Binary protocol** — MessagePack with length-prefixed frames
- **Auto-scaling pool** — `PythonEmbedPool` with async `CompletableFuture` API, scales between `minPool` and `maxPool`
- **Object handles** — keep Python objects in-process, reference by numeric ID across calls via `ref()`
- **Java proxies** — wrap Python objects as Java interfaces via dynamic proxies with automatic camelCase→snake_case conversion
- **Callbacks** — Python-to-Java callbacks via `_bridge.call()` and fire-and-forget pushes via `_bridge.push()`
- **Streaming** — generator/yield results via Java `Iterator`
- **Type-safe arguments** — `PythonEmbed.arg()` converts Java types (null, Boolean, Number, String, List, Set, Map, byte[], datetime) to Python literals with injection protection
- **Batch execution** — `batchEval`/`batchExec` send multiple requests in a single round-trip
- **File execution** — `execFile(Path)` executes Python files directly
- **Auto-restart** — `minPool` guarantee on crash; unhealthy instances are replaced automatically
- **Health check** — periodic ping/pong with RSS memory, ref count, and GC status reporting via `health()` and `ping()`
- **Python auto-download** — [python-build-standalone](https://github.com/astral-sh/python-build-standalone) when system Python is absent
- **Incremental venv** — rebuild only on dependency changes
- **Python log forwarding** — Python `logging` routed to SLF4J via `python.*` logger namespace
- **Close hooks** — `onBeforeClose` / `onAfterClose` callbacks for resource cleanup
- **Spring Boot integration** — zero-code auto-configuration with SINGLE/POOL modes and Actuator `HealthIndicator`

## Installation

### Gradle

```groovy
plugins {
    id 'io.github.howtis.python-embed' version '1.0.1'
}

dependencies {
    implementation 'io.github.howtis:python-embed-runtime:1.0.1'
}
```

### Gradle Plugin Configuration

The `pythonEmbed` extension controls Python environment setup at build time:

```groovy
pythonEmbed {
    packages = [
        'numpy',
        'torch',
        'msgpack'
    ]
    requirements = file('requirements.txt')  // alternative to packages
}
```

**How it works:**
1. Checks for `python3 --version` (system Python)
2. If found → `python -m venv` + `pip install` packages
3. If not found → downloads [python-build-standalone](https://github.com/astral-sh/python-build-standalone) (~50 MB, cached) and installs packages directly
4. Packages the venv as a JAR resource for runtime extraction

## Usage

### Basic Evaluation & Execution

```java
// Create with default options
try (PythonEmbed py = PythonEmbed.create()) {
    // Evaluate expressions — returns PythonValue
    PythonValue result = py.eval("sum([1, 2, 3])");
    int sum = result.asInt();

    // Execute statements — shared namespace
    py.exec("x = 42");
    py.exec("""
        def greet(name):
            return f"Hello, {name}!"
    """);

    String greeting = py.eval("greet('World')").asString();
}
```

### Execute Python Files

```java
try (PythonEmbed py = PythonEmbed.create()) {
    // Execute a Python script file directly
    py.execFile(Path.of("scripts/data_pipeline.py"));

    // Access variables defined by the script
    PythonValue result = py.eval("processed_data");
}
```

### Variables & Object Handles

```java
try (PythonEmbed py = PythonEmbed.create()) {
    // Pass Java variables to Python
    py.eval(Map.of("a", 10, "b", 20), "a + b");  // 30

    // Get a persistent handle to a Python object
    PythonHandle handle = py.ref("np");

    // Use the handle later (survives across eval/exec calls)
    PythonValue arr = py.eval("np.arange(5).tolist()");
}
```

### Safe Parameter Injection

Use `PythonEmbed.arg()` to safely inject Java values into Python code — no risk of injection:

```java
String userInput = "Bob'; import os; os.system('rm -rf /') #";
py.eval("len(" + PythonEmbed.arg(userInput) + ")");  // Safe!

PythonEmbed.arg(null);           // None
PythonEmbed.arg("hello");        // 'hello'
PythonEmbed.arg(42);             // 42
PythonEmbed.arg(true);           // True
PythonEmbed.arg(List.of(1, 2));  // [1, 2]
PythonEmbed.arg(Set.of(1, 2));   // {1, 2}
PythonEmbed.arg(Map.of("k", 1)); // {'k': 1}
PythonEmbed.arg(new byte[]{1,2});// b'\x01\x02'
PythonEmbed.arg(Instant.now());  // datetime.datetime.fromtimestamp(...)
```

### Configuration

```java
PythonEmbed.Options options = PythonEmbed.Options.builder()
    .timeoutMs(60_000)           // per-request timeout (default: 30s)
    .startupTimeoutMs(10_000)    // process startup timeout
    .venvPath(Path.of("/opt/venv"))  // explicit venv path
    .env(Map.of("CUDA_VISIBLE_DEVICES", "0"))
    .warmupScript("import numpy as np")
    .onBeforeClose(py -> py.exec("cleanup()"))
    .build();

try (PythonEmbed py = PythonEmbed.create(options)) {
    // ready with numpy pre-imported
}
```

### Pool (Concurrent Execution)

```java
try (PythonEmbedPool pool = PythonEmbedPool.builder()
        .minPool(2)
        .maxPool(8)
        .idleTimeoutMs(30_000)
        .options(PythonEmbed.Options.defaults())
        .build()) {

    // Async evaluation
    CompletableFuture<PythonValue> f1 = pool.eval("sum([1, 2, 3])");
    CompletableFuture<PythonValue> f2 = pool.eval("np.arange(5).tolist()");

    // Wait for results
    int sum = f1.get().asInt();
    List<Object> arr = f2.get().asList();

    // Batch execution
    pool.batchEval(List.of("x * 2", "x + 1")).thenAccept(results -> {
        System.out.println(results.get(0).asInt());
        System.out.println(results.get(1).asInt());
    });

    // Batch exec (fire-and-forget)
    pool.batchExec(List.of(
        "print('task 1')",
        "print('task 2')"
    )).get();

    // Monitor pool
    System.out.println("Size: " + pool.size());
    System.out.println("Active: " + pool.activeCount());
}
```

### Python-to-Java Callbacks

```java
try (PythonEmbed py = PythonEmbed.create()) {
    // Register a callback — callable from Python via _bridge.call("my_func", args...)
    py.registerCallback("my_func", args -> {
        String input = (String) args[0];
        return "Java says: " + input.toUpperCase();
    });

    // Typed callback
    py.registerCallback("add", Integer.class, Integer.class, (a, b) -> a + b);

    py.exec("""
        result = _bridge.call("my_func", "hello")
        print(result)  # Java says: HELLO
    """);

    // Fire-and-forget push
    py.registerPushHandler("progress", (name, value) -> {
        System.out.println("Progress: " + value);
    });

    py.exec("_bridge.push('progress', 0.5)");
}
```

### Java Proxies for Python Objects

```java
try (PythonEmbed py = PythonEmbed.create()) {
    // Define a Python class
    py.exec("""
        class Calculator:
            def add(self, a, b):
                return a + b
            def multiply(self, a, b):
                return a * b
        calc = Calculator()
    """);

    // Wrap as a Java interface
    interface Calculator {
        int add(int a, int b);
        int multiply(int a, int b);
    }

    Calculator calc = py.proxy("calc", Calculator.class);
    int result = calc.add(3, 4);  // 7 — calls calc.add(3, 4) in Python
}
```

Method names are automatically converted from Java camelCase to Python snake_case when no exact match exists.

### Streaming (Generators)

```java
try (PythonEmbed py = PythonEmbed.create()) {
    Iterator<PythonValue> stream = py.stream("range(1_000_000)");
    while (stream.hasNext()) {
        PythonValue item = stream.next();
        // process each item lazily — no memory explosion
    }
}
```

### Error Handling

```java
try {
    py.eval("1 / 0");
} catch (PythonExecutionException e) {
    System.out.println("Python error: " + e.getMessage());
    System.out.println("Cause code: " + e.getCauseCode());
    System.out.println("Error type: " + e.getPythonErrorType());
    System.out.println("Traceback:\n" + e.getPythonTraceback());
}
```

## Spring Boot

Add `python-embed-spring-boot-starter` for zero-code Spring Boot 3.x integration:

```groovy
dependencies {
    implementation 'io.github.howtis:python-embed-spring-boot-starter:1.0.1'
}
```

Configure via `application.yml`:

```yaml
python-embed:
  mode: SINGLE          # or POOL
  venv-path: /opt/venv  # optional override
  pool:
    min: 2
    max: 8
    idle-timeout: 60s
    health-check-interval: 30s
    close-timeout: 30s
  options:
    timeout-ms: 30000
    environment-vars:
      CUDA_VISIBLE_DEVICES: "0"

management:
  endpoint:
    health:
      show-details: always
```

**SINGLE mode** injects a `PythonEmbed` bean. **POOL mode** injects a `PythonEmbedPool` bean. Both modes register an Actuator `HealthIndicator`.

```java
@RestController
public class PythonController {
    private final PythonEmbed py;

    public PythonController(PythonEmbed py) {
        this.py = py;
    }

    @GetMapping("/eval")
    public Map<String, Object> eval(@RequestParam String expr) {
        return Map.of("result", py.eval(expr).toJson());
    }
}
```

## Architecture

**Key design decisions:**
- **Subprocess, not embedded interpreter** — crash isolation and no native library conflicts
- **MessagePack over stdin/stdout** — simple, fast, debuggable, works with any CPython version
- **One Python process per PythonEmbed** — shared namespace, sequential-by-default, pool for concurrency

## Modules

- **[python-embed-gradle-plugin](python-embed-gradle-plugin/)** — Gradle plugin for venv creation, package installation, and Python auto-download
- **[python-embed-runtime](python-embed-runtime/)** — Java runtime library for process communication, pool management, and type conversion
- **[python-embed-spring-boot-starter](python-embed-spring-boot-starter/)** — Spring Boot 3.x auto-configuration (SINGLE/POOL modes, HealthIndicator)
- **[python-embed-examples](python-embed-examples/)** — 13 real-world examples (eval, numpy, pool-async, callback-bridge, proxy-object, streaming, spring-boot, and more)

## Building from Source

```bash
./gradlew build
```

Requirements:
- JDK 17+
- Python 3.8+ (or python-build-standalone will be downloaded automatically)

## License

MIT — see [LICENSE](LICENSE) for details.