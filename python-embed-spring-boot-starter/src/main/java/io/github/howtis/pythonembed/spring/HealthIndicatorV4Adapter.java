package io.github.howtis.pythonembed.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Adapts a {@link PythonEmbedHealthIndicator} to Spring Boot 4.x
 * {@code org.springframework.boot.health.contributor.HealthIndicator} via reflection.
 *
 * <p>Because the starter compiles against Spring Boot 3.x, Boot 4.x types are not
 * available at compile time. This adapter uses {@link Proxy} and reflection to
 * bridge the gap at runtime when Boot 4.x is on the classpath.
 *
 * <p>Registered by {@link PythonEmbedAutoConfiguration.HealthIndicatorConfigurationV4}.
 */
final class HealthIndicatorV4Adapter {

    private static final Logger log = LoggerFactory.getLogger(HealthIndicatorV4Adapter.class);

    static final String HEALTH_INDICATOR_CLASS =
            "org.springframework.boot.health.contributor.HealthIndicator";
    static final String HEALTH_CLASS =
            "org.springframework.boot.health.contributor.Health";

    private HealthIndicatorV4Adapter() {
    }

    /**
     * Creates a dynamic proxy implementing Boot 4.x {@code HealthIndicator},
     * delegating to the given {@link PythonEmbedHealthIndicator}.
     */
    static Object create(PythonEmbedHealthIndicator delegate) {
        try {
            Class<?> healthIndicatorClass = Class.forName(HEALTH_INDICATOR_CLASS);
            return Proxy.newProxyInstance(
                    healthIndicatorClass.getClassLoader(),
                    new Class<?>[]{healthIndicatorClass},
                    (proxy, method, args) -> {
                        if ("health".equals(method.getName())) {
                            return buildHealth(delegate.health());
                        }
                        return method.invoke(proxy, args);
                    });
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Spring Boot 4.x HealthIndicator not found on classpath", e);
        }
    }

    private static Object buildHealth(PythonEmbedHealthIndicator.HealthData data) {
        try {
            Class<?> healthClass = Class.forName(HEALTH_CLASS);
            Method upMethod = healthClass.getMethod("up");
            Method downMethod = healthClass.getMethod("down");
            Method withDetailMethod = healthClass.getMethod("withDetail", String.class, Object.class);
            Method withExceptionMethod = healthClass.getMethod("withException", Throwable.class);
            Method buildMethod = healthClass.getMethod("build");

            Object builder;
            if (data.up()) {
                builder = upMethod.invoke(null);
            } else {
                builder = downMethod.invoke(null);
                if (data.error() != null) {
                    builder = withExceptionMethod.invoke(builder, data.error());
                }
            }
            for (Map.Entry<String, Object> entry : data.details().entrySet()) {
                builder = withDetailMethod.invoke(builder, entry.getKey(), entry.getValue());
            }
            return buildMethod.invoke(builder);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Spring Boot 4.x Health class not found on classpath", e);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Failed to reflectively access Boot 4.x Health API", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(
                    "Boot 4.x Health builder threw an exception", cause);
        }
    }
}
