package io.github.howtis.pythonembed;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;

/**
 * MessagePack (binary) protocol implementation.
 *
 * <p>Uses length-prefixed MessagePack frames over stdin/stdout.
 * Each frame is: 4-byte big-endian length, then MessagePack-encoded
 * {@code Map<String, Object>}.
 */
final class MsgpackProtocol extends PythonProtocol {

    private static final java.util.logging.Logger JUL =
            java.util.logging.Logger.getLogger(MsgpackProtocol.class.getName());

    /** Whether SLF4J binding is available for Python log forwarding. */
    static boolean LOG_FORWARDING_AVAILABLE;

    static {
        boolean available;
        try {
            available = !(LoggerFactory.getILoggerFactory() instanceof NOPLoggerFactory);
        } catch (Exception e) {
            available = false;
        }
        LOG_FORWARDING_AVAILABLE = available;
        if (!available) {
            JUL.log(Level.WARNING,
                    "SLF4J binding not found -- Python log forwarding disabled. "
                    + "Add an SLF4J binding (e.g., logback-classic) to the classpath to enable.");
        }
    }

    MsgpackProtocol() {
        super();
    }

    MsgpackProtocol(long timeoutMs) {
        super(timeoutMs);
    }

    // ---- request builders ----

    @Override
    byte[] buildEvalRequest(int id, String code) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "eval");
        req.put("code", code);
        return packFrame(req);
    }

    @Override
    byte[] buildExecRequest(int id, String code) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "exec");
        req.put("code", code);
        return packFrame(req);
    }

    @Override
    byte[] buildExitRequest(int id) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "exit");
        return packFrame(req);
    }

    @Override
    byte[] buildRefRequest(int id, String name) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "ref");
        req.put("name", name);
        return packFrame(req);
    }

    @Override
    byte[] buildReleaseRequest(int id, int refId) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "release");
        req.put("ref_id", refId);
        return packFrame(req);
    }

    @Override
    byte[] buildCallRequest(int id, int refId, String method, Object[] args) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "call");
        req.put("ref_id", refId);
        req.put("method", method);
        req.put("args", args != null ? Arrays.asList(args) : Collections.emptyList());
        return packFrame(req);
    }

    @Override
    byte[] buildGetAttrRequest(int id, int refId, String name) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "getattr");
        req.put("ref_id", refId);
        req.put("name", name);
        return packFrame(req);
    }

    @Override
    byte[] buildPingRequest(int id) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "ping");
        return packFrame(req);
    }

    @Override
    byte[] buildHealthRequest(int id) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "health");
        return packFrame(req);
    }

    @Override
    byte[] buildStreamRequest(int id, String code) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "stream");
        req.put("code", code);
        return packFrame(req);
    }

    // ---- callback message builders ----

    @Override
    byte[] buildCallbackResult(int id, Object value) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", id);
        msg.put("type", "callback_result");
        msg.put("value", value);
        return packFrame(msg);
    }

    @Override
    byte[] buildCallbackError(int id, String message) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", id);
        msg.put("type", "callback_error");
        msg.put("message", message);
        return packFrame(msg);
    }

    @Override
    byte[] buildPushRequest(int id, String name, Object value) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", id);
        req.put("type", "push");
        req.put("name", name);
        req.put("value", value);
        return packFrame(req);
    }

    @Override
    byte[] buildBatchRequest(int[] ids, List<Map<String, Object>> items) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "batch");
        req.put("items", items);
        return packFrame(req);
    }

    // ---- response handler ----

    @Override
    void handleResponse(byte[] raw) {
        if (serializationSizeListener != null) {
            serializationSizeListener.onResponseSize(raw.length);
        }
        try {
            Map<String, Object> map = unpackMap(raw);
            int id = ((Number) map.get("id")).intValue();
            String type = (String) map.get("type");

            if ("stream_item".equals(type) || "stream_end".equals(type)) {
                routeStreamResponse(id, type, map);
            } else if ("callback_request".equals(type)) {
                routeCallbackRequest(id, map);
            } else if ("push".equals(type)) {
                routePush(id, map);
            } else if ("log".equals(type)) {
                routeLog(map);
            } else {
                Object value = map.get("value");
                String message = (String) map.get("message");
                completeResponse(new Response(id, type, value, message));
            }
        } catch (Exception e) {
            // Malformed MessagePack - log and ignore
        }
    }

    @SuppressWarnings("unchecked")
    private void routeCallbackRequest(int id, Map<String, Object> map) {
        if (callbackDispatcher == null) return;
        String name = (String) map.get("name");
        List<Object> args = (List<Object>) map.getOrDefault("args", Collections.emptyList());
        try {
            callbackDispatcher.dispatchCall(id, name, args);
        } catch (Exception e) {
            // The dispatcher handles error reporting
        }
    }

    private void routePush(int id, Map<String, Object> map) {
        if (callbackDispatcher == null) return;
        String name = (String) map.get("name");
        Object value = map.get("value");
        callbackDispatcher.dispatchPush(id, name, value);
    }

    private void routeLog(Map<String, Object> map) {
        if (!LOG_FORWARDING_AVAILABLE) {
            return;
        }
        String level = (String) map.getOrDefault("level", "INFO");
        String loggerName = (String) map.getOrDefault("logger", "");
        String message = (String) map.getOrDefault("message", "");

        Logger slf4jLogger = LoggerFactory.getLogger("python." + loggerName);
        switch (level.toUpperCase()) {
            case "ERROR":
                slf4jLogger.error(message);
                break;
            case "WARNING":
                slf4jLogger.warn(message);
                break;
            case "INFO":
                slf4jLogger.info(message);
                break;
            case "DEBUG":
                slf4jLogger.debug(message);
                break;
            default:
                slf4jLogger.info(message);
        }
    }

    private void routeStreamResponse(int id, String type, Map<String, Object> map) {
        if ("stream_end".equals(type)) {
            BlockingQueue<PythonValue> queue = streamQueues.remove(id);
            if (queue != null) {
                queue.add(STREAM_END);
            }
        } else {
            BlockingQueue<PythonValue> queue = streamQueues.get(id);
            if (queue != null) {
                queue.add(PythonValue.of(map.get("value")));
            }
        }
    }

    // ---- MessagePack helpers ----

    private byte[] packFrame(Map<String, Object> map) {
        try {
            byte[] payload = packMap(map);
            byte[] frame = new byte[4 + payload.length];
            frame[0] = (byte) (payload.length >>> 24);
            frame[1] = (byte) (payload.length >>> 16);
            frame[2] = (byte) (payload.length >>> 8);
            frame[3] = (byte) (payload.length);
            System.arraycopy(payload, 0, frame, 4, payload.length);
            if (serializationSizeListener != null) {
                serializationSizeListener.onRequestSize(frame.length);
            }
            return frame;
        } catch (IOException e) {
            throw new RuntimeException("MessagePack packing failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static byte[] packMap(Map<String, Object> map) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MessagePacker packer = MessagePack.newDefaultPacker(out)) {
            packValue(packer, map);
        }
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static void packValue(MessagePacker packer, Object value) throws IOException {
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
        } else if (value instanceof Map<?, ?> map) {
            packer.packMapHeader(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                packValue(packer, entry.getKey().toString());
                packValue(packer, entry.getValue());
            }
        } else {
            packer.packString(value.toString());
        }
    }

    private static Map<String, Object> unpackMap(byte[] data) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            return (Map<String, Object>) unpackValue(unpacker);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object unpackValue(MessageUnpacker unpacker) throws IOException {
        Value v = unpacker.unpackValue();
        return convertValue(v);
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(Value v) {
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
                List<Object> list = new ArrayList<>();
                for (Value item : v.asArrayValue()) {
                    list.add(convertValue(item));
                }
                return list;
            case MAP:
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<Value, Value> entry : v.asMapValue().entrySet()) {
                    map.put(entry.getKey().asStringValue().asString(),
                            convertValue(entry.getValue()));
                }
                return map;
            case EXTENSION:
                org.msgpack.value.ExtensionValue ext = v.asExtensionValue();
                byte extType = ext.getType();
                byte[] data = ext.getData();
                if (extType == 1 || extType == 2) {
                    try (MessageUnpacker u = MessagePack.newDefaultUnpacker(data)) {
                        return convertValue(u.unpackValue());
                    } catch (IOException e) {
                        return data;
                    }
                }
                return data;
            default:
                return v.toString();
        }
    }
}
