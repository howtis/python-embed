# PythonEmbed

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Run Python code from Java — with full CPython compatibility and zero setup.

PythonEmbed embeds a real CPython interpreter in your JVM application via a subprocess + [MessagePack](https://msgpack.org/) binary protocol — just pure Java and CPython. All C extensions (numpy, scipy, torch) work out of the box.

> :books: **Full documentation** at [howtis.github.io/python-embed](https://howtis.github.io/python-embed)

## Quick Start

Add the Gradle plugin to `build.gradle`:

```groovy
plugins {
    id 'io.github.howtis.python-embed' version '1.0.2'
}

pythonEmbed {
    packages = ['numpy']
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

The Gradle plugin handles Python installation, venv creation, and package installation at build time.

## Key Features

| Category | Highlights                                                        |
|----------|-------------------------------------------------------------------|
| **Compatibility** | Full CPython — numpy, scipy, torch, matplotlib, all C extensions  |
| **Setup** | Zero manual steps — Gradle plugin auto-downloads Python if needed |
| **Safety** | Subprocess isolation — Python crashes never kill the JVM          |
| **Performance** | MessagePack binary protocol                                       |
| **Concurrency** | Auto-scaling pool with async `CompletableFuture` API              |
| **Integration** | Spring Boot auto-configuration, Actuator `HealthIndicator`        |
| **Interop** | Object handles, Java proxies, callbacks, streaming, batch ops     |
| **Observability** | Health checks, SLF4J log forwarding, close hooks                  |

[:books: Full documentation & API reference →](https://howtis.github.io/python-embed)

## Modules

- **[python-embed-gradle-plugin](python-embed-gradle-plugin/)** — venv creation, package installation, Python auto-download
- **[python-embed-runtime](python-embed-runtime/)** — process communication, pool management, type conversion
- **[python-embed-spring-boot-starter](python-embed-spring-boot-starter/)** — Spring Boot 3.x auto-configuration (SINGLE/POOL modes)
- **[python-embed-examples](python-embed-examples/)** — 13 real-world examples

## Building

```bash
./gradlew build
```

Requirements: JDK 17+, Python 3.8+ (auto-downloaded if absent).

## License

MIT — see [LICENSE](LICENSE) for details.