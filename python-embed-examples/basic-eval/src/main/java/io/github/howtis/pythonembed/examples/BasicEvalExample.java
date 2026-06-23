package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonValue;

import java.util.List;
import java.util.Map;

/**
 * Demonstrates basic PythonEmbed usage: eval, exec, arg injection,
 * and PythonValue type conversion.
 */
public class BasicEvalExample {

    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            // ---- eval: evaluate expressions ----
            PythonValue result = py.eval("sum([1, 2, 3])");
            System.out.println("eval sum([1,2,3]) = " + result.asInt());

            // ---- exec: execute statements (shared namespace) ----
            py.exec("x = 42");
            py.exec("""
                    def greet(name):
                        return f"Hello, {name}!"
                    """);
            System.out.println("x = " + py.eval("x").asInt());
            System.out.println(py.eval("greet('World')").asString());

            // ---- PythonValue type conversion ----
            System.out.println("bool: " + py.eval("1 + 1 == 2").asBoolean());
            System.out.println("double: " + py.eval("3.14 * 2").asDouble());
            System.out.println("string: " + py.eval("'hello'.upper()").asString());

            // List result
            List<Double> list = py.eval("[i * 2 for i in range(5)]").asList(Double.class);
            System.out.println("list: " + list);

            // ---- arg(): safe parameter injection ----
            String userInput = "Bob'; import os; os.system('rm -rf /') #";
            py.eval("len(" + PythonEmbed.arg(userInput) + ")");
            System.out.println("arg(null)  = " + py.eval(PythonEmbed.arg(null)).asString());
            System.out.println("arg(42)    = " + py.eval(PythonEmbed.arg(42)).asInt());
            System.out.println("arg(true)  = " + py.eval(PythonEmbed.arg(true)).asBoolean());
            System.out.println("arg('hi')  = " + py.eval(PythonEmbed.arg("hi")).asString());

            Object nested = py.eval(
                    PythonEmbed.arg(Map.of("name", "Alice", "scores", List.of(95, 87, 92)))
            ).asMap(String.class, Object.class);
            System.out.println("arg(map)   = " + nested);

            System.out.println("\nAll basic operations completed.");
        }
    }
}
