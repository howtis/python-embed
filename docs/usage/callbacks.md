# Callbacks

Python code can call back into Java via `_bridge.call()` and push events via `_bridge.push()`.

## Callbacks — Python Calls Java

Register a callback, then call it from Python:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    // Register a callback — callable from Python via _bridge.call("my_func", args...)
    py.registerCallback("my_func", args -> {
        String input = (String) args[0];
        return "Java says: " + input.toUpperCase();
    });

    py.exec("""
        result = _bridge.call("my_func", "hello")
        print(result)  # Java says: HELLO
    """);
}
```

## Typed Callbacks

Type-safe callback registration with automatic argument conversion:

```java
// 1-argument callback
py.registerCallback("add_one", Integer.class, (a) -> a + 1);

// 2-argument callback
py.registerCallback("add", Integer.class, Integer.class, (a, b) -> a + b);

// 3-argument callback
py.registerCallback("concat", String.class, String.class, String.class,
    (a, b, c) -> a + b + c);
```

## Push Handlers — Python Pushes Events

Fire-and-forget push from Python to Java:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.registerPushHandler("progress", (name, value) -> {
        System.out.println("Progress: " + value + "%");
    });

    py.registerPushHandler("log", String.class, (name, message) -> {
        System.out.println("[Python] " + message);
    });

    py.exec("""
        for i in range(5):
            _bridge.push('progress', i * 20)
            _bridge.push('log', f'Step {i} done')
    """);
}
```

## Error Handling in Callbacks

Exceptions thrown in callbacks are forwarded to Python as `RuntimeError`:

```java
py.registerCallback("validate", args -> {
    String input = (String) args[0];
    if (input == null || input.isEmpty()) {
        throw new IllegalArgumentException("Input must not be empty");
    }
    return "OK";
});

// Python side:
// try:
//     _bridge.call("validate", "")
// except RuntimeError as e:
//     print(e)  # Input must not be empty
```
