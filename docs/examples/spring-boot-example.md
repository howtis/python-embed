# Spring Boot Example

Demonstrates Spring Boot 3.x auto-configuration with PythonEmbed starter. Shows zero-code integration, REST endpoint serving Python evaluation, and Actuator health checks.

[:fontawesome-brands-github: View source - Application](https://github.com/howtis/python-embed/blob/main/python-embed-examples/spring-boot-example/src/main/java/io/github/howtis/pythonembed/examples/SpringBootExampleApplication.java)
[:fontawesome-brands-github: View source - Controller](https://github.com/howtis/python-embed/blob/main/python-embed-examples/spring-boot-example/src/main/java/io/github/howtis/pythonembed/examples/PythonController.java)

## Key Points

- Zero-code setup: just add the starter dependency and configure `application.yml`
- SINGLE mode injects `PythonEmbed` bean via `@Autowired`
- REST endpoint evaluates arbitrary Python expressions
- Actuator health endpoint at `/actuator/health`
- Random port configuration (`server.port=0`) to avoid conflicts

## Run It

```bash
./gradlew :python-embed-examples:spring-boot-example:run
```
