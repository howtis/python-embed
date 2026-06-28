# Contributing to PythonEmbed

Thank you for your interest in contributing! Here's how to get started.

## Development Setup

```bash
git clone https://github.com/howtis/python-embed.git
cd python-embed
./gradlew build
```

Requirements: JDK 17+, Python 3.8+ (auto-downloaded if absent).

## Running Tests

```bash
./gradlew test
```

## Pull Request Process

1. Fork the repository and create a feature branch.
2. Make your changes and add tests where appropriate.
3. Run `./gradlew build` to verify everything passes.
4. Submit a pull request with a clear description of your changes.

## Code Style

- Follow existing code style in each module.
- Keep changes focused — one feature or fix per PR.
- Match the existing comment frequency in the codebase.

## Reporting Issues

Use the [GitHub Issues](https://github.com/howtis/python-embed/issues) page. Include:

- PythonEmbed version
- Java and Python versions
- Steps to reproduce
- Expected vs. actual behavior
