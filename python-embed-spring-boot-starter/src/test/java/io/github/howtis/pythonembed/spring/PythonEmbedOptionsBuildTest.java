package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.PythonEmbed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PythonEmbedAutoConfiguration#buildOptions(PythonEmbedProperties)}.
 *
 * <p>Verifies all property-to-option mappings.
 */
class PythonEmbedOptionsBuildTest {

    @Test
    void venvPathNullUsesNoExplicitPath() {
        var props = bind(Map.of());
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.venvPath()).isNull();
    }

    @Test
    void venvPathBlankUsesNoExplicitPath() {
        var props = bind(Map.of("python-embed.venv-path", "  "));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.venvPath()).isNull();
    }

    @Test
    void venvPathSetToCustomPath(@TempDir Path tempDir) {
        var props = bind(Map.of("python-embed.venv-path", tempDir.toString()));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.venvPath()).isEqualTo(tempDir);
    }

    @Test
    void timeoutMsZeroFallsBackToBuilderDefault() {
        var props = bind(Map.of());
        props.getOptions().setTimeoutMs(0);
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.timeoutMs()).isEqualTo(30_000L);
    }

    @Test
    void timeoutMsPositiveIsSet() {
        var props = bind(Map.of("python-embed.options.timeout-ms", "5000"));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.timeoutMs()).isEqualTo(5000L);
    }

    @Test
    void maxCodeLengthDefault() {
        var props = bind(Map.of());
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.maxCodeLength()).isEqualTo(100_000);
    }

    @Test
    void maxCodeLengthCustom() {
        var props = bind(Map.of("python-embed.options.max-code-length", "50000"));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.maxCodeLength()).isEqualTo(50_000);
    }

    @Test
    void startupTimeoutMsDefault() {
        var props = bind(Map.of());
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.startupTimeoutMs()).isEqualTo(30_000L);
    }

    @Test
    void startupTimeoutMsCustom() {
        var props = bind(Map.of("python-embed.options.startup-timeout-ms", "10000"));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.startupTimeoutMs()).isEqualTo(10_000L);
    }

    @Test
    void pythonExecutableNullByDefault() {
        var props = bind(Map.of());
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.pythonExecutable()).isNull();
    }

    @Test
    void pythonExecutableCustom() {
        var props = bind(Map.of("python-embed.options.python-executable", "/usr/bin/python3.12"));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.pythonExecutable()).isEqualTo("/usr/bin/python3.12");
    }

    @Test
    void warmupScriptsEmptyByDefault() {
        var props = bind(Map.of());
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.warmupScripts()).isEmpty();
    }

    @Test
    void warmupScriptsCustom() {
        var props = bind(Map.of(
                "python-embed.options.warmup-scripts[0]", "import numpy as np",
                "python-embed.options.warmup-scripts[1]", "import torch"
        ));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.warmupScripts()).containsExactly("import numpy as np", "import torch");
    }

    @Test
    void lenientWarmupDefaultTrue() {
        var props = bind(Map.of());
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.lenientWarmup()).isTrue();
    }

    @Test
    void lenientWarmupCustomFalse() {
        var props = bind(Map.of("python-embed.options.lenient-warmup", "false"));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.lenientWarmup()).isFalse();
    }

    @Test
    void environmentVarsEmptySkipsSetting() {
        var props = bind(Map.of());
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.env()).isEmpty();
    }

    @Test
    void environmentVarsNonEmptyArePassed() {
        var props = bind(Map.of(
                "python-embed.options.environment-vars.KEY_A", "val_a",
                "python-embed.options.environment-vars.KEY_B", "val_b"
        ));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.env()).containsEntry("KEY_A", "val_a").containsEntry("KEY_B", "val_b");
    }

    private static PythonEmbedProperties bind(Map<String, String> source) {
        var binder = new Binder(new MapConfigurationPropertySource(source));
        return binder.bindOrCreate("python-embed", PythonEmbedProperties.class);
    }
}
