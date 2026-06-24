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

**SINGLE mode:**

```json
{
  "status": "UP",
  "components": {
    "pythonEmbed": {
      "status": "UP",
      "details": {
        "memoryRssKb": 46284,
        "refCount": 3,
        "gcEnabled": true,
        "gcCounts": [120, 5, 1]
      }
    }
  }
}
```

**POOL mode:**

```json
{
  "status": "UP",
  "components": {
    "pythonEmbed": {
      "status": "UP",
      "details": {
        "size": 3,
        "minPool": 2,
        "activeCount": 1
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
| `python-embed.pool.min` | `int` | 1 | Minimum pool size |
| `python-embed.pool.max` | `int` | 1 | Maximum pool size |
| `python-embed.pool.idle-timeout` | `Duration` | 60s | Idle instance removal |
| `python-embed.pool.health-check-interval` | `Duration` | 30s | Health ping interval |
| `python-embed.pool.close-timeout` | `Duration` | 30s | Graceful shutdown timeout |
| `python-embed.options.timeout-ms` | `long` | 0 | Per-request timeout (0 = use PythonEmbed default: 30000) |
| `python-embed.options.startup-timeout-ms` | `long` | 30000 | Process startup timeout |
| `python-embed.options.max-code-length` | `int` | 100000 | Maximum code length in chars |
| `python-embed.options.python-executable` | `String` | — | Override Python executable path |
| `python-embed.options.warmup-scripts` | `List<String>` | — | Warmup scripts to run on startup |
| `python-embed.options.lenient-warmup` | `boolean` | true | Log warmup failures instead of throwing |
| `python-embed.options.environment-vars` | `Map` | — | Environment variables passed to Python process |
