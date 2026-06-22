package io.github.howtis.pythonembed;

/**
 * Functional interface for Python&#8594;Java push handlers.
 *
 * <p>Register via {@link PythonEmbed#registerPushHandler(String, PushHandler)}
 * or {@link PythonEmbed#registerPushHandler(String, Class, PushHandler)}
 * for typed value conversion.
 * The handler receives the push name and value from Python's
 * {@code _bridge.push(name, value)}. This is fire-and-forget;
 * exceptions are logged but not propagated to Python.
 *
 * @param <T> the type of the push value
 */
@FunctionalInterface
public interface PushHandler<T> {
    void accept(String name, T value) throws Exception;
}
