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
                    assertThat(ctx.containsBean("pythonEmbedHealthIndicator")).isTrue();
                    assertThat(ctx.containsBean("healthIndicatorAdapter")).isTrue();
                });
    }

    @Test
    void poolModeHealthIndicatorCreatedWithActuator() {
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .withPropertyValues("python-embed.mode=POOL")
                .run(ctx -> {
                    assertThat(ctx.containsBean("pythonEmbedPoolHealthIndicator")).isTrue();
                    assertThat(ctx.containsBean("healthIndicatorPoolAdapter")).isTrue();
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
        contextRunner
                .withUserConfiguration(MockConfig.class, UserHealthIndicatorConfig.class)
                .withPropertyValues("python-embed.mode=SINGLE")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
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
            return new PythonEmbedHealthIndicator.Single(mock(PythonEmbed.class));
        }
    }
}
