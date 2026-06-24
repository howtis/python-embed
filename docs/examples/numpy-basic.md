# Numpy Basic

Demonstrates NumPy integration — CPython C extension support. PythonEmbed runs real CPython, so all C extensions work natively.

[:fontawesome-brands-github: View source](https://github.com/howtis/python-embed/blob/main/python-embed-examples/numpy-basic/src/main/java/io/github/howtis/pythonembed/examples/NumpyBasicExample.java)

## Key Points

- Real CPython means all C extensions work: numpy, scipy, torch, matplotlib
- Array creation, operations, broadcasting
- Matrix multiplication (`@` operator)
- Linear algebra (`np.linalg`)
- Random number generation
- Results returned to Java as typed lists

## Run It

```bash
./gradlew :python-embed-examples:numpy-basic:run
```
