# Maven Example

Demonstrates how to use PythonEmbed with the Maven plugin (`python-embed-maven-plugin`).

## What It Shows

- Maven plugin configuration in `pom.xml`
- Auto-venv setup with `python-embed:setup` goal
- Basic `eval()` and `exec()` calls
- NumPy usage via installed packages

## pom.xml

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

## Running

```bash
cd python-embed-examples/maven-example
mvn compile exec:java
```

The `setup` goal runs automatically during the `generate-resources` phase, so you don't need to call it manually.

## Source

```java
try (PythonEmbed py = PythonEmbed.create()) {
    py.exec("x = 42");
    int x = py.eval("x * 2").asInt();
    System.out.println("42 * 2 = " + x);

    py.exec("import numpy as np");
    PythonValue result = py.eval("[int(x) for x in np.linspace(0, 10, 2)]");
    System.out.println("numpy.linspace(0, 10, 2): " + result.asList());
}
```

See [Installation](../installation.md) for detailed Maven configuration.
