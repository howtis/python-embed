package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.HealthInfo;
import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import io.github.howtis.pythonembed.PythonExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PythonEmbedHealthIndicatorTest {

    @Test
    void singleModeUpWhenHealthy() {
        var embed = mock(PythonEmbed.class);
        when(embed.health()).thenReturn(new HealthInfo(1024, 0, true, List.of(1, 2, 3)));

        var indicator = new PythonEmbedHealthIndicator.Single(embed);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("memoryRssKb", 1024L);
        assertThat(health.getDetails()).containsEntry("refCount", 0);
        assertThat(health.getDetails()).containsEntry("gcEnabled", true);
        assertThat(health.getDetails()).containsEntry("gcCounts", List.of(1, 2, 3));
    }

    @Test
    void singleModeDownWhenException() {
        var embed = mock(PythonEmbed.class);
        when(embed.health()).thenThrow(new PythonExecutionException("test failure"));

        var indicator = new PythonEmbedHealthIndicator.Single(embed);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void poolModeUpWhenSizeAtLeastMinPool() {
        var pool = mock(PythonEmbedPool.class);
        when(pool.size()).thenReturn(2);
        when(pool.minPool()).thenReturn(2);
        when(pool.activeCount()).thenReturn(1);

        var indicator = new PythonEmbedHealthIndicator.Pool(pool);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("size", 2);
        assertThat(health.getDetails()).containsEntry("minPool", 2);
        assertThat(health.getDetails()).containsEntry("activeCount", 1);
    }

    @Test
    void poolModeDownWhenSizeBelowMinPool() {
        var pool = mock(PythonEmbedPool.class);
        when(pool.size()).thenReturn(0);
        when(pool.minPool()).thenReturn(1);
        when(pool.activeCount()).thenReturn(0);

        var indicator = new PythonEmbedHealthIndicator.Pool(pool);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void poolModeUpWhenSizeExceedsMinPool() {
        var pool = mock(PythonEmbedPool.class);
        when(pool.size()).thenReturn(4);
        when(pool.minPool()).thenReturn(2);
        when(pool.activeCount()).thenReturn(3);

        var indicator = new PythonEmbedHealthIndicator.Pool(pool);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void poolModeDownWhenException() {
        var pool = mock(PythonEmbedPool.class);
        when(pool.size()).thenThrow(new RuntimeException("pool unavailable"));

        var indicator = new PythonEmbedHealthIndicator.Pool(pool);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
