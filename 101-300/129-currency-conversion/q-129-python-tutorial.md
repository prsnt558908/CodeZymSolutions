# Currency Conversion in Python

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

`self.graph` is a `dict` of `dict`s:

- Outer key: a currency.
- Inner dict: every currency we can go to directly, along with its rate.


Why this structure?

- We get all roads going out of a currency in one lookup, instead of scanning the whole `rates` list again and again.
- If the same pair appears twice (like `USD,EUR,0.9` and `USD,EUR,0.95`), the inner dict keeps only the better rate. A lower rate on the same road can never give a bigger answer.


### Walking the Paths

The `visited` set holds the currencies in the current path.

- Before stepping into a currency, add it to `visited`.
- After coming back from it, remove it. This is **backtracking**. It lets other paths use that currency again.


This one set is all we need to stop cycles. For `rates = ["USD,EUR,0.9", "EUR,USD,1.1", "EUR,JPY,150.0"]`, the road `EUR -> USD` is skipped because `USD` is already in the path. Only `USD -> EUR -> JPY` is used, giving `1.0 * 0.9 * 150.0 = 135.0`.


### Why `sys.setrecursionlimit`?

A path can have up to `1000` arrows, so the DFS can go about `1000` calls deep. Python's default limit is also around `1000`, so a long chain of currencies would crash with `RecursionError`.


Raising the limit to `10000` removes this risk.


### Truncating to 2 Decimals

We truncate only once, at the very end. The final value is `amount * rate1 * rate2 * ...` for the best path.


Truncating the best raw value gives the same answer as truncating every path and then picking the best, because truncation never changes which number is bigger.


One trap: a `float` cannot store numbers like `1.15` exactly. `1.15 * 100` gives `114.99999999999999`, and `math.floor` turns it into `114`. That gives the wrong answer `1.14`.


Adding a tiny `1e-6` before `floor` fixes this:

```python
math.floor(value * 100 + 1e-6) / 100
```


### Code

```python
import math
import sys

# a path can have up to 1000 arrows, so allow deep recursion
sys.setrecursionlimit(10000)


class CurrencyConversion:
    def __init__(self):
        # currency -> {next currency: best direct rate}
        self.graph = {}
        # biggest amount (before truncation) that reached the target
        self.best_value = -1.0

    def convertCurrency(self, rates, sourceCurrency, targetCurrency, amount):
        if sourceCurrency == targetCurrency:
            return self.truncate(amount)

        self.build_graph(rates)

        self.best_value = -1.0
        visited = {sourceCurrency}
        self.dfs(sourceCurrency, targetCurrency, amount, visited)

        # best_value stays -1 when the target was never reached
        return -1.0 if self.best_value < 0 else self.truncate(self.best_value)

    def build_graph(self, rates):
        """Reads every rate string once. For a repeated pair, keeps the better rate."""
        self.graph = {}

        for entry in rates:
            from_currency, to, rate = entry.split(",")
            rate = float(rate)

            edges = self.graph.setdefault(from_currency, {})
            if to not in edges or rate > edges[to]:
                edges[to] = rate

    def dfs(self, current, target, value, visited):
        """Tries every non-cyclic path from 'current' and updates best_value."""
        if current == target:
            self.best_value = max(self.best_value, value)
            return

        for next_currency, rate in self.graph.get(current, {}).items():
            # a currency already in the path would make a cycle
            if next_currency in visited:
                continue

            visited.add(next_currency)
            self.dfs(next_currency, target, value * rate, visited)
            visited.remove(next_currency)  # backtrack

    def truncate(self, value):
        """Keeps only 2 decimal places. The tiny 1e-6 fixes values like 114.99999999."""
        return math.floor(value * 100 + 1e-6) / 100
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

Before the DFS, find every currency that has **some** way to reach the target. Call this set `can_reach_target`.


During DFS, never step into a currency outside this set.


### How to Find These Currencies

Flip every arrow and run a BFS starting from the target.

- In the original graph, `A -> B` means "A can go to B".
- In the flipped graph it becomes `B -> A`.
- So every currency that the BFS reaches from the target can travel to the target in the original graph.


For this we keep one more dict:

`self.reverse_graph` : for each currency, the list of currencies that have a direct rate into it.


Why BFS with a `deque` and a `set`? It is the simplest way to visit every reachable currency exactly once. A `deque` removes items from the front quickly, which a normal `list` cannot do. The whole BFS costs only `O(C + R)`, where `C` is the number of currencies. That is tiny compared to the DFS work it saves.


### What We Gain

1. If the source is not in `can_reach_target`, we return `-1.0` right away, without any DFS.

2. In the dead-end example above, DFS never enters the group of 30 currencies. It only walks the useful roads.


This check is safe. It only removes currencies that can never reach the target, so we never lose a valid path.


### Code

```python
import math
import sys
from collections import deque

# a path can have up to 1000 arrows, so allow deep recursion
sys.setrecursionlimit(10000)


class CurrencyConversion:
    def __init__(self):
        # currency -> {next currency: best direct rate}
        self.graph = {}
        # currency -> list of currencies that have a direct rate INTO it
        self.reverse_graph = {}
        # currencies from where the target can be reached
        self.can_reach_target = set()
        # biggest amount (before truncation) that reached the target
        self.best_value = -1.0

    def convertCurrency(self, rates, sourceCurrency, targetCurrency, amount):
        if sourceCurrency == targetCurrency:
            return self.truncate(amount)

        self.build_graphs(rates)
        self.find_currencies_that_reach_target(targetCurrency)

        # source can never reach target, so skip the search
        if sourceCurrency not in self.can_reach_target:
            return -1.0

        self.best_value = -1.0
        visited = {sourceCurrency}
        self.dfs(sourceCurrency, targetCurrency, amount, visited)

        return -1.0 if self.best_value < 0 else self.truncate(self.best_value)

    def build_graphs(self, rates):
        """Reads every rate string once. For a repeated pair, keeps the better rate."""
        self.graph = {}
        self.reverse_graph = {}

        for entry in rates:
            from_currency, to, rate = entry.split(",")
            rate = float(rate)

            edges = self.graph.setdefault(from_currency, {})
            if to not in edges:
                edges[to] = rate
                self.reverse_graph.setdefault(to, []).append(from_currency)
            elif rate > edges[to]:
                edges[to] = rate

    def find_currencies_that_reach_target(self, target):
        """BFS that walks the arrows backwards, starting from the target."""
        self.can_reach_target = {target}
        queue = deque([target])

        while queue:
            currency = queue.popleft()
            for previous in self.reverse_graph.get(currency, []):
                if previous not in self.can_reach_target:
                    self.can_reach_target.add(previous)
                    queue.append(previous)

    def dfs(self, current, target, value, visited):
        """Tries every non-cyclic path from 'current' and updates best_value."""
        if current == target:
            self.best_value = max(self.best_value, value)
            return

        for next_currency, rate in self.graph.get(current, {}).items():
            # skip a currency already in the path (cycle) or a dead end
            if next_currency in visited or next_currency not in self.can_reach_target:
                continue

            visited.add(next_currency)
            self.dfs(next_currency, target, value * rate, visited)
            visited.remove(next_currency)  # backtrack

    def truncate(self, value):
        """Keeps only 2 decimal places. The tiny 1e-6 fixes values like 114.99999999."""
        return math.floor(value * 100 + 1e-6) / 100
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
- Truncate only at the end, and add a tiny `1e-6` to protect against `float` precision errors.