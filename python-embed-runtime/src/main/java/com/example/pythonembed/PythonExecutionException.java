package com.example.pythonembed;

/**
 * Thrown when a Python evaluation or execution fails.
 */
public class PythonExecutionException extends Exception {

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
