# Basic Eval

Demonstrates basic `PythonEmbed` usage: `eval()`, `exec()`, `PythonValue` type conversion, and safe parameter injection with `arg()`.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/basic-eval/src/main/java/io/github/howtis/pythonembed/examples/BasicEvalExample.java)

## Key Points

- `eval()` evaluates expressions and returns `PythonValue`
- `exec()` executes statements in a shared namespace
- `PythonValue` provides typed access: `asInt()`, `asDouble()`, `asString()`, `asList()`, `asMap()`
- `PythonEmbed.arg()` safely converts Java values to Python literals — no injection risk
- PythonEmbed is `AutoCloseable` — always use try-with-resources

## Run It

```bash
./gradlew :python-embed-examples:basic-eval:run
```
