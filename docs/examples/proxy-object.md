# Proxy Object

Demonstrates Java dynamic proxies for Python objects — wrapping Python objects as Java interfaces with automatic camelCase→snake_case method name conversion.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/proxy-object/src/main/java/io/github/howtis/pythonembed/examples/ProxyObjectExample.java)

## Key Points

- `py.proxy("varName", Interface.class)` — wrap a Python object as a Java interface
- Method calls are transparent — Java method → Python method
- Automatic camelCase→snake_case conversion (`getDataFrame` → `get_data_frame`)
- Proxy objects hold handles that are auto-released on close
- Works with both `PythonEmbed` and `PythonEmbedPool`

## Run It

```bash
./gradlew :python-embed-examples:proxy-object:run
```
