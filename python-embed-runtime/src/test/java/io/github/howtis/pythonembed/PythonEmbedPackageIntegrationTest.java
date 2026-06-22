package io.github.howtis.pythonembed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for commonly used Python ecosystem packages.
 * Verifies that python-embed correctly handles packages with C extensions
 * (numpy, pandas, scipy) and pure-Python packages (requests, pyyaml, python-dateutil).
 */
class PythonEmbedPackageIntegrationTest {

    private static PythonEmbed py;

    @BeforeAll
    static void setUp() {
        py = PythonEmbed.create(PythonEmbed.Options.builder()
                .venvPath(Path.of("build", "python-venv")).build());
    }

    @AfterAll
    static void tearDown() {
        py.close();
    }

    @BeforeEach
    void clearState() {
        py.exec("globals().clear()");
    }

    // ==================================================================
    // numpy
    // ==================================================================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_importAndVersion() {
        py.exec("import numpy as np");
        String version = py.eval("np.__version__").asString();
        assertNotNull(version);
        assertFalse(version.isEmpty());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_createArrayAndSum() {
        py.exec("import numpy as np");
        int result = py.eval("np.sum(np.array([1, 2, 3, 4, 5])).item()").asInt();
        assertEquals(15, result);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_arangeAndMean() {
        py.exec("import numpy as np");
        double result = py.eval("np.mean(np.arange(1, 11)).item()").asDouble();
        assertEquals(5.5, result, 0.001);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_zerosAndShape() {
        py.exec("import numpy as np");
        py.exec("arr = np.zeros((3, 4))");
        PythonValue shape = py.eval("arr.shape");
        @SuppressWarnings("unchecked")
        List<Object> shapeList = shape.asList();
        assertEquals(2, shapeList.size());
        assertEquals(3, ((Number) shapeList.get(0)).intValue());
        assertEquals(4, ((Number) shapeList.get(1)).intValue());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_handleRefAndCall() {
        py.exec("import numpy as np");
        py.exec("arr = np.array([10, 20, 30])");
        try (PythonHandle handle = py.ref("arr")) {
            assertEquals("ndarray", handle.pythonType());
            // call tolist() which returns a msgpack-serializable list
            PythonValue listResult = handle.call("tolist");
            List<Double> result = listResult.asList(Double.class);
            assertEquals(List.of(10.0, 20.0, 30.0), result);
        }
        // verify sum via eval with int() conversion
        assertEquals(60, py.eval("int(arr.sum())").asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_handleGetAttr() {
        py.exec("import numpy as np");
        py.exec("arr = np.array([[1, 2], [3, 4]])");
        try (PythonHandle handle = py.ref("arr")) {
            PythonValue dtype = handle.getAttr("dtype");
            assertTrue(dtype.asString().contains("int"));
            PythonValue ndim = handle.getAttr("ndim");
            assertEquals(2, ndim.asInt());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_elementWiseOperation() {
        py.exec("import numpy as np");
        py.exec("a = np.array([1, 2, 3])");
        py.exec("b = np.array([4, 5, 6])");
        List<Double> result = py.eval("(a + b).tolist()").asList(Double.class);
        assertEquals(List.of(5.0, 7.0, 9.0), result);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_linspace() {
        py.exec("import numpy as np");
        List<Double> result = py.eval("np.linspace(0, 1, 5).tolist()").asList(Double.class);
        assertEquals(5, result.size());
        assertEquals(0.0, result.get(0), 0.001);
        assertEquals(1.0, result.get(4), 0.001);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_randomWithSeed() {
        py.exec("import numpy as np");
        py.exec("np.random.seed(42)");
        double result = py.eval("np.random.rand()").asDouble();
        assertTrue(result >= 0.0 && result < 1.0);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void numpy_errorShapeMismatch() {
        py.exec("import numpy as np");
        py.exec("a = np.array([1, 2, 3])");
        py.exec("b = np.array([[1, 2], [3, 4]])");
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.eval("a + b"));
        assertTrue(ex.getMessage().contains("ValueError")
                || ex.getMessage().contains("broadcast"));
    }

    // ==================================================================
    // pandas
    // ==================================================================

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pandas_importAndVersion() {
        py.exec("import pandas as pd");
        String version = py.eval("pd.__version__").asString();
        assertNotNull(version);
        assertFalse(version.isEmpty());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pandas_createDataFrame() {
        py.exec("import pandas as pd");
        py.exec("df = pd.DataFrame({'a': [1, 2, 3], 'b': [4, 5, 6]})");
        List<Double> colA = py.eval("df['a'].tolist()").asList(Double.class);
        assertEquals(List.of(1.0, 2.0, 3.0), colA);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pandas_describe() {
        py.exec("import pandas as pd");
        py.exec("df = pd.DataFrame({'x': [10, 20, 30, 40, 50]})");
        double mean = py.eval("df['x'].mean()").asDouble();
        assertEquals(30.0, mean, 0.001);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pandas_handleRef() {
        py.exec("import pandas as pd");
        py.exec("df = pd.DataFrame({'name': ['alice', 'bob'], 'score': [95, 87]})");
        try (PythonHandle handle = py.ref("df")) {
            assertEquals("DataFrame", handle.pythonType());
            PythonValue shape = handle.getAttr("shape");
            @SuppressWarnings("unchecked")
            List<Object> shapeList = shape.asList();
            assertEquals(2, shapeList.size());
            assertEquals(2, ((Number) shapeList.get(0)).intValue());
            assertEquals(2, ((Number) shapeList.get(1)).intValue());
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pandas_filterAndAggregate() {
        py.exec("import pandas as pd");
        py.exec("df = pd.DataFrame({'x': [1, 2, 3, 4, 5]})");
        // pandas sum() returns numpy.int64 - convert with int()
        int result = py.eval("int(df[df['x'] > 2]['x'].sum())").asInt();
        assertEquals(12, result);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pandas_errorColumnNotFound() {
        py.exec("import pandas as pd");
        py.exec("df = pd.DataFrame({'a': [1, 2]})");
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.eval("df['nonexistent']"));
        assertTrue(ex.getMessage().contains("KeyError"));
    }

    // ==================================================================
    // scipy
    // ==================================================================

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void scipy_importAndVersion() {
        py.exec("import scipy");
        String version = py.eval("scipy.__version__").asString();
        assertNotNull(version);
        assertFalse(version.isEmpty());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void scipy_statsDescriptive() {
        py.exec("from scipy import stats");
        py.exec("data = [1, 2, 2, 3, 3, 3, 4, 4, 5]");
        // stats.mode returns numpy.int64 - convert with int()
        int mode = py.eval("int(stats.mode(data).mode)").asInt();
        assertEquals(3, mode);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void scipy_integrate() {
        py.exec("from scipy import integrate");
        py.exec("import numpy as np");
        double result = py.eval(
                "integrate.quad(lambda x: x**2, 0, 1)[0]").asDouble();
        assertEquals(1.0 / 3.0, result, 0.001);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void scipy_linalg() {
        py.exec("from scipy import linalg");
        py.exec("import numpy as np");
        py.exec("a = np.array([[1, 2], [3, 4]])");
        double det = py.eval("linalg.det(a)").asDouble();
        assertEquals(-2.0, det, 0.001);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void scipy_optimize() {
        py.exec("from scipy import optimize");
        py.exec("import numpy as np");
        py.exec("result = optimize.minimize(lambda x: (x - 3)**2, x0=0)");
        double x = py.eval("result.x[0]").asDouble();
        assertEquals(3.0, x, 0.05);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void scipy_errorSingularMatrix() {
        py.exec("from scipy import linalg");
        py.exec("import numpy as np");
        py.exec("a = np.array([[1, 2], [2, 4]])");
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.eval("linalg.inv(a)"));
        assertTrue(ex.getMessage().contains("LinAlgError")
                || ex.getMessage().contains("singular"));
    }

    // ==================================================================
    // requests
    // ==================================================================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void requests_importAndVersion() {
        py.exec("import requests");
        String version = py.eval("requests.__version__").asString();
        assertNotNull(version);
        assertFalse(version.isEmpty());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void requests_createSession() {
        py.exec("import requests");
        py.exec("s = requests.Session()");
        try (PythonHandle handle = py.ref("s")) {
            assertEquals("Session", handle.pythonType());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void requests_prepareRequest() {
        py.exec("import requests");
        py.exec("req = requests.Request('GET', 'https://httpbin.org/get')");
        py.exec("prepared = req.prepare()");
        String url = py.eval("prepared.url").asString();
        assertEquals("https://httpbin.org/get", url);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void requests_buildRequest() {
        py.exec("import requests");
        py.exec("req = requests.Request('POST', 'https://example.com/api', "
                + "data={'key': 'value'}, headers={'Authorization': 'Bearer token'})");
        py.exec("prepared = req.prepare()");
        assertEquals("POST", py.eval("prepared.method").asString());
        assertEquals("https://example.com/api", py.eval("prepared.url").asString());
        String body = py.eval("prepared.body").asString();
        assertTrue(body.contains("key"));
        assertTrue(body.contains("value"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void requests_errorInvalidUrl() {
        py.exec("import requests");
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.exec("requests.get('not-a-valid-url://', timeout=1)"));
        assertTrue(ex.getMessage().contains("InvalidURL")
                || ex.getMessage().contains("MissingSchema")
                || ex.getMessage().contains("InvalidSchema"));
    }

    // ==================================================================
    // pyyaml
    // ==================================================================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void yaml_import() {
        py.exec("import yaml");
        // Verify module is importable by using it
        py.exec("data = yaml.safe_load('key: value')");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void yaml_parseSimple() {
        py.exec("import yaml");
        py.exec("data = yaml.safe_load('name: test\\nversion: 1.0')");
        String name = py.eval("data['name']").asString();
        assertEquals("test", name);
        double version = py.eval("data['version']").asDouble();
        assertEquals(1.0, version, 0.001);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void yaml_parseList() {
        py.exec("import yaml");
        py.exec("data = yaml.safe_load('- apple\\n- banana\\n- cherry')");
        List<String> items = py.eval("data").asList(String.class);
        assertEquals(List.of("apple", "banana", "cherry"), items);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void yaml_parseNested() {
        py.exec("import yaml");
        py.exec("""
                data = yaml.safe_load('''
                server:
                  host: localhost
                  port: 8080
                ''')

                """);
        String host = py.eval("data['server']['host']").asString();
        assertEquals("localhost", host);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void yaml_dump() {
        py.exec("import yaml");
        py.exec("data = {'items': [1, 2, 3], 'enabled': True}");
        String output = py.eval("yaml.dump(data)").asString();
        assertTrue(output.contains("items"));
        assertTrue(output.contains("enabled"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void yaml_errorInvalidYaml() {
        py.exec("import yaml");
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.eval("yaml.safe_load('key: [unclosed')"));
        // Verify error propagation - full traceback contains parser error details
        assertNotNull(ex.getPythonTraceback());
        assertTrue(ex.getPythonTraceback().contains("ParserError"));
    }

    // ==================================================================
    // python-dateutil
    // ==================================================================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void dateutil_importParser() {
        py.exec("from dateutil import parser");
        String result = py.eval("parser.parse('2024-01-15').isoformat()").asString();
        assertTrue(result.startsWith("2024-01-15"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void dateutil_parseIsoformat() {
        py.exec("from dateutil import parser");
        String result = py.eval("parser.parse('2024-06-15T14:30:00').isoformat()").asString();
        assertTrue(result.startsWith("2024-06-15T14:30:00"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void dateutil_parseUsFormat() {
        py.exec("from dateutil import parser");
        int day = py.eval("parser.parse('01/15/2024').day").asInt();
        assertEquals(15, day);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void dateutil_relativedelta() {
        py.exec("from dateutil.relativedelta import relativedelta");
        py.exec("from datetime import datetime");
        py.exec("d1 = datetime(2024, 1, 1)");
        py.exec("d2 = datetime(2025, 3, 15)");
        py.exec("delta = relativedelta(d2, d1)");
        assertEquals(1, py.eval("delta.years").asInt());
        assertEquals(2, py.eval("delta.months").asInt());
        assertEquals(14, py.eval("delta.days").asInt());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void dateutil_parseWithTimezone() {
        py.exec("from dateutil import parser");
        String result = py.eval("str(parser.parse('2024-01-15T10:00:00+09:00').tzinfo)").asString();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void dateutil_errorUnparseableDate() {
        py.exec("from dateutil import parser");
        PythonExecutionException ex = assertThrows(PythonExecutionException.class,
                () -> py.eval("parser.parse('not a date at all')"));
        assertTrue(ex.getMessage().contains("ParserError"));
    }

    // ==================================================================
    // Mixed package scenarios
    // ==================================================================

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void mixed_numpyToPandas() {
        py.exec("import numpy as np");
        py.exec("import pandas as pd");
        py.exec("arr = np.array([[1, 4], [2, 5], [3, 6]])");
        py.exec("df = pd.DataFrame(arr, columns=['a', 'b'])");
        // pandas sum() returns numpy.int64 - convert with int()
        int sumA = py.eval("int(df['a'].sum())").asInt();
        assertEquals(6, sumA);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void mixed_scipyStatsOnDataFrame() {
        py.exec("import numpy as np");
        py.exec("import pandas as pd");
        py.exec("from scipy import stats");
        py.exec("df = pd.DataFrame({'x': [1, 2, 3, 4, 5]})");
        double ttest = py.eval(
                "stats.ttest_1samp(df['x'], 3.0).statistic").asDouble();
        assertEquals(0.0, ttest, 0.001);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void mixed_pandasDescribeToYaml() {
        py.exec("import pandas as pd");
        py.exec("import yaml");
        py.exec("df = pd.DataFrame({'a': [1, 2, 3]})");
        int count = py.eval("len(df)").asInt();
        // Round-trip: DataFrame -> dict -> YAML -> dict
        py.exec("data_dict = {'count': len(df), 'mean': float(df['a'].mean())}");
        py.exec("yaml_str = yaml.dump(data_dict)");
        py.exec("parsed = yaml.safe_load(yaml_str)");
        assertEquals(count, py.eval("parsed['count']").asInt());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void stream_numpyArange() {
        py.exec("import numpy as np");
        Iterator<PythonValue> stream = py.stream("np.arange(5)");
        int sum = 0;
        int count = 0;
        while (stream.hasNext()) {
            sum += stream.next().asInt();
            count++;
        }
        assertEquals(5, count);
        assertEquals(10, sum); // 0+1+2+3+4
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void batchEval_numpyOperations() {
        py.exec("import numpy as np");
        py.exec("arr = np.array([10, 20, 30, 40, 50])");
        List<PythonValue> results = py.batchEval(List.of(
                "arr.sum().item()",
                "arr.mean().item()",
                "arr.min().item()",
                "arr.max().item()"
        ));
        assertEquals(4, results.size());
        assertEquals(150, results.get(0).asInt());
        assertEquals(30.0, results.get(1).asDouble(), 0.001);
        assertEquals(10, results.get(2).asInt());
        assertEquals(50, results.get(3).asInt());
    }
}
