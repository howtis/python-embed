package io.github.howtis.pythonembed;

/**
 * Categories of {@link PythonEmbed} close events delivered to
 * {@link CloseHook} listeners.
 */
public enum CloseReason {
    /** User-initiated close via {@link PythonEmbed#close()} or try-with-resources. */
    USER,
    /** JVM shutdown hook triggered close. */
    SHUTDOWN_HOOK,
    /** Pool idle scale-down removal. */
    POOL_CLEANUP,
    /** Pool health check removal (unhealthy instance). */
    HEALTH_CHECK
}
