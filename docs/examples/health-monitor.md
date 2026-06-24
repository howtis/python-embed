# Health Monitor

Demonstrates health monitoring for PythonEmbed instances and pools. Use `ping()` for liveness checks and `health()` for detailed runtime statistics (memory, reference count, GC status).

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/health-monitor/src/main/java/io/github/howtis/pythonembed/examples/HealthMonitorExample.java)

## Key Points

- `ping()` — quick liveness check (returns boolean)
- `health()` — detailed `HealthInfo` with RSS memory, ref count, GC status
- `HealthInfo.memoryRssKb()` — resident memory in KB
- `HealthInfo.refCount()` — number of active Python object handles
- `HealthInfo.gcEnabled()` / `gcCounts()` — Python GC state
- `pool.healthCheck()` — batch health check on all idle instances

## Run It

```bash
./gradlew :python-embed-examples:health-monitor:run
```
