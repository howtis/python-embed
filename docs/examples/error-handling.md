# Error Handling

Demonstrates Python error handling with detailed diagnostic information. Every Python exception is wrapped as a `PythonExecutionException` providing error type, cause code, and full Python traceback.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/error-handling/src/main/java/io/github/howtis/pythonembed/examples/ErrorHandlingExample.java)

## Key Points

- `PythonExecutionException` wraps all Python errors
- `getPythonErrorType()` — `SyntaxError`, `NameError`, `ValueError`, etc.
- `getCauseCode()` — the Python code that caused the error
- `getPythonTraceback()` — full Python traceback string
- Works for both `eval()` and `exec()`

## Run It

```bash
./gradlew :python-embed-examples:error-handling:run
```
