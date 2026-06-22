package io.github.howtis.pythonembed;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.concurrent.TimeoutException;

/**
 * A handle to a Python object held in the Python process's memory.
 *
 * <p>Instead of serializing the entire object across the process boundary
 * on every call, the object is kept in Python and referenced by a numeric ID.
 * This allows method calls and attribute access without re-serialization.
 *
 * <p>Handles should be released when no longer needed. They are also
 * auto-released via {@link Cleaner} when garbage-collected (best-effort).
 *
 * <pre>{@code
 * try (PythonEmbed py = PythonEmbed.create(PythonEmbed.Options.defaults())) {
 *     py.exec("import numpy as np");
 *     py.exec("arr = np.array([1, 2, 3])");
 *     PythonHandle handle = py.ref("arr");
 *     double mean = handle.call("mean").asDouble();
 *     handle.release(); // or close()
 * }
 * }</pre>
 */
public class PythonHandle implements AutoCloseable {

    private static final Cleaner cleaner = Cleaner.create();

    private final PythonEmbed owner;
    private final PythonProtocol protocol;
    private final PythonProtocol.Writer writer;
    private final int refId;
    private final String pythonType;
    private volatile boolean released;

    PythonHandle(PythonEmbed owner, PythonProtocol protocol,
                 PythonProtocol.Writer writer, int refId, String pythonType) {
        this.owner = owner;
        this.protocol = protocol;
        this.writer = writer;
        this.refId = refId;
        this.pythonType = pythonType;
        this.released = false;

        cleaner.register(this, () -> {
            if (!released && owner.isOpen()) {
                try {
                    protocol.sendRelease(writer, refId);
                } catch (Exception ignored) {
                }
            }
        });
    }

    /** The numeric reference ID in the Python process. */
    public int refId() { return refId; }

    /** The Python type name (e.g., "ndarray", "Counter"). */
    public String pythonType() { return pythonType; }

    /**
     * Calls a method on the Python object and returns the result.
     *
     * @param method method name
     * @param args   arguments to pass (primitives, strings, or null)
     * @return the method's return value
     */
    public PythonValue call(String method, Object... args)
            throws PythonExecutionException, TimeoutException, IOException {
        checkNotReleased();
        return protocol.sendCall(writer, refId, method, args);
    }

    /**
     * Gets an attribute value from the Python object.
     *
     * @param name attribute name
     * @return the attribute value
     */
    public PythonValue getAttr(String name)
            throws PythonExecutionException, TimeoutException, IOException {
        checkNotReleased();
        return protocol.sendGetAttr(writer, refId, name);
    }

    /**
     * Explicitly releases the Python object reference.
     * After release, this handle is no longer usable.
     */
    public void release() {
        if (released) return;
        released = true;
        owner.forgetHandle(this);
        protocol.sendRelease(writer, refId);
    }

    @Override
    public void close() {
        release();
    }

    private void checkNotReleased() {
        if (released) {
            throw new IllegalStateException("PythonHandle already released");
        }
    }
}
