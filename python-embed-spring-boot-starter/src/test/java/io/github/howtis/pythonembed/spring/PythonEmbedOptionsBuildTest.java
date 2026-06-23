package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.PythonEmbed;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PythonEmbedAutoConfiguration#buildOptions(PythonEmbedProperties)}.
 *
 * <p>Verifies the 3 branching paths: venvPath, timeoutMs, environmentVars.
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
    void venvPathSetToCustomPath() {
        var props = bind(Map.of("python-embed.venv-path", "/opt/custom-venv"));
        var options = PythonEmbedAutoConfiguration.buildOptions(props);
        assertThat(options.venvPath()).isEqualTo(Path.of("/opt/custom-venv"));
    }

    @Test
    void timeoutMsZeroFallsBackToBuilderDefault() {
        // timeoutMs == 0 → buildOptions skips setting it, builder default (30_000) is used
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
