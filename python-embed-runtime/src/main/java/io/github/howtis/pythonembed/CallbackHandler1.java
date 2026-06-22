package io.github.howtis.pythonembed;

/**
 * Functional interface for Python&#8594;Java callback handlers
 * that accept a single argument.
 *
 * <p>Register via
 * {@link PythonEmbed#registerCallback(String, Class, CallbackHandler1)}.
 * The handler receives the argument from Python's
 * {@code _bridge.call(name, arg)} and returns a value
 * that is sent back to Python.
 *
 * @param <A> the type of the single argument
 */
@FunctionalInterface
public interface CallbackHandler1<A> {
    Object handle(A arg1) throws Exception;
}
