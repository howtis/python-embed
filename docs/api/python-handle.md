# PythonHandle API

`PythonHandle` is a reference to a Python object kept alive in the Python process. Returned by `ref()` and usable across multiple `eval()`/`exec()` calls without re-serializing the object.

Implements `AutoCloseable`.

## Instance Methods

### Inspection

| Method | Returns | Description |
|--------|---------|-------------|
| `refId()` | `int` | The numeric handle ID in the Python process |
| `pythonType()` | `String` | The Python type name (e.g., `"numpy.ndarray"`) |

### Interaction

| Method | Returns | Description |
|--------|---------|-------------|
| `call(String method, Object... args)` | `PythonValue` | Call a method on the held object |
| `getAttr(String name)` | `PythonValue` | Read an attribute's value |

### Lifecycle

| Method | Description |
|--------|-------------|
| `release()` | Explicitly release this handle |
| `close()` | Same as `release()` (AutoCloseable) |

## Usage Examples

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("import numpy as np; arr = np.array([1.0, 2.0, 3.0, 4.0, 5.0])");

    PythonHandle handle = py.ref("arr");

    // Inspect
    System.out.println("ID: " + handle.refId());            // e.g. 1
    System.out.println("Type: " + handle.pythonType());      // "numpy.ndarray"

    // Read attributes
    PythonValue shape = handle.getAttr("shape");
    System.out.println(shape.asList(Integer.class));         // [5]

    // Call methods
    double mean = handle.call("mean").asDouble();            // 3.0
    double std = handle.call("std").asDouble();              // ~1.58

    // Explicit release (or use try-with-resources)
    handle.release();
}
```

## Notes

- Handles are automatically released when the owning `PythonEmbed` is closed
- Calling methods on a released handle throws an exception
- Use `forgetHandle()` on `PythonEmbed` to release without having the handle object
