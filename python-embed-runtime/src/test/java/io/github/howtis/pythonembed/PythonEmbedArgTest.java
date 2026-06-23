package io.github.howtis.pythonembed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonEmbedArgTest {

    @Test
    void null_returnsNone() {
        assertEquals("None", PythonEmbed.arg(null));
    }

    @Test
    void true_returnsTrue() {
        assertEquals("True", PythonEmbed.arg(true));
    }

    @Test
    void false_returnsFalse() {
        assertEquals("False", PythonEmbed.arg(false));
    }

    @Test
    void integer_positive() {
        assertEquals("42", PythonEmbed.arg(42));
    }

    @Test
    void integer_negative() {
        assertEquals("-1", PythonEmbed.arg(-1));
    }

    @Test
    void integer_zero() {
        assertEquals("0", PythonEmbed.arg(0));
    }

    @Test
    void longValue() {
        assertEquals("-1", PythonEmbed.arg(-1L));
    }

    @Test
    void long_largeValue() {
        assertEquals("9223372036854775807", PythonEmbed.arg(Long.MAX_VALUE));
    }

    @Test
    void shortValue() {
        assertEquals("42", PythonEmbed.arg((short) 42));
    }

    @Test
    void byteValue() {
        assertEquals("0", PythonEmbed.arg((byte) 0));
    }

    @Test
    void double_value() {
        assertEquals("3.14", PythonEmbed.arg(3.14));
    }

    @Test
    void double_negative() {
        assertEquals("-1.5", PythonEmbed.arg(-1.5));
    }

    @Test
    void double_nan() {
        assertEquals("float('nan')", PythonEmbed.arg(Double.NaN));
    }

    @Test
    void double_positiveInfinity() {
        assertEquals("float('inf')", PythonEmbed.arg(Double.POSITIVE_INFINITY));
    }

    @Test
    void double_negativeInfinity() {
        assertEquals("float('-inf')", PythonEmbed.arg(Double.NEGATIVE_INFINITY));
    }

    @Test
    void float_value() {
        assertEquals("3.14", PythonEmbed.arg(3.14f));
    }

    @Test
    void float_nan() {
        assertEquals("float('nan')", PythonEmbed.arg(Float.NaN));
    }

    @Test
    void float_positiveInfinity() {
        assertEquals("float('inf')", PythonEmbed.arg(Float.POSITIVE_INFINITY));
    }

    @Test
    void float_negativeInfinity() {
        assertEquals("float('-inf')", PythonEmbed.arg(Float.NEGATIVE_INFINITY));
    }

    @Test
    void string_simple() {
        assertEquals("'hello'", PythonEmbed.arg("hello"));
    }

    @Test
    void string_empty() {
        assertEquals("''", PythonEmbed.arg(""));
    }

    @Test
    void string_singleQuote_escaped() {
        assertEquals("'it\\'s'", PythonEmbed.arg("it's"));
    }

    @Test
    void string_backslash_escaped() {
        assertEquals("'back\\\\slash'", PythonEmbed.arg("back\\slash"));
    }

    @Test
    void string_newline_escaped() {
        assertEquals("'line1\\nline2'", PythonEmbed.arg("line1\nline2"));
    }

    @Test
    void string_carriageReturn_escaped() {
        assertEquals("'col1\\rcol2'", PythonEmbed.arg("col1\rcol2"));
    }

    @Test
    void string_tab_escaped() {
        assertEquals("'col1\\tcol2'", PythonEmbed.arg("col1\tcol2"));
    }

    @Test
    void string_sqlInjection_escaped() {
        String arg = PythonEmbed.arg("' OR 1=1 --");
        assertEquals("'\\' OR 1=1 --'", arg);
    }

    @Test
    void string_multipleQuotes_escaped() {
        assertEquals("'she said \\'hello\\''", PythonEmbed.arg("she said 'hello'"));
    }

    @Test
    void string_controlChars_escaped() {
        assertEquals("'\\x00'", PythonEmbed.arg("\u0000"));
    }

    @Test
    void string_unicode_preserved() {
        assertEquals("'\uD55C\uAE00'", PythonEmbed.arg("\uD55C\uAE00"));
    }

    @Test
    void fallback_unknownType_escapesToString() {
        Object obj = new Object() {
            @Override
            public String toString() {
                return "it's custom";
            }
        };
        assertEquals("'it\\'s custom'", PythonEmbed.arg(obj));
    }

    @Test
    void list_integers() {
        assertEquals("[1, 2, 3]", PythonEmbed.arg(List.of(1, 2, 3)));
    }

    @Test
    void list_strings() {
        assertEquals("['a', 'b']", PythonEmbed.arg(List.of("a", "b")));
    }

    @Test
    void list_empty() {
        assertEquals("[]", PythonEmbed.arg(List.of()));
    }

    @Test
    void list_mixedTypes() {
        assertEquals("[1, 'two', 3.0]", PythonEmbed.arg(List.of(1, "two", 3.0)));
    }

    @Test
    void set_integers() {
        Set<Integer> set = new LinkedHashSet<>();
        set.add(1);
        set.add(2);
        assertEquals("{1, 2}", PythonEmbed.arg(set));
    }

    @Test
    void set_empty() {
        assertEquals("set()", PythonEmbed.arg(Set.of()));
    }

    @Test
    void set_strings() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");
        set.add("b");
        assertEquals("{'a', 'b'}", PythonEmbed.arg(set));
    }

    @Test
    void setSizeLimit_exceeded_throws() {
        Set<Integer> large = new LinkedHashSet<>();
        for (int i = 0; i < 5001; i++) {
            large.add(i);
        }
        assertThrows(IllegalArgumentException.class, () -> PythonEmbed.arg(large));
    }

    @Test
    void bytes_empty() {
        assertEquals("b''", PythonEmbed.arg(new byte[0]));
    }

    @Test
    void bytes_basic() {
        assertEquals("b'\\x00\\xff\\xab\\xcd'", PythonEmbed.arg(new byte[]{0, -1, -85, -51}));
    }

    @Test
    void bytes_allValues() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) {
            data[i] = (byte) i;
        }
        StringBuilder expected = new StringBuilder(256 * 5 + 3);
        expected.append("b'");
        for (int i = 0; i < 256; i++) {
            expected.append("\\x");
            expected.append(String.format("%02x", i));
        }
        expected.append("'");
        assertEquals(expected.toString(), PythonEmbed.arg(data));
    }

    @Test
    void bytesSizeLimit_exceeded_throws() {
        byte[] large = new byte[5001];
        assertThrows(IllegalArgumentException.class, () -> PythonEmbed.arg(large));
    }

    @Test
    void map_stringKeys() {
        assertEquals("{'key': 42}", PythonEmbed.arg(Map.of("key", 42)));
    }

    @Test
    void map_empty() {
        assertEquals("{}", PythonEmbed.arg(Map.of()));
    }

    @Test
    void map_multipleEntries() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("count", 5);
        assertEquals("{'name': 'test', 'count': 5}", PythonEmbed.arg(map));
    }

    @Test
    void nested_listInMap() {
        Map<String, Object> map = Map.of("values", List.of(1, 2));
        assertEquals("{'values': [1, 2]}", PythonEmbed.arg(map));
    }

    @Test
    void nested_mapInList() {
        List<Object> list = List.of(Map.of("x", 1));
        assertEquals("[{'x': 1}]", PythonEmbed.arg(list));
    }

    @Test
    void nested_deeplyNested() {
        List<Object> list = List.of(
                Map.of("a", List.of(
                        Map.of("b", List.of(1, 2, 3))
                ))
        );
        assertEquals("[{'a': [{'b': [1, 2, 3]}]}]", PythonEmbed.arg(list));
    }

    @Test
    void nested_nullElements() {
        List<Object> list = new ArrayList<>();
        list.add(null);
        list.add("a");
        assertEquals("[None, 'a']", PythonEmbed.arg(list));
    }

    @Test
    void map_nullValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", null);
        assertEquals("{'key': None}", PythonEmbed.arg(map));
    }

    @Test
    void depthLimit_exceeded_throws() {
        Object deep = buildDeepNested(21);
        assertThrows(IllegalArgumentException.class, () -> PythonEmbed.arg(deep));
    }

    @Test
    void depthLimit_atEdge_doesNotThrow() {
        Object deep = buildDeepNested(20);
        String result = PythonEmbed.arg(deep);
        assertEquals("[[[[[[[[[[[[[[[[[[[[1, 2]]]]]]]]]]]]]]]]]]]]", result);
    }

    private static List<Object> buildDeepNested(int depth) {
        List<Object> inner = new ArrayList<>();
        inner.add(1);
        inner.add(2);
        List<Object> current = inner;
        for (int i = 1; i < depth; i++) {
            List<Object> outer = new ArrayList<>();
            outer.add(current);
            current = outer;
        }
        return current;
    }

    @Test
    void collectionSizeLimit_exceeded_throws() {
        List<Integer> large = new ArrayList<>();
        for (int i = 0; i < 5001; i++) {
            large.add(i);
        }
        assertThrows(IllegalArgumentException.class, () -> PythonEmbed.arg(large));
    }

    @Test
    void mapSizeLimit_exceeded_throws() {
        Map<String, Integer> large = new LinkedHashMap<>();
        for (int i = 0; i < 5001; i++) {
            large.put("key" + i, i);
        }
        assertThrows(IllegalArgumentException.class, () -> PythonEmbed.arg(large));
    }

    @Test
    void map_integerKeys() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        assertEquals("{1: 'one', 2: 'two'}", PythonEmbed.arg(map));
    }

    // ------------------------------------------------------------------
    // Datetime types
    // ------------------------------------------------------------------

    @Test
    void localDateTime_basic() {
        LocalDateTime dt = LocalDateTime.of(2024, 1, 15, 10, 30, 45, 123_000_000);
        assertEquals("datetime.datetime(2024, 1, 15, 10, 30, 45, 123000)",
                PythonEmbed.arg(dt));
    }

    @Test
    void localDateTime_midnight() {
        LocalDateTime dt = LocalDateTime.of(2024, 6, 1, 0, 0, 0, 0);
        assertEquals("datetime.datetime(2024, 6, 1, 0, 0, 0, 0)",
                PythonEmbed.arg(dt));
    }

    @Test
    void localDateTime_endOfYear() {
        LocalDateTime dt = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_000_000);
        assertEquals("datetime.datetime(2024, 12, 31, 23, 59, 59, 999000)",
                PythonEmbed.arg(dt));
    }

    @Test
    void localDate_basic() {
        LocalDate d = LocalDate.of(2024, 1, 15);
        assertEquals("datetime.date(2024, 1, 15)", PythonEmbed.arg(d));
    }

    @Test
    void localDate_leapYear() {
        LocalDate d = LocalDate.of(2024, 2, 29);
        assertEquals("datetime.date(2024, 2, 29)", PythonEmbed.arg(d));
    }

    @Test
    void localTime_basic() {
        LocalTime t = LocalTime.of(10, 30, 45, 123_000_000);
        assertEquals("datetime.time(10, 30, 45, 123000)", PythonEmbed.arg(t));
    }

    @Test
    void localTime_midnight() {
        LocalTime t = LocalTime.of(0, 0, 0, 0);
        assertEquals("datetime.time(0, 0, 0, 0)", PythonEmbed.arg(t));
    }

    @Test
    void zonedDateTime_utc() {
        ZonedDateTime zdt = ZonedDateTime.of(
                2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
        assertEquals(
                "datetime.datetime(2024, 1, 15, 10, 30, 0, 0, " +
                "tzinfo=datetime.timezone(datetime.timedelta(seconds=0)))",
                PythonEmbed.arg(zdt));
    }

    @Test
    void zonedDateTime_plus9() {
        ZonedDateTime zdt = ZonedDateTime.of(
                2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(9));
        assertEquals(
                "datetime.datetime(2024, 1, 15, 10, 30, 0, 0, " +
                "tzinfo=datetime.timezone(datetime.timedelta(seconds=32400)))",
                PythonEmbed.arg(zdt));
    }

    @Test
    void instant_noNanos() {
        Instant inst = Instant.ofEpochSecond(1705312200L);
        assertEquals(
                "datetime.datetime.fromtimestamp(1705312200, tz=datetime.timezone.utc)",
                PythonEmbed.arg(inst));
    }

    @Test
    void instant_withNanos() {
        Instant inst = Instant.ofEpochSecond(1705312200L, 123_000_000L);
        assertEquals(
                "datetime.datetime.fromtimestamp(1705312200, tz=datetime.timezone.utc) + " +
                "datetime.timedelta(microseconds=123000)",
                PythonEmbed.arg(inst));
    }
}
