# Object Handle

Demonstrates Python object handles — keeping Python objects in-process and referencing them by numeric ID across calls. Handles avoid serializing the entire object on every call.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/object-handle/src/main/java/io/github/howtis/pythonembed/examples/ObjectHandleExample.java)

## Key Points

- `ref()` returns a `PythonHandle` without serializing the object
- `handle.call("method", args...)` — invoke methods on the held object
- `handle.getAttr("name")` — read attributes
- `handle.refId()` / `handle.pythonType()` — inspect the handle
- `handle.release()` / `handle.close()` — explicit release (AutoCloseable)
- Handles are automatically released when PythonEmbed is closed

## Run It

```bash
./gradlew :python-embed-examples:object-handle:run
```
