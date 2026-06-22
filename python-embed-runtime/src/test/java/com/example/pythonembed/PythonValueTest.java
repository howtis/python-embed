package com.example.pythonembed;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PythonValueTest {

    @Test
    void asInt_fromDouble() {
        PythonValue v = PythonValue.of(42.0);
        assertEquals(42, v.asInt());
    }

    @Test
    void asInt_fromLong() {
        PythonValue v = PythonValue.of(42L);
        assertEquals(42, v.asInt());
    }

    @Test
    void asInt_fromString() {
        PythonValue v = PythonValue.of("42");
        assertEquals(42, v.asInt());
    }

    @Test
    void asLong_fromDouble() {
        PythonValue v = PythonValue.of(42.0);
        assertEquals(42L, v.asLong());
    }

    @Test
    void asDouble_fromDouble() {
        PythonValue v = PythonValue.of(3.14);
        assertEquals(3.14, v.asDouble(), 0.001);
    }

    @Test
    void asBoolean_true() {
        PythonValue v = PythonValue.of(true);
        assertTrue(v.asBoolean());
    }

    @Test
    void asBoolean_false() {
        PythonValue v = PythonValue.of(false);
        assertFalse(v.asBoolean());
    }

    @Test
    void asBoolean_wrongType_throws() {
        PythonValue v = PythonValue.of(1.0);
        assertThrows(ClassCastException.class, v::asBoolean);
    }

    @Test
    void asString_fromString() {
        PythonValue v = PythonValue.of("hello");
        assertEquals("hello", v.asString());
    }

    @Test
    void asString_fromNumber() {
        PythonValue v = PythonValue.of(42.0);
        assertEquals("42.0", v.asString());
    }

    @Test
    void asString_fromNull() {
        PythonValue v = PythonValue.of(null);
        assertEquals("null", v.asString());
    }

    @Test
    void asList_ofIntegers() {
        PythonValue v = PythonValue.of(List.of(1.0, 2.0, 3.0));
        List<Integer> list = v.asList(Integer.class);
        assertEquals(List.of(1, 2, 3), list);
    }

    @Test
    void asList_ofStrings() {
        PythonValue v = PythonValue.of(List.of("a", "b", "c"));
        List<String> list = v.asList(String.class);
        assertEquals(List.of("a", "b", "c"), list);
    }

    @Test
    void asList_raw() {
        PythonValue v = PythonValue.of(List.of(1.0, "hello", true));
        List<Object> list = v.asList(Object.class);
        assertEquals(3, list.size());
        assertEquals(1.0, list.get(0));
        assertEquals("hello", list.get(1));
        assertEquals(true, list.get(2));
    }

    @Test
    void asList_wrongType_throws() {
        PythonValue v = PythonValue.of("not_a_list");
        assertThrows(ClassCastException.class, () -> v.asList(String.class));
    }

    @Test
    void asMap_stringKeys() {
        PythonValue v = PythonValue.of(Map.of("key1", "val1", "key2", "val2"));
        Map<String, String> map = v.asMap(String.class, String.class);
        assertEquals(Map.of("key1", "val1", "key2", "val2"), map);
    }

    @Test
    void asMap_raw() {
        PythonValue v = PythonValue.of(Map.of("a", 1.0, "b", 2.0));
        Map<Object, Object> map = v.asMap(Object.class, Object.class);
        assertEquals(2, map.size());
        assertEquals(1.0, map.get("a"));
    }

    @Test
    void asMap_wrongType_throws() {
        PythonValue v = PythonValue.of(List.of());
        assertThrows(ClassCastException.class, () -> v.asMap(String.class, String.class));
    }

    @Test
    void raw_returnsOriginal() {
        Object raw = List.of(1.0, 2.0);
        PythonValue v = PythonValue.of(raw);
        assertSame(raw, v.raw());
    }

    @Test
    void asInt_wrongType_throws() {
        PythonValue v = PythonValue.of(true);
        assertThrows(ClassCastException.class, v::asInt);
    }

    @Test
    void asList_noArg_returnsRawList() {
        PythonValue v = PythonValue.of(List.of(1.0, "hello", true));
        List<Object> list = v.asList();
        assertEquals(3, list.size());
        assertEquals(1.0, list.get(0));
        assertEquals("hello", list.get(1));
        assertEquals(true, list.get(2));
    }

    @Test
    void asList_noArg_wrongType_throws() {
        PythonValue v = PythonValue.of("not_a_list");
        assertThrows(ClassCastException.class, v::asList);
    }

    @Test
    void asMap_noArg_returnsRawMap() {
        PythonValue v = PythonValue.of(Map.of("a", 1.0, "b", 2.0));
        Map<String, Object> map = v.asMap();
        assertEquals(2, map.size());
        assertEquals(1.0, map.get("a"));
        assertEquals(2.0, map.get("b"));
    }

    @Test
    void asMap_noArg_wrongType_throws() {
        PythonValue v = PythonValue.of(List.of());
        assertThrows(ClassCastException.class, v::asMap);
    }

    @Test
    void asBytes_fromByteArray() {
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};
        PythonValue v = PythonValue.of(data);
        assertArrayEquals(data, v.asBytes());
    }

    @Test
    void asBytes_fromBase64String() {
        PythonValue v = PythonValue.of("AQIDBA==");
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, v.asBytes());
    }

    @Test
    void asBytes_wrongType_throws() {
        PythonValue v = PythonValue.of(42);
        assertThrows(ClassCastException.class, v::asBytes);
    }
}
