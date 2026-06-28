package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.PythonEmbed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying end-to-end wiring with a real Python venv.
 *
 * <p>Requires {@code META-INF/python-embed.properties} on the classpath (provided by
 * the {@code python-embed-runtime} dependency, which applies the python-embed Gradle plugin).
 */
@SpringBootTest(
        classes = PythonEmbedIntegrationTest.TestApp.class,
        properties = {
                "python-embed.mode=SINGLE",
                "spring.main.banner-mode=off"
        }
)
class PythonEmbedIntegrationTest {

    @Autowired
    private PythonEmbed embed;

    @Autowired(required = false)
    private PythonEmbedHealthIndicator healthIndicator;

    @Test
    void pythonEmbedBeanIsCreated() {
        assertThat(embed).isNotNull();
    }

    @Test
    void healthIndicatorReportsUp() {
        assertThat(healthIndicator).isNotNull();
        var data = healthIndicator.health();
        assertThat(data.up()).isTrue();
    }

    @Test
    void canEvaluateSimpleExpression() {
        var result = embed.eval("2 + 3");
        assertThat(result.asInt()).isEqualTo(5);
    }

    @Test
    void pythonVersionAvailable() {
        embed.exec("import sys");
        var result = embed.eval("sys.version");
        assertThat(result.asString()).contains("3.");
    }

    @SpringBootApplication
    static class TestApp {
    }
}
