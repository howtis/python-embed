[English](README.md) | [한국어](README.ko.md)

# PythonEmbed

[![CI](https://github.com/howtis/python-embed/actions/workflows/ci.yml/badge.svg)](https://github.com/howtis/python-embed/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.howtis/python-embed-runtime)](https://central.sonatype.com/artifact/io.github.howtis/python-embed-runtime)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.howtis.python-embed)](https://plugins.gradle.org/plugin/io.github.howtis.python-embed)
[![codecov](https://codecov.io/gh/howtis/python-embed/graph/badge.svg?token=CODECOV_TOKEN)](https://codecov.io/gh/howtis/python-embed)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Java에서 Python 코드를 실행하세요 — 완전한 CPython 호환성, 설정 불필요.

PythonEmbed는 서브프로세스 + [MessagePack](https://msgpack.org/) 바이너리 프로토콜을 통해 실제 CPython 인터프리터를 JVM 애플리케이션에 내장합니다. 순수 Java와 CPython만으로 동작하며, 모든 C 확장(numpy, scipy, torch)이 그대로 작동합니다.

> :books: **전체 문서**: [howtis.github.io/python-embed](https://howtis.github.io/python-embed)

## 빠른 시작

=== "Gradle"

    `build.gradle`에 Gradle 플러그인을 추가하세요:

    ```groovy
    plugins {
        id 'io.github.howtis.python-embed' version '1.0.2'
    }

    pythonEmbed {
        packages = ['numpy']
    }

    dependencies {
        implementation 'io.github.howtis:python-embed-runtime:1.0.2'
    }
    ```

=== "Maven"

    `pom.xml`에 Maven 플러그인을 추가하세요:

    ```xml
    <dependencies>
        <dependency>
            <groupId>io.github.howtis</groupId>
            <artifactId>python-embed-runtime</artifactId>
            <version>1.0.2</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.github.howtis</groupId>
                <artifactId>python-embed-maven-plugin</artifactId>
                <version>1.0.2</version>
                <executions>
                    <execution>
                        <goals><goal>setup</goal></goals>
                    </execution>
                </executions>
                <configuration>
                    <packages>
                        <package>numpy</package>
                    </packages>
                </configuration>
            </plugin>
        </plugins>
    </build>
    ```

이제 Java 코드에서 사용하면 됩니다:

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("import numpy as np");
    int sum = py.eval("sum([1, 2, 3])").asInt();
    System.out.println(sum);  // 6
}
```

빌드 플러그인이 Python 설치, venv 생성, 패키지 설치를 빌드 시점에 자동으로 처리합니다.

## 주요 기능

| 분류 | 특징                                                              |
|------|-------------------------------------------------------------------|
| **호환성** | 완전한 CPython — numpy, scipy, torch, matplotlib, 모든 C 확장 지원  |
| **설정** | 수동 작업 불필요 — Gradle 플러그인이 필요 시 Python 자동 다운로드     |
| **안전성** | 서브프로세스 격리 — Python 크래시가 JVM에 영향을 주지 않음             |
| **성능** | MessagePack 바이너리 프로토콜                                       |
| **동시성** | 자동 확장 풀, 비동기 `CompletableFuture` API 지원                    |
| **통합** | Spring Boot 자동 구성, Actuator `HealthIndicator`                   |
| **상호운용** | 객체 핸들, Java 프록시, 콜백, 스트리밍, 배치 작업                     |
| **관측성** | 헬스 체크, SLF4J 로그 포워딩, 종료 훅                                |

[:books: 전체 문서 & API 레퍼런스 →](https://howtis.github.io/python-embed)

## 모듈

- **[python-embed-gradle-plugin](python-embed-gradle-plugin/)** — venv 생성, 패키지 설치, Python 자동 다운로드
- **[python-embed-maven-plugin](python-embed-maven-plugin/)** — Gradle 플러그인의 Maven 버전
- **[python-embed-runtime](python-embed-runtime/)** — 프로세스 통신, 풀 관리, 타입 변환
- **[python-embed-spring-boot-starter](python-embed-spring-boot-starter/)** — Spring Boot 3.x 자동 구성 (SINGLE/POOL 모드)
- **[python-embed-examples](python-embed-examples/)** — 13가지 실제 예제

## 빌드

```bash
./gradlew build
```

요구 사항: JDK 17+, Python 3.8+ (없으면 자동 다운로드).

## 라이선스

MIT — 자세한 내용은 [LICENSE](LICENSE)를 참고하세요.
