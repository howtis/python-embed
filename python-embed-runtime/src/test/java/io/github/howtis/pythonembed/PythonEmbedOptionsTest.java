package io.github.howtis.pythonembed;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonEmbedOptionsTest {

    // ---- defaults() ----

    @Test
    void defaults_returnsSensibleDefaults() {
        PythonEmbed.Options opts = PythonEmbed.Options.defaults();

        assertEquals(30_000, opts.timeoutMs());
        assertEquals(100_000, opts.maxCodeLength());
        assertEquals(30_000, opts.startupTimeoutMs());
        assertNull(opts.pythonExecutable());
        assertTrue(opts.warmupScripts().isEmpty());
        assertTrue(opts.lenientWarmup());
        assertNull(opts.venvPath());
        assertTrue(opts.env().isEmpty());
        assertTrue(opts.beforeCloseHooks().isEmpty());
        assertTrue(opts.afterCloseHooks().isEmpty());
    }

    // ---- builder() ----

    @Test
    void builder_returnsNonNull() {
        assertNotNull(PythonEmbed.Options.builder());
    }

    // ---- timeoutMs ----

    @Test
    void builder_timeoutMs_setsValue() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .timeoutMs(60_000)
                .build();

        assertEquals(60_000, opts.timeoutMs());
    }

    @Test
    void builder_timeoutMs_defaultValue() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder().build();

        assertEquals(30_000, opts.timeoutMs());
    }

    // ---- maxCodeLength ----

    @Test
    void builder_maxCodeLength_setsValue() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .maxCodeLength(50_000)
                .build();

        assertEquals(50_000, opts.maxCodeLength());
    }

    // ---- startupTimeoutMs ----

    @Test
    void builder_startupTimeoutMs_setsValue() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .startupTimeoutMs(120_000)
                .build();

        assertEquals(120_000, opts.startupTimeoutMs());
    }

    // ---- pythonExecutable ----

    @Test
    void builder_pythonExecutable_setsValue() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .pythonExecutable("/usr/bin/python3.12")
                .build();

        assertEquals("/usr/bin/python3.12", opts.pythonExecutable());
    }

    @Test
    void builder_pythonExecutable_defaultNull() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder().build();

        assertNull(opts.pythonExecutable());
    }

    // ---- warmupScript (singular) ----

    @Test
    void builder_warmupScript_appends() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .warmupScript("import math")
                .warmupScript("import json")
                .build();

        assertEquals(List.of("import math", "import json"), opts.warmupScripts());
    }

    @Test
    void builder_warmupScript_emptyByDefault() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder().build();

        assertTrue(opts.warmupScripts().isEmpty());
    }

    // ---- warmupScripts (plural / list) ----

    @Test
    void builder_warmupScripts_addsAll() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .warmupScripts(List.of("import math", "import json", "import os"))
                .build();

        assertEquals(3, opts.warmupScripts().size());
        assertEquals(List.of("import math", "import json", "import os"), opts.warmupScripts());
    }

    @Test
    void builder_warmupScripts_appendsToList() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .warmupScript("import math")
                .warmupScripts(List.of("import json", "import os"))
                .build();

        assertEquals(List.of("import math", "import json", "import os"), opts.warmupScripts());
    }

    @Test
    void builder_warmupScripts_emptyListSafe() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .warmupScripts(List.of())
                .build();

        assertTrue(opts.warmupScripts().isEmpty());
    }

    // ---- lenientWarmup ----

    @Test
    void builder_lenientWarmup_defaultTrue() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder().build();

        assertTrue(opts.lenientWarmup());
    }

    @Test
    void builder_lenientWarmup_setFalse() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .lenientWarmup(false)
                .build();

        assertFalse(opts.lenientWarmup());
    }

    // ---- venvPath ----

    @Test
    void builder_venvPath_setsValue() {
        Path path = Path.of("/opt/venv");
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .venvPath(path)
                .build();

        assertEquals(path, opts.venvPath());
    }

    @Test
    void builder_venvPath_defaultNull() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder().build();

        assertNull(opts.venvPath());
    }

    // ---- env ----

    @Test
    void builder_env_setsValue() {
        Map<String, String> env = Map.of("CUDA_VISIBLE_DEVICES", "0", "PYTHONPATH", "/custom");
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .env(env)
                .build();

        assertEquals(env, opts.env());
    }

    @Test
    void builder_env_emptyByDefault() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder().build();

        assertTrue(opts.env().isEmpty());
    }

    @Test
    void builder_env_overwritesPrevious() {
        Map<String, String> first = Map.of("A", "1");
        Map<String, String> second = Map.of("B", "2");
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .env(first)
                .env(second)
                .build();

        assertEquals(second, opts.env());
        assertEquals(1, opts.env().size());
        assertEquals("2", opts.env().get("B"));
    }

    // ---- onBeforeClose ----

    @Test
    void builder_onBeforeClose_addsHook() {
        CloseHook hook = (embed, reason) -> {};
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .onBeforeClose(hook)
                .build();

        assertEquals(1, opts.beforeCloseHooks().size());
    }

    @Test
    void builder_onBeforeClose_multipleHooks() {
        CloseHook hook1 = (embed, reason) -> {};
        CloseHook hook2 = (embed, reason) -> {};
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .onBeforeClose(hook1)
                .onBeforeClose(hook2)
                .build();

        assertEquals(2, opts.beforeCloseHooks().size());
    }

    @Test
    void builder_onBeforeClose_emptyByDefault() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder().build();

        assertTrue(opts.beforeCloseHooks().isEmpty());
    }

    // ---- onAfterClose ----

    @Test
    void builder_onAfterClose_addsHook() {
        CloseHook hook = (embed, reason) -> {};
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .onAfterClose(hook)
                .build();

        assertEquals(1, opts.afterCloseHooks().size());
    }

    @Test
    void builder_onAfterClose_emptyByDefault() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder().build();

        assertTrue(opts.afterCloseHooks().isEmpty());
    }

    // ---- build() immutability ----

    @Test
    void build_createsImmutableCopyOfWarmupScripts() {
        PythonEmbed.Options.Builder builder = PythonEmbed.Options.builder()
                .warmupScript("import math");
        PythonEmbed.Options opts = builder.build();

        // Modifying the builder after build() should not affect the Options
        builder.warmupScript("import json");

        assertEquals(1, opts.warmupScripts().size());
        assertEquals(List.of("import math"), opts.warmupScripts());
    }

    @Test
    void build_createsImmutableCopyOfHooks() {
        PythonEmbed.Options.Builder builder = PythonEmbed.Options.builder()
                .onBeforeClose((embed, reason) -> {});
        PythonEmbed.Options opts = builder.build();

        builder.onBeforeClose((embed, reason) -> {});

        assertEquals(1, opts.beforeCloseHooks().size());
    }

    // ---- Builder chainability ----

    @Test
    void builder_isChainable() {
        PythonEmbed.Options opts = PythonEmbed.Options.builder()
                .timeoutMs(60_000)
                .maxCodeLength(50_000)
                .startupTimeoutMs(120_000)
                .pythonExecutable("/usr/bin/python3")
                .warmupScript("import math")
                .warmupScripts(List.of("import json"))
                .lenientWarmup(false)
                .venvPath(Path.of("/opt/venv"))
                .env(Map.of("KEY", "VAL"))
                .onBeforeClose((embed, reason) -> {})
                .onAfterClose((embed, reason) -> {})
                .build();

        assertEquals(60_000, opts.timeoutMs());
        assertEquals(50_000, opts.maxCodeLength());
        assertEquals(120_000, opts.startupTimeoutMs());
        assertEquals("/usr/bin/python3", opts.pythonExecutable());
        assertEquals(List.of("import math", "import json"), opts.warmupScripts());
        assertFalse(opts.lenientWarmup());
        assertEquals(Path.of("/opt/venv"), opts.venvPath());
        assertEquals(Map.of("KEY", "VAL"), opts.env());
        assertEquals(1, opts.beforeCloseHooks().size());
        assertEquals(1, opts.afterCloseHooks().size());
    }

    // ---- Builder multiple builds ----

    @Test
    void builder_multipleBuilds_produceIndependentOptions() {
        PythonEmbed.Options.Builder builder = PythonEmbed.Options.builder()
                .timeoutMs(10_000);

        PythonEmbed.Options opts1 = builder.build();
        PythonEmbed.Options opts2 = builder.timeoutMs(20_000).build();

        assertEquals(10_000, opts1.timeoutMs());
        assertEquals(20_000, opts2.timeoutMs());
    }
}
