package io.github.howtis.pythonembed;

/**
 * Functional interface for Python&#8594;Java callback handlers.
 *
 * <p>Register via {@link PythonEmbed#registerCallback(String, CallbackHandler)}.
 * The handler receives args from Python's {@code _bridge.call(name, *args)}
 * and returns a value that is sent back to Python.
 */
@FunctionalInterface
public interface CallbackHandler {
    Object handle(Object... args) throws Exception;
}
