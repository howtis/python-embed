package io.github.howtis.pythonembed;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PythonExecutionException}: constructors,
 * {@code wrap()}, {@code getPythonTraceback()}, {@code getCauseCode()}.
 */
class PythonExecutionExceptionTest {

    // ==================================================================
    // Constructors
    // ==================================================================

    @Test
    void constructor_messageOnly() {
        PythonExecutionException e = new PythonExecutionException("eval failed");

        assertEquals("eval failed", e.getMessage());
        assertNull(e.getPythonTraceback());
        assertNull(e.getCauseCode());
        assertNull(e.getCause());
    }

    @Test
    void constructor_messageAndTraceback() {
        PythonExecutionException e = new PythonExecutionException(
                "ValueError: bad input",
                "Traceback (most recent call last):\n  File \"<string>\", line 1\nValueError: bad input");

        assertEquals("ValueError: bad input", e.getMessage());
        assertTrue(e.getPythonTraceback().contains("Traceback"));
        assertTrue(e.getPythonTraceback().contains("ValueError"));
        assertNull(e.getCauseCode());
    }

    @Test
    void constructor_messageAndCause() {
        IOException cause = new IOException("pipe broken");
        PythonExecutionException e = new PythonExecutionException(
                "exec failed: pipe broken", cause);

        assertEquals("exec failed: pipe broken", e.getMessage());
        assertSame(cause, e.getCause());
        assertNull(e.getPythonTraceback());
        assertNull(e.getCauseCode());
    }

    @Test
    void constructor_fullFields() {
        PythonExecutionException e = new PythonExecutionException(
                "TypeError: unsupported operand",
                "Traceback (most recent call last):\n  File \"<string>\", line 3\nTypeError",
                "eval(refId=5, code=1 + 'x')");

        assertEquals("TypeError: unsupported operand", e.getMessage());
        assertTrue(e.getPythonTraceback().contains("Traceback"));
        assertEquals("eval(refId=5, code=1 + 'x')", e.getCauseCode());
    }

    @Test
    void constructor_withNullTracebackAndNullCauseCode() {
        PythonExecutionException e = new PythonExecutionException("error", null, null);

        assertEquals("error", e.getMessage());
        assertNull(e.getPythonTraceback());
        assertNull(e.getCauseCode());
    }

    // ==================================================================
    // wrap()
    // ==================================================================

    @Test
    void wrap_pythonExecutionException_returnsAsIs() {
        PythonExecutionException original = new PythonExecutionException(
                "NameError: name 'x' is not defined",
                "Traceback...",
                "eval(1, x)");
        PythonExecutionException wrapped = PythonExecutionException.wrap("eval", original);

        assertSame(original, wrapped);
        assertEquals("NameError: name 'x' is not defined", wrapped.getMessage());
        assertEquals("Traceback...", wrapped.getPythonTraceback());
        assertEquals("eval(1, x)", wrapped.getCauseCode());
    }

    @Test
    void wrap_ioException_wrapsWithMessage() {
        IOException cause = new IOException("Broken pipe");
        PythonExecutionException wrapped = PythonExecutionException.wrap("exec", cause);

        assertEquals("exec failed: Broken pipe", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
        assertNull(wrapped.getPythonTraceback());
        assertNull(wrapped.getCauseCode());
    }

    @Test
    void wrap_timeoutException_wrapsWithMessage() {
        TimeoutException cause = new TimeoutException("timed out after 5000ms");
        PythonExecutionException wrapped = PythonExecutionException.wrap("call", cause);

        assertEquals("call failed: timed out after 5000ms", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
    }

    @Test
    void wrap_runtimeException_wrapsWithMessage() {
        RuntimeException cause = new RuntimeException("unexpected state");
        PythonExecutionException wrapped = PythonExecutionException.wrap("getattr", cause);

        assertEquals("getattr failed: unexpected state", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
    }

    // ==================================================================
    // getPythonTraceback
    // ==================================================================

    @Test
    void getPythonTraceback_returnsNull_whenNotProvided() {
        PythonExecutionException e = new PythonExecutionException("error");
        assertNull(e.getPythonTraceback());
    }

    @Test
    void getPythonTraceback_returnsMultilineTraceback() {
        String tb = "Traceback (most recent call last):\n"
                + "  File \"<string>\", line 1, in <module>\n"
                + "  File \"<string>\", line 3, in compute\n"
                + "ZeroDivisionError: division by zero";

        PythonExecutionException e = new PythonExecutionException(
                "ZeroDivisionError: division by zero", tb);

        assertEquals(tb, e.getPythonTraceback());
    }

    // ==================================================================
    // getCauseCode
    // ==================================================================

    @Test
    void getCauseCode_returnsNull_whenNotProvided() {
        PythonExecutionException e = new PythonExecutionException("error",
                "Traceback...");
        assertNull(e.getCauseCode());
    }

    @Test
    void getCauseCode_returnsProvidedCode() {
        PythonExecutionException e = new PythonExecutionException(
                "error", "tb", "exec(refId=3, code=print(x))");
        assertEquals("exec(refId=3, code=print(x))", e.getCauseCode());
    }

    // ==================================================================
    // Exception chaining
    // ==================================================================

    @Test
    void exceptionChain_causeIsPreserved() {
        IOException io = new IOException("pipe closed");
        PythonExecutionException e = new PythonExecutionException(
                "write failed: pipe closed", io);

        assertSame(io, e.getCause());
        assertEquals("write failed: pipe closed", e.getMessage());
    }

    @Test
    void exceptionChain_fromProtocol() {
        // Simulate protocol error format: message, fullTraceback, causeCode
        PythonExecutionException e = new PythonExecutionException(
                "TypeError: 'int' object is not callable",
                "Traceback (most recent call last):\n"
                        + "  File \"<string>\", line 100, in handle_call\n"
                        + "TypeError: 'int' object is not callable",
                "call(refId=1, method=get_value)");

        assertEquals("TypeError: 'int' object is not callable", e.getMessage());
        assertNotNull(e.getPythonTraceback());
        assertEquals("call(refId=1, method=get_value)", e.getCauseCode());
        assertNull(e.getCause()); // no chained exception from protocol path
    }
}
