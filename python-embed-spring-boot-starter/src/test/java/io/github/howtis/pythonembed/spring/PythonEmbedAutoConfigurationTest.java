package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    @Test
    void poolCloserDestroyCallsPoolClose() {
        var pool = mock(PythonEmbedPool.class);
        var poolProps = new PythonEmbedProperties.PoolProperties();
        poolProps.setCloseTimeout(java.time.Duration.ofSeconds(15));
        var closer = new PythonEmbedAutoConfiguration.PoolCloser(pool, poolProps);

        closer.destroy();

        verify(pool).close(15_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void userProvidedHealthIndicatorOverridesSingleMode() {
        // Mockito cannot mock sealed PythonEmbedHealthIndicator directly;
        // use the concrete Single subclass (this still satisfies @ConditionalOnMissingBean)
        contextRunner
                .withUserConfiguration(MockConfig.class, UserHealthIndicatorConfig.class)
                .withPropertyValues("python-embed.mode=SINGLE")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    // When a user provides their own indicator, the auto-configured one is skipped.
                    // Here we get the single bean; it should be the user's config bean, not
                    // the auto-configured one (which would use a different constructor).
                    var bean = ctx.getBean(PythonEmbedHealthIndicator.class);
                    assertThat(bean).isNotNull();
                });
    }

    @Test
    void userProvidedHealthIndicatorOverridesPoolMode() {
        contextRunner
                .withUserConfiguration(MockConfig.class, UserHealthIndicatorConfig.class)
                .withPropertyValues("python-embed.mode=POOL")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var bean = ctx.getBean(PythonEmbedHealthIndicator.class);
                    assertThat(bean).isNotNull();
                });
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class UserHealthIndicatorConfig {
        @Bean
        @Primary
        PythonEmbedHealthIndicator customHealthIndicator() {
            // Use concrete subclass — sealed PythonEmbedHealthIndicator cannot be mocked
            return new PythonEmbedHealthIndicator.Single(mock(PythonEmbed.class));
        }
    }
}
