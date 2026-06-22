package io.github.howtis.pythonembed;

/**
 * Thrown when a Python evaluation or execution fails.
 */
public class PythonExecutionException extends RuntimeException {

    private final String pythonTraceback;
    private final String causeCode;

    public PythonExecutionException(String message) {
        super(message);
        this.pythonTraceback = null;
        this.causeCode = null;
    }

    public PythonExecutionException(String message, String pythonTraceback) {
        super(message);
        this.pythonTraceback = pythonTraceback;
        this.causeCode = null;
    }

    public PythonExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.pythonTraceback = null;
        this.causeCode = null;
    }

    public PythonExecutionException(String message, String pythonTraceback, String causeCode) {
        super(message);
        this.pythonTraceback = pythonTraceback;
        this.causeCode = causeCode;
    }

    /**
     * Wraps a checked exception as a PythonExecutionException.
     * If the cause is already a PythonExecutionException, returns it as-is.
     *
     * @param operation description of the operation that failed
     * @param cause     the original exception
     * @return a PythonExecutionException (unchecked)
     */
    public static PythonExecutionException wrap(String operation, Throwable cause) {
        if (cause instanceof PythonExecutionException) {
            return (PythonExecutionException) cause;
        }
        return new PythonExecutionException(operation + " failed: " + cause.getMessage(), cause);
    }

    /**
     * Returns the full Python traceback, or null if not available.
     * The traceback includes file names, line numbers, and stack frames.
     */
    public String getPythonTraceback() {
        return pythonTraceback;
    }

    /**
     * Returns the Python code that caused this exception, or null if not available.
     * Useful for debugging when multiple eval/exec calls are made.
     */
    public String getCauseCode() {
        return causeCode;
    }
}
