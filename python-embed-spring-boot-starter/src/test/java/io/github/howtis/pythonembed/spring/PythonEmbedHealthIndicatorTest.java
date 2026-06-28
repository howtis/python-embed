package io.github.howtis.pythonembed.spring;

import io.github.howtis.pythonembed.HealthInfo;
import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonEmbedPool;
import io.github.howtis.pythonembed.PythonExecutionException;
import org.junit.jupiter.api.Test;

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
        var data = indicator.health();

        assertThat(data.up()).isTrue();
        assertThat(data.error()).isNull();
        assertThat(data.details()).containsEntry("memoryRssKb", 1024L);
        assertThat(data.details()).containsEntry("refCount", 0);
        assertThat(data.details()).containsEntry("gcEnabled", true);
        assertThat(data.details()).containsEntry("gcCounts", List.of(1, 2, 3));
    }

    @Test
    void singleModeDownWhenException() {
        var embed = mock(PythonEmbed.class);
        when(embed.health()).thenThrow(new PythonExecutionException("test failure"));

        var indicator = new PythonEmbedHealthIndicator.Single(embed);
        var data = indicator.health();

        assertThat(data.up()).isFalse();
        assertThat(data.error()).isNotNull();
        assertThat(data.error().getMessage()).contains("test failure");
    }

    @Test
    void poolModeUpWhenSizeAtLeastMinPool() {
        var pool = mock(PythonEmbedPool.class);
        when(pool.size()).thenReturn(2);
        when(pool.minPool()).thenReturn(2);
        when(pool.activeCount()).thenReturn(1);

        var indicator = new PythonEmbedHealthIndicator.Pool(pool);
        var data = indicator.health();

        assertThat(data.up()).isTrue();
        assertThat(data.details()).containsEntry("size", 2);
        assertThat(data.details()).containsEntry("minPool", 2);
        assertThat(data.details()).containsEntry("activeCount", 1);
    }

    @Test
    void poolModeDownWhenSizeBelowMinPool() {
        var pool = mock(PythonEmbedPool.class);
        when(pool.size()).thenReturn(0);
        when(pool.minPool()).thenReturn(1);
        when(pool.activeCount()).thenReturn(0);

        var indicator = new PythonEmbedHealthIndicator.Pool(pool);
        var data = indicator.health();

        assertThat(data.up()).isFalse();
    }

    @Test
    void poolModeUpWhenSizeExceedsMinPool() {
        var pool = mock(PythonEmbedPool.class);
        when(pool.size()).thenReturn(4);
        when(pool.minPool()).thenReturn(2);
        when(pool.activeCount()).thenReturn(3);

        var indicator = new PythonEmbedHealthIndicator.Pool(pool);
        var data = indicator.health();

        assertThat(data.up()).isTrue();
    }

    @Test
    void poolModeDownWhenException() {
        var pool = mock(PythonEmbedPool.class);
        when(pool.size()).thenThrow(new RuntimeException("pool unavailable"));

        var indicator = new PythonEmbedHealthIndicator.Pool(pool);
        var data = indicator.health();

        assertThat(data.up()).isFalse();
        assertThat(data.error()).isNotNull();
    }
}
