package io.github.howtis.pythonembed;

/**
 * Hook called before or after a {@link PythonEmbed} instance is closed.
 *
 * <p>Register via
 * {@link PythonEmbed.Options.Builder#onBeforeClose(CloseHook)} or
 * {@link PythonEmbed.Options.Builder#onAfterClose(CloseHook)}.
 *
 * @see CloseReason
 */
@FunctionalInterface
public interface CloseHook {
    /**
     * Called during {@link PythonEmbed} close with the reason for closing.
     *
     * @param embed  the instance being closed
     * @param reason why the instance is being closed
     */
    void onClose(PythonEmbed embed, CloseReason reason);
}
