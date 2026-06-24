# Architecture

How PythonEmbed works under the hood.

## Overview

PythonEmbed uses a **subprocess + MessagePack** model:

```
┌─────────────┐     MessagePack frames      ┌──────────────────┐
│  JVM (Java) │ ◄──────────────────────────► │ CPython process  │
│             │     via stdin / stdout       │ (bridge.py)      │
└─────────────┘                              └──────────────────┘
```

Key design choices:

- **Subprocess, not embedded interpreter** — crash isolation and no native library conflicts
- **MessagePack over stdin/stdout** — simple, fast, debuggable, works with any CPython version
- **One Python process per PythonEmbed** — shared namespace, sequential-by-default, pool for concurrency

## Communication Protocol

All communication happens via **MessagePack** binary protocol over stdin/stdout:

1. Java serializes a request (eval/exec/ref/callback/etc.) as a MessagePack frame
2. The frame is prefixed with a 4-byte big-endian length header
3. Python's `bridge.py` reads the length, then the full frame
4. Python processes the request and writes a response frame back
5. Java reads the response, deserializes, and returns the result

### Why MessagePack?

| Format | Size (typical) | Speed | Python support |
|--------|---------------|-------|---------------|
| MessagePack | ~50 bytes | Fast (binary) | `msgpack` package |
| JSON | ~120 bytes | Medium | Built-in |
| Pickle | ~40 bytes | Fast | Built-in (unsafe) |

MessagePack is 2-5× faster than JSON for typical workloads and produces smaller payloads. Pickle is faster but not safe for cross-process communication.

## Process Lifecycle

### Startup

1. `PythonEmbed.create()` resolves the Python executable
2. Spawns `python bridge.py` as a subprocess
3. Bridge initializes: imports user-specified modules, runs warmup scripts
4. Sends a ready signal — PythonEmbed is now usable

### Runtime

- `eval()` / `exec()` → serialize request → write stdin → read stdout → deserialize → return
- Object handles (`ref()`) keep Python objects alive by ID
- Health checks (`ping()`) send periodic ping/pong messages

### Shutdown

1. `close()` sends an exit request to the Python process
2. Waits for graceful shutdown (configurable timeout)
3. If process doesn't exit, `hardShutdown()` destroys the process forcibly

## Handle System

Python objects referenced by `ref()` are tracked by numeric ID:

```
Java                           Python
────                           ──────
ref("np") ─────────────────►   np_id = 1
py.eval(... using np_id...)    use handles[1]
forgetHandle(handle) ──────►   del handles[1]
```

Handles are automatically released when the PythonEmbed is closed.

## Pool Architecture

`PythonEmbedPool` manages multiple `PythonEmbed` instances:

```
               ┌──────────────────┐
  eval() ────► │ PythonEmbed #1   │
  eval() ────► │ PythonEmbed #2   │
  eval() ────► │ PythonEmbed #3   │
               │ ... (up to max)  │
               └──────────────────┘
```

- **minPool** instances kept warm at all times
- **maxPool** upper bound on concurrent instances
- Idle instances above minPool are removed after idleTimeoutMs
- Health checks monitor each idle instance
- Failed instances are replaced automatically

### Concurrency Model

The pool is **thread-safe**. Each `eval()`/`exec()` call acquires an instance from the pool, uses it, and returns it. Instances are single-threaded (one request at a time) — concurrency comes from multiple instances.

## Crash Isolation

Since Python runs in a separate process:

- Python segfaults, infinite loops, or `os._exit()` never kill the JVM
- The pool detects dead processes and replaces them
- Each PythonEmbed has a timeout — hung requests are killed
