# Streaming

Demonstrates Python generator streaming via `PythonEmbed.stream()`. Python generators are consumed lazily through a Java `Iterator`, avoiding memory explosion even for large datasets.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/streaming/src/main/java/io/github/howtis/pythonembed/examples/StreamingExample.java)

## Key Points

- `py.stream("generator_expr")` — returns `Iterator<PythonValue>`
- Each `next()` call resumes the generator and yields the next value
- Only one value in memory at a time — safe for millions of items
- Works with built-in `range()`, custom generators, and string generators

## Run It

```bash
./gradlew :python-embed-examples:streaming:run
```
