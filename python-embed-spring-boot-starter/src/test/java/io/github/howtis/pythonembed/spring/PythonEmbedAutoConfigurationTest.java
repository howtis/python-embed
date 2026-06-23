package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PythonEmbedAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PythonEmbedAutoConfiguration.class));

    /**
     * Provides mock {@link PythonEmbed} and {@link PythonEmbedPool} beans so the
     * auto-configuration's {@code @ConditionalOnMissingBean} prevents actual
     * Python process creation (which would fail without a Python installation).
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        PythonEmbed mockPythonEmbed() {
            return mock(PythonEmbed.class);
        }

        @Bean
        @Primary
        PythonEmbedPool mockPythonEmbedPool() {
            return mock(PythonEmbedPool.class);
        }
    }

    @Test
    void contextLoadsInSingleMode() {
        // Mock beans prevent real PythonEmbed/PythonEmbedPool creation
        // (real creation would fail without Python installed).
        // The context should load without errors.
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .withPropertyValues("python-embed.mode=SINGLE")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.containsBean("mockPythonEmbed")).isTrue();
                });
    }

    @Test
    void contextLoadsInPoolMode() {
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .withPropertyValues("python-embed.mode=POOL")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.containsBean("mockPythonEmbedPool")).isTrue();
                });
    }

    @Test
    void singleModeHealthIndicatorCreatedWithActuator() {
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .withPropertyValues("python-embed.mode=SINGLE")
                .run(ctx -> {
                    // Actuator IS on test classpath (spring-boot-starter-actuator)
                    assertThat(ctx.containsBean("pythonEmbedHealthIndicator")).isTrue();
                });
    }

    @Test
    void poolModeHealthIndicatorCreatedWithActuator() {
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .withPropertyValues("python-embed.mode=POOL")
                .run(ctx -> {
                    assertThat(ctx.containsBean("pythonEmbedPoolHealthIndicator")).isTrue();
                });
    }

    @Test
    void poolCloserRegisteredInPoolMode() {
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .withPropertyValues("python-embed.mode=POOL")
                .run(ctx -> {
                    assertThat(ctx.containsBean("pythonEmbedPoolCloser")).isTrue();
                });
    }

    @Test
    void poolCloserNotRegisteredInSingleMode() {
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .withPropertyValues("python-embed.mode=SINGLE")
                .run(ctx -> {
                    assertThat(ctx.containsBean("pythonEmbedPoolCloser")).isFalse();
                });
    }
}
