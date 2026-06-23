package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonValue;

import java.util.Iterator;

/**
 * Demonstrates Python generator streaming via {@link PythonEmbed#stream(String)}.
 * <p>
 * Python generators are consumed lazily through a Java {@link Iterator}, avoiding
 * memory explosion even for large datasets.
 */
public class StreamingExample {

    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            // ---- Basic generator stream ----
            System.out.println("range(10):");
            Iterator<PythonValue> stream = py.stream("range(10)");
            while (stream.hasNext()) {
                System.out.print(stream.next().asInt() + " ");
            }
            System.out.println();

            // ---- Fibonacci generator ----
            py.exec("""
                    def fibonacci(n):
                        a, b = 0, 1
                        for _ in range(n):
                            yield a
                            a, b = b, a + b
                    """);
            System.out.println("fibonacci(15):");
            Iterator<PythonValue> fib = py.stream("fibonacci(15)");
            while (fib.hasNext()) {
                System.out.print(fib.next().asInt() + " ");
            }
            System.out.println();

            // ---- Large stream without memory pressure ----
            System.out.println("First 5 from 1_000_000-item range: ");
            Iterator<PythonValue> large = py.stream("range(1_000_000)");
            for (int i = 0; i < 5 && large.hasNext(); i++) {
                System.out.print(large.next().asInt() + " ");
            }
            System.out.println("... (lazy, no memory explosion)");

            // ---- String generator ----
            py.exec("""
                    def words(text):
                        for word in text.split():
                            yield word.upper()
                    """);
            System.out.println("words generator:");
            Iterator<PythonValue> words = py.stream("words('hello world from python')");
            while (words.hasNext()) {
                System.out.println("  " + words.next().asString());
            }

            System.out.println("\nAll streaming operations completed.");
        }
    }
}
