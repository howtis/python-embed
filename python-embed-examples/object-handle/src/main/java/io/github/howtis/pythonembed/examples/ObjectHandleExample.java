package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonHandle;
import io.github.howtis.pythonembed.PythonValue;

import java.util.List;

/**
 * Demonstrates Python object handles -- keeping Python objects in-process
 * and referencing them by numeric ID across calls.
 * <p>
 * Handles avoid serializing the entire object on every call. Instead,
 * the object stays alive in Python and is accessed via
 * {@link PythonHandle#call(String, Object...)} and
 * {@link PythonHandle#getAttr(String)}.
 */
public class ObjectHandleExample {

    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            // ---- Create a Python object and get a handle ----
            py.exec("""
                    class Counter:
                        def __init__(self, start=0):
                            self.value = start
                        def increment(self):
                            self.value += 1
                            return self.value
                        def reset(self, val=0):
                            self.value = val
                    c = Counter()
                    """);

            // ref() returns a handle without serializing the object
            PythonHandle handle = py.ref("c");
            System.out.println("Handle: refId=" + handle.refId()
                    + ", type=" + handle.pythonType());

            // call() invokes methods on the held object
            PythonValue v1 = handle.call("increment");
            PythonValue v2 = handle.call("increment");
            PythonValue v3 = handle.call("increment");
            System.out.println("After 3 increments: " + v3.asInt());

            // Call with arguments
            handle.call("reset", 100);
            PythonValue afterReset = handle.call("increment");
            System.out.println("After reset(100) + increment: " + afterReset.asInt());

            // ---- Handle for numpy array ----
            py.exec("""
                    import numpy as np
                    arr = np.array([1.0, 2.0, 3.0, 4.0, 5.0])
                    """);

            PythonHandle arrHandle = py.ref("arr");
            System.out.println("\nArray handle: refId=" + arrHandle.refId()
                    + ", type=" + arrHandle.pythonType());

            // getAttr() reads attributes
            PythonValue shape = arrHandle.getAttr("shape");
            System.out.println("arr.shape = " + shape.asList(Integer.class));

            PythonValue dtype = arrHandle.getAttr("dtype");
            System.out.println("arr.dtype = " + dtype.asString());

            // call() numpy methods
            PythonValue meanValue = arrHandle.call("mean");
            System.out.println("arr.mean() = " + meanValue.asDouble());

            double stdValue = arrHandle.call("std").asDouble();
            System.out.println("arr.std() = " + stdValue);

            // ---- Multiple handles in parallel ----
            py.exec("""
                    import collections
                    dq = collections.deque([1, 2, 3])
                    words = ["hello", "world"]
                    """);

            PythonHandle dqHandle = py.ref("dq");
            PythonHandle wordsHandle = py.ref("words");

            dqHandle.call("append", 4);
            dqHandle.call("appendleft", 0);
            List<Double> dqList = py.eval("list(dq)").asList(Double.class);
            System.out.println("\ndeque after append/appendleft: " + dqList);

            PythonValue upperWords = wordsHandle.call("__getitem__", 0);
            System.out.println("words[0] = " + upperWords.asString());

            // ---- Release handles ----
            handle.release();       // explicit release
            arrHandle.close();      // try-with-resources style (AutoCloseable)
            dqHandle.close();
            wordsHandle.close();

            System.out.println("\nAll handle operations completed.");
        }
    }
}
