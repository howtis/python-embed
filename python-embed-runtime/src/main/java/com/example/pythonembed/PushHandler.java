package com.example.pythonembed;

/**
 * Functional interface for Python&#8594;Java push handlers.
 *
 * <p>Register via {@link PythonEmbed#registerPushHandler(String, PushHandler)}.
 * The handler receives the push name and value from Python's
 * {@code _bridge.push(name, value)}. This is fire-and-forget;
 * exceptions are logged but not propagated to Python.
 */
@FunctionalInterface
public interface PushHandler {
    void accept(String name, Object value);
}
