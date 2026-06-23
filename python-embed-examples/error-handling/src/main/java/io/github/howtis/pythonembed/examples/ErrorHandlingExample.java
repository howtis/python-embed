package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonExecutionException;

/**
 * Demonstrates Python error handling with detailed diagnostic information.
 * <p>
 * Every Python exception is wrapped as a {@link PythonExecutionException} providing
 * the error type, cause code, and full Python traceback -- just like you'd see
 * in a Python console.
 */
public class ErrorHandlingExample {

    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            // ---- SyntaxError ----
            System.out.println("=== SyntaxError ===");
            try {
                py.eval("1 + ");
            } catch (PythonExecutionException e) {
                printException(e);
            }

            // ---- NameError ----
            System.out.println("=== NameError ===");
            try {
                py.eval("undefined_var");
            } catch (PythonExecutionException e) {
                printException(e);
            }

            // ---- ZeroDivisionError ----
            System.out.println("=== ZeroDivisionError ===");
            try {
                py.eval("1 / 0");
            } catch (PythonExecutionException e) {
                printException(e);
            }

            // ---- TypeError ----
            System.out.println("=== TypeError ===");
            try {
                py.eval("1 + 'hello'");
            } catch (PythonExecutionException e) {
                printException(e);
            }

            // ---- ValueError with exec ----
            System.out.println("=== ValueError (from exec) ===");
            try {
                py.exec("int('not a number')");
            } catch (PythonExecutionException e) {
                printException(e);
            }

            // ---- RuntimeError (custom) ----
            System.out.println("=== RuntimeError (custom) ===");
            py.exec("""
                    def validate_age(age):
                        if age < 0:
                            raise ValueError(f"Invalid age: {age}")
                        return age
                    """);
            try {
                py.eval("validate_age(-5)");
            } catch (PythonExecutionException e) {
                printException(e);
            }

            // ---- ImportError ----
            System.out.println("=== ImportError ===");
            try {
                py.exec("import nonexistent_module_xyz");
            } catch (PythonExecutionException e) {
                printException(e);
            }

            // ---- Conditional error handling ----
            System.out.println("=== Conditional handling ===");
            boolean isSyntax = isSyntaxError(py, "1 + ");
            System.out.println("isSyntaxError(\"1 + \") = " + isSyntax);
            boolean isNotSyntax = isSyntaxError(py, "'hello'");
            System.out.println("isSyntaxError(\"'hello'\") = " + isNotSyntax);

            System.out.println("\nAll error handling demonstrations completed.");
        }
    }

    /** Check if code contains a Python syntax error without throwing. */
    private static boolean isSyntaxError(PythonEmbed py, String code) {
        try {
            py.eval(code);
            return false;
        } catch (PythonExecutionException e) {
            return "SyntaxError".equals(e.getPythonErrorType());
        }
    }

    /** Print full diagnostic info from a PythonExecutionException. */
    private static void printException(PythonExecutionException e) {
        System.out.println("  errorType:  " + e.getPythonErrorType());
        System.out.println("  causeCode:  " + e.getCauseCode());
        System.out.println("  message:    " + e.getMessage());
        System.out.println("  traceback:");
        System.out.println(e.getPythonTraceback());
        System.out.println();
    }
}
