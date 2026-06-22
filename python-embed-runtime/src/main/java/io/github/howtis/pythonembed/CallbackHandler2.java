package io.github.howtis.pythonembed;

/**
 * Functional interface for Python&#8594;Java callback handlers
 * that accept two arguments.
 *
 * <p>Register via
 * {@link PythonEmbed#registerCallback(String, Class, Class, CallbackHandler2)}.
 * The handler receives the arguments from Python's
 * {@code _bridge.call(name, arg1, arg2)} and returns a value
 * that is sent back to Python.
 *
 * @param <A> the type of the first argument
 * @param <B> the type of the second argument
 */
@FunctionalInterface
public interface CallbackHandler2<A, B> {
    Object handle(A arg1, B arg2) throws Exception;
}
