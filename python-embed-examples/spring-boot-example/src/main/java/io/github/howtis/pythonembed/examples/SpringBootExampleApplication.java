package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Demonstrates Spring Boot auto-configuration for python-embed.
 *
 * <p>Starts a web server with a REST controller ({@link PythonController})
 * and Actuator health endpoint backed by {@code PythonEmbedHealthIndicator}.
 */
@SpringBootApplication
public class SpringBootExampleApplication {

    private static final Logger log = LoggerFactory.getLogger(SpringBootExampleApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(SpringBootExampleApplication.class, args);
        SpringApplication.exit(ctx, () -> 0);
    }

    @Bean
    CommandLineRunner demo(PythonEmbed pythonEmbed) {
        return args -> {
            log.info("Auto-configured PythonEmbed bean: {}", pythonEmbed);
            pythonEmbed.exec("import sys");
            log.info("Python version: {}", pythonEmbed.eval("sys.version").asString());
            log.info("Ping: {}", pythonEmbed.ping());
        };
    }
}
