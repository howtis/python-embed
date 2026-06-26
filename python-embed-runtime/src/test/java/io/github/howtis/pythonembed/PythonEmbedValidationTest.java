package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonEmbedValidationTest {

    private static PythonEmbed py;

    @BeforeAll
    static void setUp() {
        py = PythonEmbed.create(PythonEmbed.Options.defaults());
    }

    @AfterAll
    static void tearDown() {
        py.close();
    }

    // ---- eval(String code) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.eval(null)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_blankCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> py.eval(""));
        assertThrows(IllegalArgumentException.class, () -> py.eval("   "));
    }

    // ---- eval(String code, long timeoutMs) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_withTimeout_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.eval(null, 1000)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    // ---- exec(String code) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.exec(null)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_blankCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> py.exec(""));
        assertThrows(IllegalArgumentException.class, () -> py.exec("   "));
    }

    // ---- exec(String code, long timeoutMs) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_withTimeout_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.exec(null, 1000)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    // ---- execFile(Path scriptPath) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void execFile_nullPath_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.execFile(null)
        );
        assertTrue(ex.getMessage().contains("scriptPath must not be null"));
    }

    // ---- execFile(Path scriptPath, long timeoutMs) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void execFile_withTimeout_nullPath_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.execFile(null, 1000)
        );
        assertTrue(ex.getMessage().contains("scriptPath must not be null"));
    }

    // ---- eval(Map<String, Object> variables, String code) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_withVariables_nullVariables_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.eval(null, "1 + 1")
        );
        assertTrue(ex.getMessage().contains("variables must not be null"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_withVariables_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.eval(Map.of(), null)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    // ---- eval(Map<String, Object> variables, String code, long timeoutMs) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void eval_withVariablesAndTimeout_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.eval(Map.of(), null, 1000)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    // ---- exec(Map<String, Object> variables, String code) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_withVariables_nullVariables_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.exec(null, "x = 1")
        );
        assertTrue(ex.getMessage().contains("variables must not be null"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_withVariables_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.exec(Map.of(), null)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    // ---- exec(Map<String, Object> variables, String code, long timeoutMs) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void exec_withVariablesAndTimeout_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.exec(Map.of(), null, 1000)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    // ---- warmup(String script) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void warmup_nullScript_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.warmup(null)
        );
        assertTrue(ex.getMessage().contains("script must not be null or blank"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void warmup_blankScript_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> py.warmup(""));
        assertThrows(IllegalArgumentException.class, () -> py.warmup("   "));
    }

    // ---- batchEval(List<String> codes) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void batchEval_nullList_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.batchEval(null)
        );
        assertTrue(ex.getMessage().contains("codes must not be null"));
    }

    // ---- batchExec(List<String> codes) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void batchExec_nullList_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.batchExec(null)
        );
        assertTrue(ex.getMessage().contains("codes must not be null"));
    }

    // ---- stream(String code) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_nullCode_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.stream(null)
        );
        assertTrue(ex.getMessage().contains("code must not be null or blank"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void stream_blankCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> py.stream(""));
        assertThrows(IllegalArgumentException.class, () -> py.stream("   "));
    }

    // ---- ref(String variableName) ----

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ref_nullVariableName_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> py.ref(null)
        );
        assertTrue(ex.getMessage().contains("variableName must not be null or blank"));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void ref_blankVariableName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> py.ref(""));
        assertThrows(IllegalArgumentException.class, () -> py.ref("   "));
    }
}
