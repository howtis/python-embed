package io.github.howtis.pythonembed;

/**
 * Functional interface for Python&#8594;Java callback handlers
 * that accept three arguments.
 *
 * <p>Register via
 * {@link PythonEmbed#registerCallback(String, Class, Class, Class, CallbackHandler3)}.
 * The handler receives the arguments from Python's
 * {@code _bridge.call(name, arg1, arg2, arg3)} and returns a value
 * that is sent back to Python.
 *
 * @param <A> the type of the first argument
 * @param <B> the type of the second argument
 * @param <C> the type of the third argument
 */
@FunctionalInterface
public interface CallbackHandler3<A, B, C> {
    Object handle(A arg1, B arg2, C arg3) throws Exception;
}
