# Basics

Core usage patterns: `eval`, `exec`, `PythonValue`, and safe parameter injection.

## eval — Evaluate Expressions

`eval()` evaluates a Python expression and returns a `PythonValue`:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    PythonValue result = py.eval("sum([1, 2, 3])");
    int sum = result.asInt();          // 6
    double pi = py.eval("3.14 * 2").asDouble();  // 6.28
    boolean t = py.eval("1 + 1 == 2").asBoolean(); // true
}
```

## exec — Execute Statements

`exec()` executes Python statements in a shared namespace. No return value:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("x = 42");
    py.exec("""
        def greet(name):
            return f"Hello, {name}!"
    """);

    int x = py.eval("x").asInt();         // 42
    String g = py.eval("greet('World')").asString(); // "Hello, World!"
}
```

## execFile — Execute Python Files

`execFile(Path)` runs a Python script file directly:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.execFile(Path.of("scripts/data_pipeline.py"));
    PythonValue result = py.eval("processed_data");
}
```

## PythonValue — Typed Access

| Method | Returns | Example |
|--------|---------|---------|
| `asInt()` | `int` | `py.eval("42").asInt()` |
| `asLong()` | `long` | `py.eval("2**40").asLong()` |
| `asDouble()` | `double` | `py.eval("3.14").asDouble()` |
| `asBoolean()` | `boolean` | `py.eval("True").asBoolean()` |
| `asString()` | `String` | `py.eval("'hi'").asString()` |
| `asList(Class)` | `List<T>` | `py.eval("[1,2,3]").asList(Integer.class)` |
| `asMap(K,V)` | `Map<K,V>` | `py.eval("{'a':1}").asMap(String.class, Integer.class)` |
| `asByteArray()` | `byte[]` | `py.eval("b'\\x01\\x02'").asByteArray()` |
| `toJson()` | `String` | JSON representation |

## arg() — Safe Parameter Injection

`PythonEmbed.arg()` converts Java values to Python literals with injection protection:

```java
PythonEmbed.arg(null);            // None
PythonEmbed.arg("hello");         // 'hello'
PythonEmbed.arg(42);              // 42
PythonEmbed.arg(true);            // True
PythonEmbed.arg(3.14);            // 3.14
PythonEmbed.arg(List.of(1, 2));   // [1, 2]
PythonEmbed.arg(Set.of(1, 2));    // {1, 2}
PythonEmbed.arg(Map.of("k", 1));  // {'k': 1}
PythonEmbed.arg(new byte[]{1,2}); // b'\x01\x02'

// Safe — user input can't break out of the string literal:
String userInput = "Bob'; import os; os.system('rm -rf /') #";
py.eval("len(" + PythonEmbed.arg(userInput) + ")");
```

## Variables — Java to Python

Pass Java variables into Python evaluations:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.eval(Map.of("a", 10, "b", 20), "a + b");  // 30

    py.exec(Map.of("name", "Alice", "age", 30), """
        greeting = f"{name} is {age} years old"
    """);
}
```
