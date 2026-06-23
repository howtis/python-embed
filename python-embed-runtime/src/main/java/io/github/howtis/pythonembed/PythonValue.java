package io.github.howtis.pythonembed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-safe wrapper for values returned from Python eval().
 * Internally holds the raw MessagePack-deserialized object and provides
 * typed accessors with appropriate conversions.
 */
public class PythonValue {

    private final Object raw;

    PythonValue(Object raw) {
        this.raw = raw;
    }

    /** Returns the raw Java object from MessagePack deserialization. */
    public Object raw() {
        return raw;
    }

    /**
     * Returns true if the Python result is {@code None}.
     * All typed accessors will throw when called on a null value.
     */
    public boolean isNull() {
        return raw == null;
    }

    /** Converts to int. Handles any Number type. */
    public int asInt() {
        checkNotNull("int");
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s) {
            return Integer.parseInt(s);
        }
        throw new ClassCastException("Cannot convert " + className() + " to int");
    }

    /** Converts to long. */
    public long asLong() {
        checkNotNull("long");
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            return Long.parseLong(s);
        }
        throw new ClassCastException("Cannot convert " + className() + " to long");
    }

    /** Converts to double. */
    public double asDouble() {
        checkNotNull("double");
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s) {
            return Double.parseDouble(s);
        }
        throw new ClassCastException("Cannot convert " + className() + " to double");
    }

    /** Converts to boolean. */
    public boolean asBoolean() {
        checkNotNull("boolean");
        if (raw instanceof Boolean b) {
            return b;
        }
        throw new ClassCastException("Cannot convert " + className() + " to boolean");
    }

    /** Converts to String. */
    public String asString() {
        if (raw == null) {
            return "null";
        }
        return raw.toString();
    }

    /** Converts a list with typed elements. */
    @SuppressWarnings("unchecked")
    public <T> List<T> asList(Class<T> type) {
        checkNotNull("List");
        if (!( raw instanceof List<?> rawList )) {
            throw new ClassCastException("Cannot convert " + className() + " to List");
        }
        if (type == Object.class) {
            return (List<T>) rawList;
        }
        List<T> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            result.add(convertValue(item, type));
        }
        return result;
    }

    /**
     * Returns the map entries as {@code Map<String, Double>}.
     * Each value is converted via {@link #asDouble()} semantics
     * (Number -&gt; doubleValue).
     */
    public Map<String, Double> asDoubleMap() {
        return asMap(String.class, Double.class);
    }

    /**
     * Returns the map entries as {@code Map<String, String>}.
     * Each value is converted via {@link Object#toString()}.
     */
    public Map<String, String> asStringMap() {
        return asMap(String.class, String.class);
    }

    /**
     * Returns the map entries as {@code Map<String, Integer>}.
     * Each value is converted via {@link Number#intValue()}.
     */
    public Map<String, Integer> asIntMap() {
        return asMap(String.class, Integer.class);
    }

    /**
     * Returns the map entries as {@code Map<String, Long>}.
     * Each value is converted via {@link Number#longValue()}.
     */
    public Map<String, Long> asLongMap() {
        return asMap(String.class, Long.class);
    }

    /** Converts a map with typed keys and values. */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> asMap(Class<K> keyType, Class<V> valueType) {
        checkNotNull("Map");
        if (!( raw instanceof Map<?, ?> rawMap )) {
            throw new ClassCastException("Cannot convert " + className() + " to Map");
        }
        if (keyType == Object.class && valueType == Object.class) {
            return (Map<K, V>) rawMap;
        }
        Map<K, V> result = new HashMap<>(rawMap.size());
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(convertValue(entry.getKey(), keyType),
                       convertValue(entry.getValue(), valueType));
        }
        return result;
    }

    /** Returns the raw list without type conversion. */
    @SuppressWarnings("unchecked")
    public List<Object> asList() {
        checkNotNull("List");
        if (!(raw instanceof List)) {
            throw new ClassCastException("Cannot convert " + className() + " to List");
        }
        return (List<Object>) raw;
    }

    /** Returns the raw map without type conversion. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap() {
        checkNotNull("Map");
        if (!(raw instanceof Map)) {
            throw new ClassCastException("Cannot convert " + className() + " to Map");
        }
        return (Map<String, Object>) raw;
    }

    /**
     * Converts to byte[]. Handles byte[] from MessagePack binary data
     * and Base64-encoded strings for backward compatibility.
     */
    public byte[] asBytes() {
        checkNotNull("byte[]");
        if (raw instanceof byte[] b) {
            return b;
        }
        if (raw instanceof String s) {
            return java.util.Base64.getDecoder().decode(s);
        }
        throw new ClassCastException("Cannot convert " + className() + " to byte[]");
    }

    /**
     * Serializes the Python value to a JSON string using Java-side
     * recursive serialization. This is a convenience method equivalent
     * to {@code toJson(false)}.
     *
     * <p>The raw MessagePack-deserialized Java object is serialized
     * directly, avoiding a Python round-trip.
     *
     * @return JSON representation of the Python value
     * @throws PythonExecutionException if the value contains
     *         non-JSON-serializable types or circular references
     */
    public String toJson() {
        return toJson(false);
    }

    /**
     * Serializes the Python value to a JSON string.
     *
     * <p>The raw MessagePack-deserialized Java object is serialized
     * directly with optional pretty-printing.
     *
     * @param prettyPrint if {@code true}, the output is indented with 2 spaces
     * @return JSON representation of the Python value
     * @throws PythonExecutionException if the value contains
     *         non-JSON-serializable types (e.g., bytes) or circular references
     */
    public String toJson(boolean prettyPrint) {
        StringBuilder sb = new StringBuilder();
        try {
            toJsonValue(raw, sb, prettyPrint, 0, new IdentityHashMap<>());
        } catch (PythonExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw PythonExecutionException.wrap("toJson", e);
        }
        if (prettyPrint) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void toJsonValue(Object value, StringBuilder sb, boolean pretty,
                                     int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof Boolean) {
            sb.append((Boolean) value ? "true" : "false");
            return;
        }
        if (value instanceof Number n) {
            if (n instanceof Double d) {
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    sb.append("null");
                    return;
                }
            } else if (n instanceof Float f) {
                if (Float.isNaN(f) || Float.isInfinite(f)) {
                    sb.append("null");
                    return;
                }
            }
            sb.append(n);
            return;
        }
        if (value instanceof String s) {
            appendJsonString(sb, s);
            return;
        }
        if (value instanceof byte[]) {
            throw new PythonExecutionException(
                    "Cannot serialize bytes to JSON. " +
                    "Use asBytes() to extract the raw bytes.");
        }
        if (value instanceof List<?> list) {
            if (!seen.containsKey(value)) {
                seen.put(value, Boolean.TRUE);
            } else {
                throw new PythonExecutionException(
                        "Cannot serialize circular reference to JSON");
            }
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                    sb.append(' ');
                }
                first = false;
                if (pretty) {
                    sb.append('\n');
                    indent(sb, depth + 1);
                }
                toJsonValue(item, sb, pretty, depth + 1, seen);
            }
            if (pretty && !list.isEmpty()) {
                sb.append('\n');
                indent(sb, depth);
            }
            sb.append(']');
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (!seen.containsKey(value)) {
                seen.put(value, Boolean.TRUE);
            } else {
                throw new PythonExecutionException(
                        "Cannot serialize circular reference to JSON");
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                    sb.append(' ');
                }
                first = false;
                if (pretty) {
                    sb.append('\n');
                    indent(sb, depth + 1);
                }
                Object key = entry.getKey();
                if (key instanceof String ks) {
                    appendJsonString(sb, ks);
                } else {
                    appendJsonString(sb, String.valueOf(key));
                }
                sb.append(':');
                sb.append(' ');
                toJsonValue(entry.getValue(), sb, pretty, depth + 1, seen);
            }
            if (pretty && !map.isEmpty()) {
                sb.append('\n');
                indent(sb, depth);
            }
            sb.append('}');
            return;
        }
        throw new PythonExecutionException(
                "Cannot serialize " + value.getClass().getName() + " to JSON");
    }

    private static void appendJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
    }

    @Override
    public String toString() {
        return "PythonValue{" + raw + "}";
    }

    private void checkNotNull(String targetType) {
        if (raw == null) {
            throw new IllegalStateException(
                    "Python returned None, cannot convert to " + targetType + ". " +
                    "Use isNull() to check before converting.");
        }
    }

    private String className() {
        return raw == null ? "null" : raw.getClass().getName();
    }

    /**
     * Creates a PythonValue from a raw object (used internally by the protocol layer).
     */
    static PythonValue of(Object raw) {
        return new PythonValue(raw);
    }

    /**
     * Converts a single value to the target type.
     */
    @SuppressWarnings("unchecked")
    static <T> T convertValue(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        if (targetType == Double.class && value instanceof Number) {
            return targetType.cast(((Number) value).doubleValue());
        }
        if (targetType == Float.class && value instanceof Number) {
            return targetType.cast(((Number) value).floatValue());
        }
        if (targetType == Long.class && value instanceof Number) {
            return targetType.cast(((Number) value).longValue());
        }
        if (targetType == Integer.class && value instanceof Number) {
            return targetType.cast(((Number) value).intValue());
        }
        if (targetType == Short.class && value instanceof Number) {
            return targetType.cast(((Number) value).shortValue());
        }
        if (targetType == Byte.class && value instanceof Number) {
            return targetType.cast(((Number) value).byteValue());
        }
        if (targetType == String.class) {
            return targetType.cast(value.toString());
        }
        if (targetType == LocalDateTime.class) {
            if (value instanceof String s) {
                return targetType.cast(LocalDateTime.parse(s));
            }
        }
        if (targetType == LocalDate.class) {
            if (value instanceof String s) {
                return targetType.cast(LocalDate.parse(s));
            }
        }
        if (targetType == LocalTime.class) {
            if (value instanceof String s) {
                return targetType.cast(LocalTime.parse(s));
            }
        }
        if (targetType == ZonedDateTime.class) {
            if (value instanceof String s) {
                return targetType.cast(ZonedDateTime.parse(s));
            }
        }
        if (targetType == Instant.class) {
            if (value instanceof String s) {
                return targetType.cast(Instant.parse(s));
            }
            if (value instanceof Number n) {
                return targetType.cast(Instant.ofEpochMilli(n.longValue()));
            }
        }
        throw new ClassCastException(
                "Cannot convert " + value.getClass().getName() + " to " + targetType.getName());
    }
}
