# Pandas DataFrame

Demonstrates Pandas DataFrame integration — creating, transforming, and analyzing tabular data via Python from Java. Pandas and all its C extensions work natively with no configuration beyond listing it in the Gradle plugin's `packages` block.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/pandas-dataframe/src/main/java/io/github/howtis/pythonembed/examples/PandasDataframeExample.java)

## Key Points

- DataFrame creation, inspection, filtering, aggregation, sorting
- Group-by and statistical summary (`describe()`)
- Java↔DataFrame round-trip using `PythonEmbed.arg()`
- Real CPython → all Pandas C extensions work natively
- Results returned as typed `List<Map<String, Object>>`

## Run It

```bash
./gradlew :python-embed-examples:pandas-dataframe:run
```
