package io.github.howtis.pythonembed.maven;

import org.apache.maven.plugin.logging.Log;

/**
 * Minimal in-memory {@link Log} implementation for testing.
 */
final class TestLog implements Log {
    private final StringBuilder info = new StringBuilder();
    private final StringBuilder warn = new StringBuilder();
    private final StringBuilder error = new StringBuilder();

    boolean containsInfo(String text) {
        return info.toString().contains(text);
    }

    @Override public boolean isDebugEnabled() { return false; }
    @Override public void debug(CharSequence content) {}
    @Override public void debug(CharSequence content, Throwable error) {}
    @Override public void debug(Throwable error) {}
    @Override public void info(CharSequence content) { info.append(content).append('\n'); }
    @Override public void info(CharSequence content, Throwable t) { info.append(content).append('\n'); }
    @Override public void info(Throwable t) { info.append(t.getMessage()).append('\n'); }
    @Override public void warn(CharSequence content) { warn.append(content).append('\n'); }
    @Override public void warn(CharSequence content, Throwable t) { warn.append(content).append('\n'); }
    @Override public void warn(Throwable t) { warn.append(t.getMessage()).append('\n'); }
    @Override public void error(CharSequence content) { error.append(content).append('\n'); }
    @Override public void error(CharSequence content, Throwable t) { error.append(content).append('\n'); }
    @Override public void error(Throwable t) { error.append(t.getMessage()).append('\n'); }
    @Override public boolean isInfoEnabled() { return true; }
    @Override public boolean isWarnEnabled() { return true; }
    @Override public boolean isErrorEnabled() { return true; }
}
