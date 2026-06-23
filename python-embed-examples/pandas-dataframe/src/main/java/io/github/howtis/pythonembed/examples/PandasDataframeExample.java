package io.github.howtis.pythonembed.examples;

import io.github.howtis.pythonembed.PythonEmbed;
import io.github.howtis.pythonembed.PythonValue;

import java.util.List;
import java.util.Map;

/**
 * Demonstrates Pandas DataFrame integration -- creating, transforming, and
 * analyzing tabular data via Python from Java.
 * <p>
 * Since PythonEmbed uses real CPython, Pandas and all its optimized C
 * extensions work natively with no configuration beyond listing it in
 * the Gradle plugin's {@code packages} block.
 */
public class PandasDataframeExample {

    public static void main(String[] args) {
        try (PythonEmbed py = PythonEmbed.create()) {
            py.exec("import pandas as pd");

            // ---- Creating DataFrames ----
            System.out.println("=== Creating DataFrames ===");

            // From Python dict
            py.exec("""
                    df = pd.DataFrame({
                        'name':    ['Alice', 'Bob', 'Charlie', 'Diana', 'Eve'],
                        'age':     [25, 30, 35, 28, 32],
                        'score':   [88.5, 92.0, 85.5, 95.0, 78.0],
                        'city':    ['Seoul', 'Busan', 'Seoul', 'Incheon', 'Busan']
                    })
                    """);

            PythonValue shapeValue = py.eval("df.shape");
            List<Integer> shape = shapeValue.asList(Integer.class);
            System.out.println("Shape: " + shape.get(0) + " rows x " + shape.get(1) + " cols");

            PythonValue columns = py.eval("list(df.columns)");
            System.out.println("Columns: " + columns.asList(String.class));

            // ---- Basic inspection ----
            System.out.println("\n=== Basic Inspection ===");

            List<Map<String, Object>> preview = (List) py.eval("df.head(3).to_dict('records')").asList();
            System.out.println("First 3 rows:");
            for (Map<String, Object> row : preview) {
                System.out.println("  " + row);
            }

            // ---- Filtering ----
            System.out.println("\n=== Filtering ===");

            py.exec("high_scorers = df[df['score'] > 90.0]");
            List<String> highNames = py.eval("high_scorers['name'].tolist()").asList(String.class);
            System.out.println("High scorers (>90): " + highNames);

            py.exec("seoul_residents = df[df['city'] == 'Seoul']");
            int seoulCount = py.eval("len(seoul_residents)").asInt();
            System.out.println("Seoul residents: " + seoulCount);

            // ---- Aggregation ----
            System.out.println("\n=== Aggregation ===");

            double avgScore = py.eval("float(df['score'].mean())").asDouble();
            System.out.println("Average score: " + String.format("%.1f", avgScore));

            int maxAge = py.eval("int(df['age'].max())").asInt();
            System.out.println("Max age: " + maxAge);

            // Group by city
            List<Map<String, Object>> cityStats = (List) py.eval(
                    "df.groupby('city').agg(avg_score=('score', 'mean'), count=('name', 'count'))"
                            + ".reset_index().to_dict('records')"
            ).asList();
            System.out.println("\nGroup by city:");
            for (Map<String, Object> stat : cityStats) {
                System.out.println("  " + stat);
            }

            // ---- Sorting ----
            System.out.println("\n=== Sorting ===");

            py.exec("sorted_df = df.sort_values('score', ascending=False)");
            List<String> topByScore = py.eval("sorted_df['name'].tolist()").asList(String.class);
            System.out.println("Sorted by score (desc): " + topByScore);

            // ---- I/O simulation (DataFrame to/from Java) ----
            System.out.println("\n=== Java <-> DataFrame Round-trip ===");

            // Java data -> DataFrame -> Python computation -> Java result
            // Use arg() to safely inject Java data as a Python literal
            List<Map<String, Object>> javaData = List.of(
                    Map.of("product", "A", "price", 100, "quantity", 5),
                    Map.of("product", "B", "price", 200, "quantity", 3),
                    Map.of("product", "C", "price", 150, "quantity", 8)
            );

            py.exec("sales_records = " + PythonEmbed.arg(javaData));
            py.exec("sales_df = pd.DataFrame(sales_records)");

            py.exec("sales_df['total'] = sales_df['price'] * sales_df['quantity']");
            PythonValue totalRevenue = py.eval("int(sales_df['total'].sum())");
            System.out.println("Revenue from Java data: " + totalRevenue.asInt());

            // ---- Statistical summary ----
            System.out.println("\n=== describe() ===");

            List<Map<String, Object>> desc = (List) py.eval(
                    "df.describe().reset_index().to_dict('records')"
            ).asList();
            for (Map<String, Object> row : desc) {
                System.out.println("  " + row.get("index") + ": " + row);
            }

            System.out.println("\nAll Pandas operations completed.");
        }
    }
}
