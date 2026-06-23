package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller demonstrating auto-configured {@link PythonEmbed} bean
 * injection and usage.
 */
@RestController
public class PythonController {

    private final PythonEmbed pythonEmbed;

    public PythonController(PythonEmbed pythonEmbed) {
        this.pythonEmbed = pythonEmbed;
    }

    /**
     * Evaluates a Python expression and returns the result.
     *
     * <pre>
     * GET /eval?expr=2+2        → 4
     * GET /eval?expr=len('abc') → 3
     * </pre>
     */
    @GetMapping("/eval")
    public Map<String, Object> eval(@RequestParam String expr) {
        String result;
        try {
            var value = pythonEmbed.eval(expr);
            result = value.toJson();
        } catch (Exception e) {
            result = "Error: " + e.getMessage();
        }
        return Map.of("expression", expr, "result", result);
    }

    /**
     * Executes a Python statement.
     *
     * <pre>
     * GET /exec?stmt=x=42
     * GET /eval?expr=x          → 42  (shared namespace)
     * </pre>
     */
    @GetMapping("/exec")
    public Map<String, Object> exec(@RequestParam String stmt) {
        try {
            pythonEmbed.exec(stmt);
            return Map.of("statement", stmt, "status", "ok");
        } catch (Exception e) {
            return Map.of("statement", stmt, "status", "error", "message", e.getMessage());
        }
    }

    /**
     * Returns Python interpreter information.
     */
    @GetMapping("/info")
    public Map<String, String> info() {
        pythonEmbed.exec("import sys");
        String version = pythonEmbed.eval("sys.version").asString();
        String executable = pythonEmbed.eval("sys.executable").asString();
        return Map.of("version", version, "executable", executable);
    }
}
