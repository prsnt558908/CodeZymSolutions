# Currency Conversion Queries in Python

#### Problem Statement

[https://codezym.com/question/443-currency-conversion-queries](https://codezym.com/question/443-currency-conversion-queries)

Treat currencies as a graph, where each connection stores a conversion rate. A simple solution searches for a path for every query. We can improve this by choosing one reference currency in each connected group and storing every currency's rate relative to it. After that, each query needs only map lookups and one division.

## 1. Start with a graph search

For an entry such as `USD,CAD,2.0`, store both directions. The edge from USD to CAD has rate `2.0`, and the reverse edge has rate `0.5`.

To answer one query, start at the source with an amount of `1.0`. Use depth-first search, or DFS, and multiply the amount by each edge rate as you move. A visited set prevents cycles from sending the search back to currencies it has already reached.

The main traversal step looks like this. Here, `currentRate` is the amount reached from the query's source.

```python
for nextCurrency, edgeRate in graph[current].items():
    if nextCurrency not in visited:
        visited.add(nextCurrency)
        rateFromSource[nextCurrency] = currentRate * edgeRate
        stack.append(nextCurrency)
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

`Decimal` is used only for final rounding, with enough precision for large finite `float` values. The graph calculations still use ordinary floats.

Return numbers, not strings padded with zeros. The code builds its own graph and leaves the supplied lists unchanged.

## 5. Why the division is correct

Suppose one root unit equals `a` units of currency A and `b` units of currency B. One unit of A is therefore `1 / a` root units, which equals `b / a` units of B.

DFS stores exactly these root-relative amounts by multiplying rates along a path. Consistency guarantees that choosing another path would give the same mathematical result. Different roots identify disconnected groups, where no conversion is possible.

## 6. Complexity

Preprocessing takes `O(V + E)` time. Each query then takes expected `O(1)` time using hash maps, so a batch takes `O(V + E + Q)` overall. A standalone `getConversionRate` call takes `O(V + E)` because it also prepares its graph.

Auxiliary space is `O(V + E)` for the graph, maps, and stack. The batch result uses an additional `O(Q)` space. The batch time is optimal up to hash-map costs because we must read the relationships and produce one answer per query.

## Python Solution

```python
from decimal import Decimal, ROUND_HALF_UP, localcontext


class CurrencyConverter:
    def __init__(self):
        self.component = {}
        self.rateFromRoot = {}

    def getConversionRate(self, conversionGraph, fromCurrency, toCurrency):
        self.buildRates(conversionGraph)
        return self.findRate(fromCurrency, toCurrency)

    def getConversionRates(self, conversionGraph, queries):
        self.buildRates(conversionGraph)
        answers = []

        for query in queries:
            fromCurrency, toCurrency = query.split(",")
            answers.append(self.findRate(fromCurrency, toCurrency))

        return answers

    def buildRates(self, conversionGraph):
        # Each public query call may supply a different graph.
        self.component.clear()
        self.rateFromRoot.clear()
        graph = {}

        for conversion in conversionGraph:
            fromCurrency, toCurrency, rateText = conversion.split(",")
            rate = float(rateText)

            graph.setdefault(fromCurrency, {})[toCurrency] = rate
            graph.setdefault(toCurrency, {})[fromCurrency] = 1.0 / rate

        for root in graph:
            if root in self.component:
                continue

            stack = [root]
            self.component[root] = root
            self.rateFromRoot[root] = 1.0

            while stack:
                current = stack.pop()

                for nextCurrency, edgeRate in graph[current].items():
                    if nextCurrency in self.component:
                        continue

                    # Mark before pushing, so cycles cannot schedule it again.
                    self.component[nextCurrency] = root
                    self.rateFromRoot[nextCurrency] = (
                        self.rateFromRoot[current] * edgeRate
                    )
                    stack.append(nextCurrency)

    def findRate(self, fromCurrency, toCurrency):
        # Check membership before handling even a self-conversion.
        if fromCurrency not in self.component or toCurrency not in self.component:
            return -1.0

        if self.component[fromCurrency] != self.component[toCurrency]:
            return -1.0

        rate = self.rateFromRoot[toCurrency] / self.rateFromRoot[fromCurrency]

        # Round only the final result, without scaling a large float by 10000.
        with localcontext() as context:
            # Enough digits for any finite float and four decimal places.
            context.prec = 320
            return float(
                Decimal(str(rate)).quantize(
                    Decimal("0.0001"), rounding=ROUND_HALF_UP
                )
            )
```
