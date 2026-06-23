package io.github.howtis.pythonembed;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonValueToJsonTest {

    @Test
    void nullValue_returnsNull() {
        PythonValue v = PythonValue.of(null);
        assertEquals("null", v.toJson());
    }

    @Test
    void booleanTrue_returnsTrue() {
        PythonValue v = PythonValue.of(true);
        assertEquals("true", v.toJson());
    }

    @Test
    void booleanFalse_returnsFalse() {
        PythonValue v = PythonValue.of(false);
        assertEquals("false", v.toJson());
    }

    @Test
    void integer_returnsNumber() {
        PythonValue v = PythonValue.of(42);
        assertEquals("42", v.toJson());
    }

    @Test
    void long_returnsNumber() {
        PythonValue v = PythonValue.of(9223372036854775807L);
        assertEquals("9223372036854775807", v.toJson());
    }

    @Test
    void double_returnsNumber() {
        PythonValue v = PythonValue.of(3.14);
        assertEquals("3.14", v.toJson());
    }

    @Test
    void doubleNaN_returnsNull() {
        PythonValue v = PythonValue.of(Double.NaN);
        assertEquals("null", v.toJson());
    }

    @Test
    void doublePositiveInfinity_returnsNull() {
        PythonValue v = PythonValue.of(Double.POSITIVE_INFINITY);
        assertEquals("null", v.toJson());
    }

    @Test
    void doubleNegativeInfinity_returnsNull() {
        PythonValue v = PythonValue.of(Double.NEGATIVE_INFINITY);
        assertEquals("null", v.toJson());
    }

    @Test
    void simpleString_returnsQuoted() {
        PythonValue v = PythonValue.of("hello");
        assertEquals("\"hello\"", v.toJson());
    }

    @Test
    void stringWithEscapes_returnsEscaped() {
        PythonValue v = PythonValue.of("he\"llo\nwor\\ld");
        assertEquals("\"he\\\"llo\\nwor\\\\ld\"", v.toJson());
    }

    @Test
    void unicodeString_preservesCharacters() {
        PythonValue v = PythonValue.of("café");
        assertEquals("\"café\"", v.toJson());
    }

    @Test
    void emptyList_returnsEmptyArray() {
        PythonValue v = PythonValue.of(List.of());
        assertEquals("[]", v.toJson());
    }

    @Test
    void listOfInts_returnsArray() {
        PythonValue v = PythonValue.of(List.of(1, 2, 3));
        assertEquals("[1, 2, 3]", v.toJson());
    }

    @Test
    void listOfMixedTypes_returnsMixedArray() {
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add("two");
        list.add(true);
        list.add(null);
        PythonValue v = PythonValue.of(list);
        assertEquals("[1, \"two\", true, null]", v.toJson());
    }

    @Test
    void nestedList_returnsNestedArrays() {
        PythonValue v = PythonValue.of(List.of(List.of(1, 2), List.of(3, 4)));
        assertEquals("[[1, 2], [3, 4]]", v.toJson());
    }

    @Test
    void emptyMap_returnsEmptyObject() {
        PythonValue v = PythonValue.of(Map.of());
        assertEquals("{}", v.toJson());
    }

    @Test
    void simpleMap_returnsObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Alice");
        map.put("age", 30);
        PythonValue v = PythonValue.of(map);
        assertEquals("{\"name\": \"Alice\", \"age\": 30}", v.toJson());
    }

    @Test
    void nestedMap_returnsNestedObjects() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("x", 1);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("inner", inner);
        PythonValue v = PythonValue.of(outer);
        assertEquals("{\"inner\": {\"x\": 1}}", v.toJson());
    }

    @Test
    void prettyPrint_simpleMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Bob");
        map.put("score", 95);
        PythonValue v = PythonValue.of(map);
        String json = v.toJson(true);
        assertTrue(json.contains("\n"));
        assertTrue(json.contains("  "));
    }

    @Test
    void prettyPrint_emptyMap() {
        PythonValue v = PythonValue.of(Map.of());
        assertEquals("{}\n", v.toJson(true));
    }

    @Test
    void prettyPrint_listOfMaps() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        item.put("name", "A");
        PythonValue v = PythonValue.of(List.of(item));
        String json = v.toJson(true);
        assertTrue(json.contains("\n"));
        assertTrue(json.contains("  "));
    }

    @Test
    void bytes_throwsException() {
        PythonValue v = PythonValue.of(new byte[]{0x01, 0x02});
        PythonExecutionException ex = assertThrows(PythonExecutionException.class, v::toJson);
        assertTrue(ex.getMessage().contains("bytes"));
    }

    @Test
    void circularReference_list_throwsException() {
        List<Object> list = new ArrayList<>();
        list.add("self");
        list.add(list);
        PythonValue v = PythonValue.of(list);
        PythonExecutionException ex = assertThrows(PythonExecutionException.class, v::toJson);
        assertTrue(ex.getMessage().contains("circular"));
    }

    @Test
    void circularReference_map_throwsException() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("self", map);
        PythonValue v = PythonValue.of(map);
        PythonExecutionException ex = assertThrows(PythonExecutionException.class, v::toJson);
        assertTrue(ex.getMessage().contains("circular"));
    }

    @Test
    void mapWithNonStringKey_stringifiesKey() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(42, "value");
        PythonValue v = PythonValue.of(map);
        assertEquals("{\"42\": \"value\"}", v.toJson());
    }
}
