# PythonEmbed

Run Python code from Java — with full CPython compatibility and zero setup.

PythonEmbed embeds a real CPython interpreter in your JVM application via a subprocess + [MessagePack](https://msgpack.org/) binary protocol — just pure Java and CPython. All C extensions (numpy, scipy, torch) work out of the box.

## Quick Look

Add the Gradle plugin:

```groovy
plugins {
    id 'io.github.howtis.python-embed' version '1.0.2'
}

pythonEmbed {
    packages = ['numpy']
}
```

Then run Python from Java:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("import numpy as np");
    int sum = py.eval("sum([1, 2, 3])").asInt();  // 6
}
```

## Next Steps

- **[Quickstart](quickstart.md)** — Get running in 5 minutes
- **[Installation](installation.md)** — Gradle plugin and runtime setup
- **[Usage Guides](usage/basics.md)** — eval, exec, pool, callbacks, streaming, and more
- **[Spring Boot](spring-boot.md)** — Zero-code auto-configuration
- **[Examples](examples/index.md)** — 13 real-world examples
- **[API Reference](api/python-embed.md)** — Full method documentation
- **[Architecture](architecture.md)** — How it works under the hood
