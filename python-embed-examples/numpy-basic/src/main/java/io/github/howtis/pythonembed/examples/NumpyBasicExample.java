package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonValue;

import java.util.List;

/**
 * Demonstrates numpy integration - CPython C extension support.
 * PythonEmbed runs real CPython, so all C extensions work natively.
 */
public class NumpyBasicExample {

    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            // Import numpy
            py.exec("import numpy as np");

            // Array creation
            PythonValue arr = py.eval("np.arange(10).tolist()");
            List<Double> values = arr.asList(Double.class);
            System.out.println("np.arange(10) = " + values);

            // Basic operations
            double mean = py.eval("np.mean(np.arange(1, 11))").asDouble();
            System.out.println("mean(1..10)   = " + mean);

            double std = py.eval("np.std([2, 4, 6, 8])").asDouble();
            System.out.println("std([2,4,6,8]) = " + std);

            // Matrix operations
            py.exec("""
                    a = np.array([[1, 2], [3, 4]])
                    b = np.array([[5, 6], [7, 8]])
                    c = a @ b
                    """);
            Object matrix = py.eval("c.tolist()").asList();
            System.out.println("matrix multiply: " + matrix);

            // Linear algebra
            double det = py.eval("float(np.linalg.det(c))").asDouble();
            System.out.println("det(c) = " + det);

            // Random number generation
            py.exec("rng = np.random.default_rng(42)");
            List<Double> random = py.eval("rng.random(5).tolist()").asList(Double.class);
            System.out.println("random(5) = " + random);

            // Broadcasting
            py.exec("x = np.array([1, 2, 3])");
            List<Double> broadcast = py.eval("(x + 10).tolist()").asList(Double.class);
            System.out.println("broadcast  = " + broadcast);

            System.out.println("\nAll numpy operations completed.");
        }
    }
}
