# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

Please report security vulnerabilities privately to the repository maintainer via GitHub's [Security Advisories](https://github.com/howtis/python-embed/security/advisories/new) page.

Do not open a public issue for security vulnerabilities.

## Scope

- The subprocess isolation between CPython and JVM
- MessagePack protocol serialization/deserialization
- Build plugin behavior (Python installation, package management)

## Out of Scope

- Vulnerabilities in CPython itself — report those to the [Python Security team](https://www.python.org/about/security/)
- Vulnerabilities in third-party Python packages (numpy, scipy, etc.)
