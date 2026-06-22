# PythonEmbed

A Gradle plugin + runtime library that lets you run Python code from Java with a single line.

## Overview

Java library for embedding CPython in JVM applications. Uses a subprocess + MessagePack binary protocol for communication — just pure Java and CPython.

## Modules

- **python-embed-gradle-plugin** — Gradle plugin handling venv creation, package installation, and Python auto-download
- **python-embed-runtime** — Java runtime library for process communication, pool management, and type conversion

## Key Features

- **Zero setup**: One Gradle plugin line + package list
- **Full CPython compatibility**: All C extensions work (numpy, torch, scipy)
- **Crash isolation**: Python segfaults never kill the JVM
- **Binary protocol**: MessagePack with length-prefixed frames (2-5x faster than JSON)
- **Pool**: Auto-scaling PythonEmbedPool with async CompletableFuture API
- **Auto-restart**: minPool guarantee on crash
- **Health check**: Periodic ping/pong with WARNING-level logging
- **Micrometer metrics**: Pool stats, latency histograms, error rate counters

## License

MIT