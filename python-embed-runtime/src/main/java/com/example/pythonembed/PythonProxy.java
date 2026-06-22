package com.example.pythonembed;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * {@link InvocationHandler} that routes Java interface method calls to a
 * Python object referenced by {@code refId} via the existing
 * {@code call}/{@code getattr} protocol commands.
 *
 * <p>Package-private - instantiated only by {@link PythonEmbed#proxy} and
 * {@link PythonEmbedPool#proxy}.
 */
class PythonProxy implements InvocationHandler {

    private final PythonProtocol protocol;
    private final PythonProtocol.Writer writer;
    private final int refId;
    private final long timeoutMs;
    private final PythonHandle handle; // strong reference to keep Python object alive

    /**
     * Creates a handler that wraps a Python object identified by {@code refId}.
     * The optional {@code handle} is held as a strong reference so that the
     * Python object is not garbage-collected while the proxy is in use.
     */
    PythonProxy(PythonProtocol protocol, PythonProtocol.Writer writer,
                int refId, long timeoutMs, PythonHandle handle) {
        this.protocol = protocol;
        this.writer = writer;
        this.refId = refId;
        this.timeoutMs = timeoutMs;
        this.handle = handle;
    }

    PythonProxy(PythonProtocol protocol, PythonProtocol.Writer writer,
                int refId, long timeoutMs) {
        this(protocol, writer, refId, timeoutMs, null);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object methods handled locally - no Python round-trip
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        return invokePython(method, args);
    }

    /**
     * Core invocation logic shared with {@link PythonPoolProxy}.
     * Routes a Java interface method call to the Python object.
     */
    Object invokePython(Method method, Object[] args) throws Throwable {
        String javaName = method.getName();
        Object[] callArgs = (args != null && args.length > 0) ? args : new Object[0];

        // 1. Try calling as a method (handles both zero-arg and multi-arg)
        try {
            return convertResult(
                    protocol.sendCall(writer, refId, javaName, callArgs),
                    method.getReturnType());
        } catch (PythonExecutionException e) {
            // 2. If AttributeError and name differs, try snake_case call
            String snakeName = camelToSnake(javaName);
            if (isAttributeError(e) && !snakeName.equals(javaName)) {
                try {
                    return convertResult(
                            protocol.sendCall(writer, refId, snakeName, callArgs),
                            method.getReturnType());
                } catch (PythonExecutionException e2) {
                    // call failed with snake_case too - try getattr as last resort
                    // (it might be a property, not a method)
                    if (isAttributeError(e2) && callArgs.length == 0) {
                        return convertResult(
                                protocol.sendGetAttr(writer, refId, snakeName),
                                method.getReturnType());
                    }
                    throw e2;
                }
            }
            // 3. Exact-name call failed - try getattr as fallback
            //    (the attribute might be a property, not a callable)
            if (isAttributeError(e) && callArgs.length == 0) {
                return convertResult(
                        protocol.sendGetAttr(writer, refId, javaName),
                        method.getReturnType());
            }
            throw e;
        }
    }

    // ---- type conversion ----

    @SuppressWarnings("unchecked")
    private static Object convertResult(PythonValue result, Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        Object raw = result.raw();
        if (raw == null) {
            return null;
        }
        if (returnType == int.class || returnType == Integer.class) {
            return result.asInt();
        }
        if (returnType == long.class || returnType == Long.class) {
            return result.asLong();
        }
        if (returnType == double.class || returnType == Double.class) {
            return result.asDouble();
        }
        if (returnType == float.class || returnType == Float.class) {
            return (float) result.asDouble();
        }
        if (returnType == boolean.class || returnType == Boolean.class) {
            return result.asBoolean();
        }
        if (returnType == String.class) {
            return result.asString();
        }
        if (returnType == byte[].class) {
            return result.asBytes();
        }
        if (List.class.isAssignableFrom(returnType)) {
            return result.asList();
        }
        if (Map.class.isAssignableFrom(returnType)) {
            return result.asMap();
        }
        return raw;
    }

    // ---- camelCase to snake_case ----

    /**
     * Converts camelCase to snake_case (e.g., "calculateSum" to "calculate_sum").
     */
    static String camelToSnake(String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Checks whether the exception was caused by a Python {@code AttributeError}.
     */
    static boolean isAttributeError(PythonExecutionException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("AttributeError");
    }
}
