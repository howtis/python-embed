# Spring Boot

Zero-code Spring Boot 3.x integration with auto-configuration and Actuator health checks.

## Setup

Add the starter dependency:

```groovy
dependencies {
    implementation 'io.github.howtis:python-embed-spring-boot-starter:1.0.2'
}
```

No Java configuration needed — the starter auto-configures everything.

## Configuration

Configure via `application.yml`:

```yaml
python-embed:
  mode: SINGLE          # or POOL
  venv-path: /opt/venv  # optional override
  pool:
    min: 2
    max: 8
    idle-timeout: 60s
    health-check-interval: 30s
    close-timeout: 30s
  options:
    timeout-ms: 30000
    environment-vars:
      CUDA_VISIBLE_DEVICES: "0"
```

## Modes

### SINGLE Mode

Injects a single `PythonEmbed` bean. Best for sequential workloads:

```java
@RestController
public class PythonController {
    private final PythonEmbed py;

    public PythonController(PythonEmbed py) {
        this.py = py;
    }

    @GetMapping("/eval")
    public Map<String, Object> eval(@RequestParam String expr) {
        return Map.of("result", py.eval(expr).toJson());
    }
}
```

### POOL Mode

Injects a `PythonEmbedPool` bean. Best for concurrent workloads:

```java
@RestController
public class AsyncPythonController {
    private final PythonEmbedPool pool;

    public AsyncPythonController(PythonEmbedPool pool) {
        this.pool = pool;
    }

    @GetMapping("/async-eval")
    public CompletableFuture<Map<String, Object>> eval(@RequestParam String expr) {
        return pool.eval(expr)
                .thenApply(v -> Map.of("result", v.toJson()));
    }
}
```

## Health Check

Both modes register an Actuator `HealthIndicator`. Enable details in `application.yml`:

```yaml
management:
  endpoint:
    health:
      show-details: always
```

Access at `/actuator/health`:

```json
{
  "status": "UP",
  "components": {
    "pythonEmbed": {
      "status": "UP",
      "details": {
        "pid": 12345,
        "running": true,
        "rssMB": 45.2,
        "refCount": 3,
        "gcGen": 1,
        "pool": {
          "size": 3,
          "active": 1,
          "minPool": 2,
          "maxPool": 8
        }
      }
    }
  }
}
```

## Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `python-embed.mode` | `SINGLE` / `POOL` | `SINGLE` | Bean type to create |
| `python-embed.venv-path` | `String` | auto-resolved | Override venv location |
| `python-embed.pool.min` | `int` | 2 | Minimum pool size |
| `python-embed.pool.max` | `int` | 8 | Maximum pool size |
| `python-embed.pool.idle-timeout` | `Duration` | 60s | Idle instance removal |
| `python-embed.pool.health-check-interval` | `Duration` | 30s | Health ping interval |
| `python-embed.pool.close-timeout` | `Duration` | 30s | Graceful shutdown timeout |
| `python-embed.options.timeout-ms` | `long` | 30000 | Per-request timeout |
| `python-embed.options.startup-timeout-ms` | `long` | 10000 | Process startup timeout |
| `python-embed.options.environment-vars` | `Map` | — | Environment variables |
