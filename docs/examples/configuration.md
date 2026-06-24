# Configuration

Demonstrates the full `PythonEmbed.Options` builder: timeouts, environment variables, warmup scripts, code limits, close hooks, and explicit Python executable path.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/configuration/src/main/java/io/github/howtis/pythonembed/examples/ConfigurationExample.java)

## Key Points

- `timeoutMs()` / `startupTimeoutMs()` — per-request and startup timeouts
- `maxCodeLength()` — safety limit on code size
- `env()` — pass environment variables to the Python process
- `warmupScript()` / `warmupScripts()` — pre-execute on startup
- `lenientWarmup()` — don't fail if warmup scripts have errors
- `onBeforeClose()` / `onAfterClose()` — lifecycle hooks

## Run It

```bash
./gradlew :python-embed-examples:configuration:run
```
