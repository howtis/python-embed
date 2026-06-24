# Callback Bridge

Demonstrates Python-to-Java callbacks and push handlers. Python code can call Java methods via `_bridge.call("name", args...)` (synchronous) and `_bridge.push("name", value)` (fire-and-forget).

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/callback-bridge/src/main/java/io/github/howtis/pythonembed/examples/CallbackBridgeExample.java)

## Key Points

- `registerCallback(name, handler)` — synchronous Python→Java call
- Typed callbacks: `registerCallback(name, Integer.class, Integer.class, (a, b) -> a + b)`
- `registerPushHandler(name, handler)` — fire-and-forget push from Python
- Python side: `_bridge.call("name", args...)` and `_bridge.push("name", value)`

## Run It

```bash
./gradlew :python-embed-examples:callback-bridge:run
```
