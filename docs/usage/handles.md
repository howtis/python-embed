# Object Handles

Object handles keep Python objects alive in-process and reference them by numeric ID across calls.

## ref() — Get a Handle

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("import numpy as np");

    // Get a persistent handle to a Python object
    PythonHandle npHandle = py.ref("np");
    PythonHandle arrHandle = py.ref("np.array([1, 2, 3])");

    // Handles survive across eval/exec calls
    PythonValue result = py.eval("np.__version__");
    System.out.println(result.asString());
}
```

## Java Proxies — Wrap Python Objects as Java Interfaces

Define a Java interface and wrap a Python object with it:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("""
        class Calculator:
            def add(self, a, b):
                return a + b
            def multiply(self, a, b):
                return a * b
        calc = Calculator()
    """);

    // Define a matching Java interface
    interface Calculator {
        int add(int a, int b);
        int multiply(int a, int b);
    }

    // Wrap the Python object
    Calculator calc = py.proxy("calc", Calculator.class);

    // Call Python methods from Java — transparent!
    int result = calc.add(3, 4);        // 7
    int product = calc.multiply(5, 6);  // 30
}
```

### Method Name Conversion

Java camelCase method names are automatically converted to Python snake_case when no exact match exists on the Python object. `getDataFrame()` → `get_data_frame()`.

### Proxy from Pool

```java
CompletableFuture<PythonHandle> handleFuture = pool.ref("np");
PythonHandle handle = handleFuture.get();
Calculator calc = pool.proxy(handle, Calculator.class);
```

## Handle Lifecycle

Handles are automatically released when:
- The owning `PythonEmbed` is closed
- `forgetHandle(handle)` is explicitly called

```java
py.forgetHandle(handle);  // release the Python object
```
