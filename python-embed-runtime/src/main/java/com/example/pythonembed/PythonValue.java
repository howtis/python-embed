package com.example.pythonembed;

import java.util.ArrayList;
import java.util.HashMap;
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
        if (!(raw instanceof List)) {
            throw new ClassCastException("Cannot convert " + className() + " to List");
        }
        List<?> rawList = (List<?>) raw;
        if (type == Object.class) {
            return (List<T>) rawList;
        }
        List<T> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            result.add(convertValue(item, type));
        }
        return result;
    }

    /** Converts a map with typed keys and values. */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> asMap(Class<K> keyType, Class<V> valueType) {
        checkNotNull("Map");
        if (!(raw instanceof Map)) {
            throw new ClassCastException("Cannot convert " + className() + " to Map");
        }
        Map<?, ?> rawMap = (Map<?, ?>) raw;
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
    private static <T> T convertValue(Object value, Class<T> targetType) {
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
        throw new ClassCastException(
                "Cannot convert " + value.getClass().getName() + " to " + targetType.getName());
    }
}
