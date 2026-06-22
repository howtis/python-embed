package com.example.pythonembed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MsgpackProtocolTest {

    // ---- request builders produce valid MessagePack frames ----

    @Test
    void buildEvalRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildEvalRequest(1, "42");

        assertTrue(frame.length >= 4, "frame must have at least 4-byte length prefix");
        int payloadLen = readFrameLength(frame);
        assertTrue(payloadLen > 0);
        assertEquals(frame.length - 4, payloadLen);

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(1, ((Number) decoded.get("id")).intValue());
        assertEquals("eval", decoded.get("type"));
        assertEquals("42", decoded.get("code"));
    }

    @Test
    void buildExecRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildExecRequest(2, "x = 1");

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(2, ((Number) decoded.get("id")).intValue());
        assertEquals("exec", decoded.get("type"));
        assertEquals("x = 1", decoded.get("code"));
    }

    @Test
    void buildExitRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildExitRequest(99);

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(99, ((Number) decoded.get("id")).intValue());
        assertEquals("exit", decoded.get("type"));
    }

    @Test
    void buildRefRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildRefRequest(5, "my_var");

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(5, ((Number) decoded.get("id")).intValue());
        assertEquals("ref", decoded.get("type"));
        assertEquals("my_var", decoded.get("name"));
    }

    @Test
    void buildReleaseRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildReleaseRequest(7, 42);

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(7, ((Number) decoded.get("id")).intValue());
        assertEquals("release", decoded.get("type"));
        assertEquals(42, ((Number) decoded.get("ref_id")).intValue());
    }

    @Test
    void buildCallRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildCallRequest(10, 100, "upper", new Object[]{"hello"});

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(10, ((Number) decoded.get("id")).intValue());
        assertEquals("call", decoded.get("type"));
        assertEquals(100, ((Number) decoded.get("ref_id")).intValue());
        assertEquals("upper", decoded.get("method"));
        assertTrue(decoded.get("args") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> args = (List<Object>) decoded.get("args");
        assertEquals(1, args.size());
        assertEquals("hello", args.get(0));
    }

    @Test
    void buildCallRequest_nullArgs_usesEmptyList() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildCallRequest(1, 1, "m", null);

        Map<String, Object> decoded = unpackFrame(frame);
        @SuppressWarnings("unchecked")
        List<Object> args = (List<Object>) decoded.get("args");
        assertTrue(args.isEmpty());
    }

    @Test
    void buildGetAttrRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildGetAttrRequest(3, 200, "__class__");

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(3, ((Number) decoded.get("id")).intValue());
        assertEquals("getattr", decoded.get("type"));
        assertEquals(200, ((Number) decoded.get("ref_id")).intValue());
        assertEquals("__class__", decoded.get("name"));
    }

    @Test
    void buildPingRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildPingRequest(1);

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(1, ((Number) decoded.get("id")).intValue());
        assertEquals("ping", decoded.get("type"));
    }

    @Test
    void buildHealthRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildHealthRequest(1);

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(1, ((Number) decoded.get("id")).intValue());
        assertEquals("health", decoded.get("type"));
    }

    @Test
    void buildStreamRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildStreamRequest(8, "range(10)");

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(8, ((Number) decoded.get("id")).intValue());
        assertEquals("stream", decoded.get("type"));
        assertEquals("range(10)", decoded.get("code"));
    }

    @Test
    void buildCallbackResult_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildCallbackResult(42, "result_value");

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(42, ((Number) decoded.get("id")).intValue());
        assertEquals("callback_result", decoded.get("type"));
        assertEquals("result_value", decoded.get("value"));
    }

    @Test
    void buildCallbackError_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildCallbackError(42, "something went wrong");

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(42, ((Number) decoded.get("id")).intValue());
        assertEquals("callback_error", decoded.get("type"));
        assertEquals("something went wrong", decoded.get("message"));
    }

    @Test
    void buildPushRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] frame = proto.buildPushRequest(3, "notify", "pushed_value");

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals(3, ((Number) decoded.get("id")).intValue());
        assertEquals("push", decoded.get("type"));
        assertEquals("notify", decoded.get("name"));
        assertEquals("pushed_value", decoded.get("value"));
    }

    @Test
    void buildBatchRequest_producesValidFrame() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        Map<String, Object> item1 = Map.of("id", 1, "type", "eval", "code", "1+1");
        Map<String, Object> item2 = Map.of("id", 2, "type", "eval", "code", "2+2");
        byte[] frame = proto.buildBatchRequest(new int[]{1, 2}, List.of(item1, item2));

        Map<String, Object> decoded = unpackFrame(frame);
        assertEquals("batch", decoded.get("type"));
        assertNull(decoded.get("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) decoded.get("items");
        assertEquals(2, items.size());
        assertEquals("eval", items.get(0).get("type"));
        assertEquals("1+1", items.get(0).get("code"));
        assertEquals("eval", items.get(1).get("type"));
        assertEquals("2+2", items.get(1).get("code"));
    }

    // ---- handleResponse routing ----

    @Test
    void handleResponse_result_completesRegisteredFuture() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] raw = encodeResponseFrame(id, "result", 42, null);
        proto.handleResponse(raw);

        assertTrue(future.isDone());
        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        assertEquals(42, resp.value());
        assertFalse(resp.isError());
    }

    @Test
    void handleResponse_error_completesWithError() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] raw = encodeResponseFrame(id, "error", null, "NameError: name 'x' is not defined");
        proto.handleResponse(raw);

        assertTrue(future.isDone());
        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        assertTrue(resp.isError());
        assertTrue(resp.message().contains("NameError"));
    }

    @Test
    void handleResponse_unknownId_doesNotThrow() {
        MsgpackProtocol proto = new MsgpackProtocol();
        byte[] raw = encodeResponseFrame(999, "result", null, null);
        // should not throw
        proto.handleResponse(raw);
    }

    // ---- MessagePack type round-trip through response handling ----

    @Test
    void handleResponse_booleanValue() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] raw = encodeResponseFrame(id, "result", true, null);
        proto.handleResponse(raw);

        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        assertEquals(true, resp.value());
    }

    @Test
    void handleResponse_doubleValue() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] raw = encodeResponseFrame(id, "result", 3.14, null);
        proto.handleResponse(raw);

        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        assertEquals(3.14, (Double) resp.value(), 0.001);
    }

    @Test
    void handleResponse_stringValue() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] raw = encodeResponseFrame(id, "result", "hello", null);
        proto.handleResponse(raw);

        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        assertEquals("hello", resp.value());
    }

    @Test
    void handleResponse_listValue() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] raw = encodeResponseFrame(id, "result", List.of(1, 2, 3), null);
        proto.handleResponse(raw);

        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) resp.value();
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }

    @Test
    void handleResponse_mapValue() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] raw = encodeResponseFrame(id, "result", Map.of("a", 1, "b", 2), null);
        proto.handleResponse(raw);

        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) resp.value();
        assertEquals(2, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
    }

    @Test
    void handleResponse_nullValue() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] raw = encodeResponseFrame(id, "result", null, null);
        proto.handleResponse(raw);

        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        assertNull(resp.value());
    }

    @Test
    void handleResponse_binaryValue() throws Exception {
        MsgpackProtocol proto = new MsgpackProtocol();
        int id = proto.nextId();
        CompletableFuture<PythonProtocol.Response> future = proto.registerRequest(id);

        byte[] binary = new byte[]{0x01, 0x02, 0x03};
        byte[] raw = encodeResponseFrame(id, "result", binary, null);
        proto.handleResponse(raw);

        PythonProtocol.Response resp = future.get(1, TimeUnit.SECONDS);
        assertArrayEquals(binary, (byte[]) resp.value());
    }

    // ---- helpers ----

    private static int readFrameLength(byte[] frame) {
        return ((frame[0] & 0xFF) << 24)
             | ((frame[1] & 0xFF) << 16)
             | ((frame[2] & 0xFF) << 8)
             |  (frame[3] & 0xFF);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unpackFrame(byte[] frame) throws Exception {
        int payloadLen = readFrameLength(frame);
        byte[] payload = new byte[payloadLen];
        System.arraycopy(frame, 4, payload, 0, payloadLen);

        org.msgpack.core.MessageUnpacker unpacker =
                org.msgpack.core.MessagePack.newDefaultUnpacker(payload);
        Object converted = convertValue(unpacker.unpackValue());
        return (Map<String, Object>) converted;
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(org.msgpack.value.Value v) {
        switch (v.getValueType()) {
            case NIL:
                return null;
            case BOOLEAN:
                return v.asBooleanValue().getBoolean();
            case INTEGER:
                try {
                    return v.asIntegerValue().toInt();
                } catch (ArithmeticException e) {
                    return v.asIntegerValue().toLong();
                }
            case FLOAT:
                return v.asFloatValue().toDouble();
            case STRING:
                return v.asStringValue().asString();
            case BINARY:
                return v.asBinaryValue().asByteArray();
            case ARRAY:
                java.util.List<Object> list = new java.util.ArrayList<>();
                for (org.msgpack.value.Value item : v.asArrayValue()) {
                    list.add(convertValue(item));
                }
                return list;
            case MAP:
                java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<org.msgpack.value.Value, org.msgpack.value.Value> entry
                        : v.asMapValue().entrySet()) {
                    map.put(entry.getKey().asStringValue().asString(),
                            convertValue(entry.getValue()));
                }
                return map;
            default:
                return v.toString();
        }
    }

    private static byte[] encodeResponseFrame(int id, String type, Object value, String message) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", id);
        map.put("type", type);
        if (value != null) {
            map.put("value", value);
        }
        if (message != null) {
            map.put("message", message);
        }
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            org.msgpack.core.MessagePacker packer = org.msgpack.core.MessagePack.newDefaultPacker(out);
            packValue(packer, map);
            packer.close();
            return out.toByteArray(); // payload only, no length prefix (handleResponse strips it)
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void packValue(org.msgpack.core.MessagePacker packer, Object value) throws java.io.IOException {
        if (value == null) {
            packer.packNil();
        } else if (value instanceof Boolean b) {
            packer.packBoolean(b);
        } else if (value instanceof Integer i) {
            packer.packInt(i);
        } else if (value instanceof Long l) {
            packer.packLong(l);
        } else if (value instanceof Double d) {
            packer.packDouble(d);
        } else if (value instanceof String s) {
            packer.packString(s);
        } else if (value instanceof byte[] b) {
            packer.packBinaryHeader(b.length);
            packer.writePayload(b);
        } else if (value instanceof List<?> list) {
            packer.packArrayHeader(list.size());
            for (Object item : list) {
                packValue(packer, item);
            }
        } else if (value instanceof Map<?, ?> m) {
            packer.packMapHeader(m.size());
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                packValue(packer, entry.getKey().toString());
                packValue(packer, entry.getValue());
            }
        } else {
            packer.packString(value.toString());
        }
    }
}
