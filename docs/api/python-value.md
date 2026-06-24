# PythonValue API

`PythonValue` wraps a Python evaluation result and provides typed access methods. Returned by `eval()` and `batchEval()`.

## Type Conversion Methods

| Method | Returns | Python Type | Description |
|--------|---------|-------------|-------------|
| `asInt()` | `int` | `int` | Convert to Java int |
| `asLong()` | `long` | `int` | Convert to Java long |
| `asDouble()` | `double` | `float` / `int` | Convert to Java double |
| `asBoolean()` | `boolean` | `bool` | Convert to Java boolean |
| `asString()` | `String` | `str` | Convert to Java String |
| `asList()` | `List<Object>` | `list` | Convert to raw list |
| `asList(Class<T>)` | `List<T>` | `list` | Convert to typed list |
| `asMap()` | `Map<String, Object>` | `dict` | Convert to raw map |
| `asMap(Class<K>, Class<V>)` | `Map<K, V>` | `dict` | Convert to typed map |
| `asByteArray()` | `byte[]` | `bytes` | Convert to byte array |
| `toJson()` | `String` | any | JSON string representation |

## Usage Examples

```java
PythonValue val = py.eval("42");
int i = val.asInt();                    // 42
long l = val.asLong();                  // 42L
double d = val.asDouble();              // 42.0

PythonValue listVal = py.eval("[1, 2, 3]");
List<Integer> list = listVal.asList(Integer.class);  // [1, 2, 3]

PythonValue dictVal = py.eval("{'a': 1, 'b': 2}");
Map<String, Integer> map = dictVal.asMap(String.class, Integer.class);

PythonValue bytesVal = py.eval("b'hello'");
byte[] bytes = bytesVal.asByteArray();

// JSON serialization
String json = py.eval("{'key': [1, 2, 3]}").toJson();
```

## Notes

- `asList()` without type parameter returns `List<Object>` — use the typed variant when possible
- `asMap()` without type parameters returns `Map<String, Object>`
- `toJson()` serializes the Python value to JSON for logging or transmission
