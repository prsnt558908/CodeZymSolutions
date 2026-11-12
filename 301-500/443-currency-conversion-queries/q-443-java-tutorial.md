# Currency Conversion Queries in Java

#### Problem Statement

[https://codezym.com/question/443-currency-conversion-queries](https://codezym.com/question/443-currency-conversion-queries)

Treat currencies as a graph, where each connection stores a conversion rate. A simple solution searches for a path for every query. We can improve this by choosing one reference currency in each connected group and storing every currency's rate relative to it. After that, each query needs only map lookups and one division.

## 1. Start with a graph search

For an entry such as `USD,CAD,2.0`, store both directions. The edge from USD to CAD has rate `2.0`, and the reverse edge has rate `0.5`.

To answer one query, start at the source with an amount of `1.0`. Use depth-first search, or DFS, and multiply the amount by each edge rate as you move. A visited set prevents cycles from sending the search back to currencies it has already reached.

The main traversal step looks like this. Here, `currentRate` is the amount reached from the query's source.

```java
for (Map.Entry<String, Double> edge : graph.get(current).entrySet()) {
    String next = edge.getKey();
    if (visited.add(next)) {
        rateFromSource.put(next, currentRate * edge.getValue());
        stack.push(next);
    }
}
```

Return the amount when the destination is reached. If either currency is missing, or the search finishes without reaching the destination, return `-1.0`.

With `V` currencies and `E` supplied relationships, one search takes `O(V + E)` time in the worst case. Repeating it for `Q` queries takes `O(Q × (V + E))`, even when the graph is built only once. That repeats too much work for 100,000 queries.

## 2. Store rates from one reference currency

A connected group contains currencies that can reach one another. Choose any currency in that group as its reference, also called its root.

Let `rateFromRoot[currency]` mean the number of units of that currency equal to one unit of the root.

For `USD,CAD,2.0` and `CAD,MXN,5.0`, choose USD as the root. Its stored rate is `1.0`, CAD's is `2.0`, and MXN's is `10.0`.

Now one CAD equals `10.0 / 2.0 = 5.0` MXN. The reverse query gives `2.0 / 10.0 = 0.2` CAD per MXN.

The general rule is:

```text
conversion rate = destination's rate from root / source's rate from root
```

Run one iterative DFS per connected group to fill these rates. The input guarantees consistent relationships, so the first path used to reach a currency gives the required rate. We do not need a shortest path or a search for the largest product.

## 3. Keep the data structures simple

`CurrencyConverter` holds two maps. `component` stores each currency's root, so we can tell whether two currencies belong to the same group. `rateFromRoot` stores the conversion amounts used in the division.

During preprocessing, `graph` maps each currency to a map of its neighbors and their rates. The `stack` holds currencies still waiting to be explored. An explicit stack avoids recursion-depth problems on a long chain.

The `component` map also acts as the visited set. Mark a currency before pushing it onto the stack, so a cycle cannot schedule it again.

Each public method rebuilds these maps from its supplied graph. This prevents an earlier method call from affecting a later call with different relationships. `getConversionRates` builds them only once for the whole batch.

## 4. Handle the boundary cases

Check that both currencies exist before checking their groups. An unknown currency converted to itself must return `-1.0`. A known currency converted to itself gives `1.0` through the same division rule.

Currencies in different groups return `-1.0`. Otherwise, divide the stored rates and round only the final answer to four decimal places. Rounding intermediate rates could lose information needed by later conversions. A very small positive result may round to `0.0`.

`BigDecimal` is used only for final rounding. This avoids multiplying a potentially large `double` by 10,000. The graph calculations still use ordinary `double` values.

Return numbers, not strings padded with zeros. The code builds its own graph and leaves the supplied lists unchanged.

## 5. Why the division is correct

Suppose one root unit equals `a` units of currency A and `b` units of currency B. One unit of A is therefore `1 / a` root units, which equals `b / a` units of B.

DFS stores exactly these root-relative amounts by multiplying rates along a path. Consistency guarantees that choosing another path would give the same mathematical result. Different roots identify disconnected groups, where no conversion is possible.

## 6. Complexity

Preprocessing takes `O(V + E)` time. Each query then takes expected `O(1)` time using hash maps, so a batch takes `O(V + E + Q)` overall. A standalone `getConversionRate` call takes `O(V + E)` because it also prepares its graph.

Auxiliary space is `O(V + E)` for the graph, maps, and stack. The batch result uses an additional `O(Q)` space. The batch time is optimal up to hash-map costs because we must read the relationships and produce one answer per query.

## Java Solution

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CurrencyConverter {
    Map<String, String> component;
    Map<String, Double> rateFromRoot;

    public CurrencyConverter() {
        component = new HashMap<>();
        rateFromRoot = new HashMap<>();
    }

    public double getConversionRate(
            List<String> conversionGraph,
            String fromCurrency,
            String toCurrency) {
        buildRates(conversionGraph);
        return findRate(fromCurrency, toCurrency);
    }

    public List<Double> getConversionRates(
            List<String> conversionGraph,
            List<String> queries) {
        buildRates(conversionGraph);
        List<Double> answers = new ArrayList<>();

        for (String query : queries) {
            String[] parts = query.split(",");
            answers.add(findRate(parts[0], parts[1]));
        }

        return answers;
    }

    public void buildRates(List<String> conversionGraph) {
        // Each public query call may supply a different graph.
        component.clear();
        rateFromRoot.clear();
        Map<String, Map<String, Double>> graph = new HashMap<>();

        for (String conversion : conversionGraph) {
            String[] parts = conversion.split(",");
            String from = parts[0];
            String to = parts[1];
            double rate = Double.parseDouble(parts[2]);

            graph.computeIfAbsent(from, key -> new HashMap<>()).put(to, rate);
            graph.computeIfAbsent(to, key -> new HashMap<>()).put(from, 1.0 / rate);
        }

        for (String root : graph.keySet()) {
            if (component.containsKey(root)) {
                continue;
            }

            Deque<String> stack = new ArrayDeque<>();
            stack.push(root);
            component.put(root, root);
            rateFromRoot.put(root, 1.0);

            while (!stack.isEmpty()) {
                String current = stack.pop();

                for (Map.Entry<String, Double> edge : graph.get(current).entrySet()) {
                    String next = edge.getKey();
                    if (component.containsKey(next)) {
                        continue;
                    }

                    // Mark before pushing, so cycles cannot schedule it again.
                    component.put(next, root);
                    rateFromRoot.put(next, rateFromRoot.get(current) * edge.getValue());
                    stack.push(next);
                }
            }
        }
    }

    public double findRate(String fromCurrency, String toCurrency) {
        // Check membership before handling even a self-conversion.
        if (!component.containsKey(fromCurrency)
                || !component.containsKey(toCurrency)) {
            return -1.0;
        }

        if (!component.get(fromCurrency).equals(component.get(toCurrency))) {
            return -1.0;
        }

        double rate = rateFromRoot.get(toCurrency) / rateFromRoot.get(fromCurrency);

        // Round only the final result, without scaling a large double by 10000.
        return BigDecimal.valueOf(rate)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
```
