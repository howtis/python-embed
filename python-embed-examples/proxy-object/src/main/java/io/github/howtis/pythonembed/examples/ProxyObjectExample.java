package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;

/**
 * Demonstrates Java dynamic proxies for Python objects.
 * <p>
 * Python objects are wrapped as Java interfaces via {@link PythonEmbed#proxy(String, Class)}.
 * Method names are automatically converted from Java camelCase to Python snake_case
 * when no exact match exists.
 */
public class ProxyObjectExample {

    // Define a Java interface matching the Python object's methods
    interface Calculator {
        int add(int a, int b);
        int multiply(int a, int b);
    }

    interface StringTools {
        String greet(String name);
        String shout(String text);
    }

    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            // ---- Basic proxy ----
            py.exec("""
                    class Calculator:
                        def add(self, a, b):
                            return a + b
                        def multiply(self, a, b):
                            return a * b
                    calc = Calculator()
                    """);

            Calculator calc = py.proxy("calc", Calculator.class);
            System.out.println("add(3, 4)       = " + calc.add(3, 4));
            System.out.println("multiply(5, 6)  = " + calc.multiply(5, 6));

            // ---- camelCase to snake_case auto-conversion ----
            py.exec("""
                    class StringTools:
                        def greet(self, name):
                            return f"Hello, {name}!"
                        def shout(self, text):
                            return text.upper() + "!!!"
                    tools = StringTools()
                    """);

            StringTools tools = py.proxy("tools", StringTools.class);
            System.out.println(tools.greet("World"));
            System.out.println(tools.shout("hello"));

            // ---- Proxy with snake_case conversion demo ----
            py.exec("""
                    class DataStore:
                        def get_user_name(self):
                            return "Alice"
                        def get_user_age(self):
                            return 30
                    store = DataStore()
                    """);

            interface DataStore {
                String getUserName();
                int getUserAge();
            }

            DataStore store = py.proxy("store", DataStore.class);
            System.out.println("userName = " + store.getUserName());
            System.out.println("userAge  = " + store.getUserAge());

            System.out.println("\nAll proxy operations completed.");
        }
    }
}
