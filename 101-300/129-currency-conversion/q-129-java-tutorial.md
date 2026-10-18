# Currency Conversion in Java

#### Problem Statement
[https://codezym.com/question/129-currency-conversion](https://codezym.com/question/129-currency-conversion)


Think of every currency as a city and every rate as a one-way road between two cities. When you travel along a road, your money gets multiplied by that road's rate.


So the question becomes: starting from the source city with some money, which trip ends at the target city with the most money, if you can never visit the same city twice?


Because a rate can be bigger than `1`, a longer trip can give more money than a short one. So a greedy choice does not work. We simply try every valid trip using **DFS with backtracking** and keep the best result.


We first build this simple version. Then we make it faster by finding, in advance, which currencies can never lead to the target, and skipping them completely.

---

## Problem in Short

- Each entry `"FROM,TO,RATE"` means `1 FROM = RATE TO`. It works only in that direction.
- Convert `amount` from `sourceCurrency` to `targetCurrency`.
- You may pass through other currencies, but no currency can appear twice in a path.
- If many paths exist, return the biggest final amount.
- Cut the answer to `2` decimal places (truncate, do not round).
- If there is no path, return `-1.0`. If source and target are the same, return `amount` truncated.


**Example**

```
rates  = ["USD,EUR,0.9", "EUR,JPY,160.0", "USD,JPY,140.0"]
source = USD, target = JPY, amount = 2.0

USD -> JPY         : 2.0 * 140.0        = 280.0
USD -> EUR -> JPY  : 2.0 * 0.9 * 160.0  = 288.0   <- best
```


The longer path wins here. This is why we cannot just pick the shortest path.

---

## Why Not Dijkstra or Bellman-Ford?

These algorithms remember one "best value so far" for each currency and build on top of it. That breaks here for two reasons.


1. **Rates can be bigger than 1.** One more step can increase the money, so a value that looks final can still get better later.

2. **Loops can grow money forever.** With `A,B,2.0` and `B,A,2.0`, every trip around the loop multiplies the money by `4`. The problem stops this by banning repeated currencies. But now the best way to reach a currency depends on which currencies the path has already used, so one "best so far" per currency is not enough.


So the reliable approach is to walk through the valid paths one by one.

---

## Solution 1: Try Every Path (DFS + Backtracking)

### Idea

Start at the source with the full amount. Move along every road, multiplying the amount by the rate. Never step into a currency that is already in the current path.


Whenever we land on the target, compare the amount with the best amount seen so far.


### Building the Graph

`Map<String, Map<String, Double>> graph`

- Outer key: a currency.
- Inner map: every currency we can go to directly, along with its rate.


Why this structure?

- We get all roads going out of a currency in one lookup, instead of scanning the whole `rates` list again and again.
- If the same pair appears twice (like `USD,EUR,0.9` and `USD,EUR,0.95`), the inner map keeps only the better rate. A lower rate on the same road can never give a bigger answer.


### Walking the Paths

`Set<String> visited` holds the currencies in the current path.

- Before stepping into a currency, add it to `visited`.
- After coming back from it, remove it. This is **backtracking**. It lets other paths use that currency again.


This one set is all we need to stop cycles. For `rates = ["USD,EUR,0.9", "EUR,USD,1.1", "EUR,JPY,150.0"]`, the road `EUR -> USD` is skipped because `USD` is already in the path. Only `USD -> EUR -> JPY` is used, giving `1.0 * 0.9 * 150.0 = 135.0`.


### Truncating to 2 Decimals

We truncate only once, at the very end. The final value is `amount * rate1 * rate2 * ...` for the best path.


Truncating the best raw value gives the same answer as truncating every path and then picking the best, because truncation never changes which number is bigger.


One trap: a `double` cannot store numbers like `1.15` exactly. `1.15 * 100` gives `114.99999999999999`, and `Math.floor` turns it into `114`. That gives the wrong answer `1.14`.


Adding a tiny `1e-6` before `floor` fixes this:

```java
Math.floor(value * 100 + 1e-6) / 100
```


### Code

```java
import java.util.*;

public class CurrencyConversion {

    // currency -> (next currency -> best direct rate)
    Map<String, Map<String, Double>> graph = new HashMap<>();

    // biggest amount (before truncation) that reached the target
    double bestValue = -1.0;

    public CurrencyConversion() {
    }

    public double convertCurrency(List<String> rates, String sourceCurrency, String targetCurrency, double amount) {
        if (sourceCurrency.equals(targetCurrency)) {
            return truncate(amount);
        }

        buildGraph(rates);

        bestValue = -1.0;
        Set<String> visited = new HashSet<>();
        visited.add(sourceCurrency);
        dfs(sourceCurrency, targetCurrency, amount, visited);

        // bestValue stays -1 when the target was never reached
        return bestValue < 0 ? -1.0 : truncate(bestValue);
    }

    /**
     * Reads every rate string once and stores it as an arrow.
     * If the same pair appears again, only the better rate is kept.
     */
    void buildGraph(List<String> rates) {
        graph = new HashMap<>();

        for (String entry : rates) {
            String[] parts = entry.split(",");
            String from = parts[0];
            String to = parts[1];
            double rate = Double.parseDouble(parts[2]);

            graph.putIfAbsent(from, new HashMap<>());
            Map<String, Double> edges = graph.get(from);

            if (!edges.containsKey(to) || rate > edges.get(to)) {
                edges.put(to, rate);
            }
        }
    }

    /**
     * Tries every non-cyclic path from 'current' and updates bestValue
     * whenever the target is reached.
     */
    void dfs(String current, String target, double value, Set<String> visited) {
        if (current.equals(target)) {
            bestValue = Math.max(bestValue, value);
            return;
        }

        Map<String, Double> edges = graph.getOrDefault(current, new HashMap<>());

        for (String next : edges.keySet()) {
            // a currency already in the path would make a cycle
            if (visited.contains(next)) {
                continue;
            }

            visited.add(next);
            dfs(next, target, value * edges.get(next), visited);
            visited.remove(next); // backtrack, so other paths can use 'next'
        }
    }

    /**
     * Keeps only 2 decimal places (no rounding).
     * The tiny 1e-6 fixes values like 114.99999999 that should be 115.
     */
    double truncate(double value) {
        return Math.floor(value * 100 + 1e-6) / 100;
    }
}
```


### Complexity

- Building the graph: `O(R)`, where `R` is the number of rates.
- DFS: in the worst case it visits every non-cyclic path, and that number can be huge.


### The Problem With Solution 1

DFS happily walks into currencies that have **no road at all** to the target.


Imagine the source can reach a group of 30 currencies that are all connected to each other, but none of them has a road to the target. DFS tries every possible path inside that group before giving up.


The number of such paths is astronomically large, so the program never finishes in time.


All that work is wasted, because the answer was clear from the start: nothing in that group can ever reach the target.

---

## Solution 2: Skip Dead Ends

### Idea

Before the DFS, find every currency that has **some** way to reach the target. Call this set `canReachTarget`.


During DFS, never step into a currency outside this set.


### How to Find These Currencies

Flip every arrow and run a BFS starting from the target.

- In the original graph, `A -> B` means "A can go to B".
- In the flipped graph it becomes `B -> A`.
- So every currency that the BFS reaches from the target can travel to the target in the original graph.


For this we keep one more map:

`Map<String, List<String>> reverseGraph` : for each currency, the list of currencies that have a direct rate into it.


Why BFS with a `Queue` and a `Set`? It is the simplest way to visit every reachable currency exactly once. It costs only `O(C + R)`, where `C` is the number of currencies. That is tiny compared to the DFS work it saves.


### What We Gain

1. If the source is not in `canReachTarget`, we return `-1.0` right away, without any DFS.

2. In the dead-end example above, DFS never enters the group of 30 currencies. It only walks the useful roads.


This check is safe. It only removes currencies that can never reach the target, so we never lose a valid path.


### Code

```java
import java.util.*;

public class CurrencyConversion {

    // currency -> (next currency -> best direct rate)
    Map<String, Map<String, Double>> graph = new HashMap<>();

    // currency -> list of currencies that have a direct rate INTO it
    Map<String, List<String>> reverseGraph = new HashMap<>();

    // currencies from where the target can be reached
    Set<String> canReachTarget = new HashSet<>();

    // biggest amount (before truncation) that reached the target
    double bestValue = -1.0;

    public CurrencyConversion() {
    }

    public double convertCurrency(List<String> rates, String sourceCurrency, String targetCurrency, double amount) {
        if (sourceCurrency.equals(targetCurrency)) {
            return truncate(amount);
        }

        buildGraphs(rates);
        findCurrenciesThatReachTarget(targetCurrency);

        // source can never reach target, so skip the search
        if (!canReachTarget.contains(sourceCurrency)) {
            return -1.0;
        }

        bestValue = -1.0;
        Set<String> visited = new HashSet<>();
        visited.add(sourceCurrency);
        dfs(sourceCurrency, targetCurrency, amount, visited);

        return bestValue < 0 ? -1.0 : truncate(bestValue);
    }

    /**
     * Reads every rate string only once.
     * If the same pair appears again, only the better rate is kept.
     */
    void buildGraphs(List<String> rates) {
        graph = new HashMap<>();
        reverseGraph = new HashMap<>();

        for (String entry : rates) {
            String[] parts = entry.split(",");
            String from = parts[0];
            String to = parts[1];
            double rate = Double.parseDouble(parts[2]);

            graph.putIfAbsent(from, new HashMap<>());
            Map<String, Double> edges = graph.get(from);

            if (!edges.containsKey(to)) {
                edges.put(to, rate);
                reverseGraph.putIfAbsent(to, new ArrayList<>());
                reverseGraph.get(to).add(from);
            } else if (rate > edges.get(to)) {
                edges.put(to, rate);
            }
        }
    }

    /**
     * BFS that walks the arrows backwards, starting from the target.
     * Every currency it touches has some way to reach the target.
     */
    void findCurrenciesThatReachTarget(String target) {
        canReachTarget = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        canReachTarget.add(target);
        queue.add(target);

        while (!queue.isEmpty()) {
            String currency = queue.poll();
            for (String previous : reverseGraph.getOrDefault(currency, new ArrayList<>())) {
                if (canReachTarget.add(previous)) {
                    queue.add(previous);
                }
            }
        }
    }

    /**
     * Tries every non-cyclic path from 'current' and updates bestValue
     * whenever the target is reached.
     */
    void dfs(String current, String target, double value, Set<String> visited) {
        if (current.equals(target)) {
            bestValue = Math.max(bestValue, value);
            return;
        }

        Map<String, Double> edges = graph.getOrDefault(current, new HashMap<>());

        for (String next : edges.keySet()) {
            // skip a currency already in the path (cycle) or a dead end
            if (visited.contains(next) || !canReachTarget.contains(next)) {
                continue;
            }

            visited.add(next);
            dfs(next, target, value * edges.get(next), visited);
            visited.remove(next); // backtrack
        }
    }

    /**
     * Keeps only 2 decimal places (no rounding).
     * The tiny 1e-6 fixes values like 114.99999999 that should be 115.
     */
    double truncate(double value) {
        return Math.floor(value * 100 + 1e-6) / 100;
    }
}
```


### Complexity

- Building both graphs: `O(R)`.
- BFS from the target: `O(C + R)`.
- DFS: still exponential in the worst case, when every currency can reach the target and is linked to many others. The no-repeat rule forces us to check paths one by one, and no known shortcut is fast for every graph. But all the time wasted in dead ends is gone.

---

## Key Takeaways

- Currency conversion is a graph: currencies are nodes, rates are one-way arrows, and money gets multiplied along the path.
- Rates above `1` and the no-repeat rule make shortest-path algorithms a bad fit. DFS with backtracking handles them.
- A `visited` set with add-before and remove-after (backtracking) is enough to stop cycles.
- A reverse BFS from the target is a cheap way to cut away dead ends before an expensive search.
- Truncate only at the end, and add a tiny `1e-6` to protect against `double` precision errors.