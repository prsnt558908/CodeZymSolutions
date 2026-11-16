# Longest Itinerary Route in a Directed Graph in Python

#### Problem Statement
[https://codezym.com/question/210-longest-itinerary-route](https://codezym.com/question/210-longest-itinerary-route)

---

## Core Idea

Think of every city as a point and every itinerary `"A,B"` as a one way arrow from `A` to `B`. The problem promises there are no cycles, so a route can never come back to a city it already visited. We need the route that passes through the most cities.

The key thought: **the longest route starting at a city does not depend on how we reached that city.** So we work out one number per city, `longest[city]`, only once, and reuse it everywhere.

We fill these numbers from the last cities backwards, using a **topological order**. That is just an order where every city comes before all the cities it can reach. Then we build the answer in a single walk: start at the best city, and keep moving to the best next city.

There is one small trap in the tie-break. We must compare full route strings, and the `"->"` between cities changes the result in some cases. One tiny helper method takes care of it.

---

## Approach 1: Brute Force (Try Every Route)

The most direct idea: start from every city, follow every arrow, and build every possible route string. Keep the route with the most cities. On a tie, keep the smaller string.

```python
# tries every route that starts from 'city'
def explore(self, city: str, route: str, cities: int) -> None:
    if cities > self.bestCount or (cities == self.bestCount and route < self.bestRoute):
        self.bestCount = cities
        self.bestRoute = route
    for nextCity in self.graph[city]:
        self.explore(nextCity, route + "->" + nextCity, cities + 1)

# called once for every city
for city in self.graph:
    self.explore(city, city, 1)
```

It gives the right answer, but it is far too slow.

**Too many routes.** Take a "diamond": `A` goes to `B` and `C`, and both go to `D`. That is 2 routes from `A` to `D`. Put 20 diamonds in a row and there are over a million routes (2^20), from just 80 itineraries.

**Same work again and again.** Everything after `D` is explored once through `B` and once more through `C`.

**Deep recursion.** A chain of 100,000 cities means 100,000 nested calls. Python's default recursion limit is 1000, so it crashes with a `RecursionError`.

---

## Approach 2: Topological Order + DP (Optimal)

### Step 1: Store one number per city

Let `longest[city]` be the number of cities in the longest route that starts at `city`.

- A city with no outgoing itinerary: `longest = 1`.
- Any other city: `longest = 1 + max(longest[nextCity])` over all its next cities.

Now each city is solved only once, and its answer is reused by every city that points to it. This removes all the repeated work.

**Why not store the best route string per city?** In a chain of 100,000 cities, the first city would store 100,000 names, the next one 99,999 names, and so on. That adds up to billions of names. Numbers are cheap, so we store numbers and build the final string only once.

### Step 2: Solve cities in topological order

To compute `longest[city]`, all of its next cities must already be solved. A topological order lists every city before all the cities it can reach. So we simply walk that order **backwards**.

We get the order with **Kahn's algorithm**:

1. Count the incoming itineraries of every city. This is its `indegree`.
2. Cities with `indegree` 0 have nothing pointing to them, so they go first.
3. Go through the order one city at a time. For each of its next cities, reduce `indegree` by 1. When it hits 0, every city pointing to it is already in the order, so add it to the end.

It is a plain loop, so there is no recursion and no recursion limit to worry about.

### Step 3: Build the route one city at a time

Let `maxCities` be the biggest `longest` value.

- **First city:** among cities with `longest == maxCities`, pick the smallest.
- **Next city:** look only at next cities with `longest == longest[current] - 1`. Only they can still complete a longest route. Pick the smallest.
- Stop when `longest[current] == 1`.

### The tie-break trap: what does "smallest" mean?

Inside a route string, every city except the last is followed by `"->"`. City names can contain spaces and hyphens, and a space comes **before** `-` in character order.

So comparing plain names can go wrong. Say `A` and `A B` both lead to `X`:

| What we compare | Smaller one |
|---|---|
| names `"A"` and `"A B"` | `"A"` (it is a prefix) |
| routes `"A->X"` and `"A B->X"` | `"A B->X"` (at index 1, `' '` comes before `'-'`) |

The problem asks for the smallest **route string**, so `"A B->X"` is correct.

The fix: compare the exact text each city adds to the route.

- `name + "->"` when more cities follow.
- Just `name` for the last city.

City names never contain `>`. Because of that, two different pieces ending with `"->"`, like `"A->"` and `"A B->"`, always differ at some character inside them, and that character decides the order. The cities after them never matter. For the last city nothing follows, so plain names compare correctly.

This is also why picking the smallest city at every step works. Two routes match until the first city where they differ, and that city's piece alone decides which route is smaller.

### Data structures we need

- `graph`, a `dict` from city to a `set` of next cities. The `set` drops repeated itineraries like `"A,B"` given twice, so nothing is counted twice.
- `indegree`, a `dict` of incoming itineraries per city. Kahn's algorithm uses it to know when a city is ready.
- `order`, a `list` holding the topological order. It also works as the queue in Kahn's algorithm, read with an index.
- `longest`, a `dict` holding the one number we store per city.

### Dry run (Example 2)

Itineraries: `"A,B"`, `"A,C"`, `"B,D"`, `"C,D"`

Topological order: `A, B, C, D`. Walking it backwards:

| City | Next cities | longest |
|---|---|---|
| D | none | 1 |
| C | D | 2 |
| B | D | 2 |
| A | B, C | 3 |

Building the route:

1. `maxCities = 3`, and only `A` has it. Start at `A`.
2. From `A` we need `longest = 2`. Both `B` and `C` qualify. `"B->"` is smaller than `"C->"`, so pick `B`.
3. From `B` we need `longest = 1`, which is `D`. It is the last city, so we stop.

Answer: `"A->B->D"`

### Complexity

Let `V` = number of cities, `E` = number of itineraries, `L` = longest city name (at most 100).

- **Time:** `O((V + E) * L)`. Every city and itinerary is handled a few times, and each dictionary lookup or string compare costs up to `L`.
- **Space:** `O((V + E) * L)` for the dictionaries and the answer.

### Python Code

```python
from typing import List, Optional


class LongestItineraryRoute:

    def __init__(self):
        pass

    def findLongestItineraryRoute(self, allowedItineraries: List[str]) -> str:
        # city -> cities we can reach with one itinerary (a set drops duplicates)
        self.graph = {}
        # city -> number of itineraries coming into the city
        self.indegree = {}
        # city -> number of cities in the longest route that starts at this city
        self.longest = {}

        self.buildGraph(allowedItineraries)
        if not self.graph:
            return ""

        order = self.topologicalOrder()
        self.computeLongest(order)
        return self.buildRoute()

    def buildGraph(self, allowedItineraries: List[str]) -> None:
        for itinerary in allowedItineraries:
            source, destination = itinerary.split(",")

            self.addCity(source)
            self.addCity(destination)

            # a repeated itinerary is counted only once
            if destination not in self.graph[source]:
                self.graph[source].add(destination)
                self.indegree[destination] += 1

    def addCity(self, city: str) -> None:
        if city not in self.graph:
            self.graph[city] = set()
            self.indegree[city] = 0

    def topologicalOrder(self) -> List[str]:
        """
        Kahn's algorithm. A city joins the order only after every city
        pointing to it has joined. So each city comes before all the cities it can reach.
        """
        remaining = dict(self.indegree)
        order = [city for city in self.graph if remaining[city] == 0]

        # 'order' also works as our queue: index i is the next city to process
        i = 0
        while i < len(order):
            for nextCity in self.graph[order[i]]:
                remaining[nextCity] -= 1
                if remaining[nextCity] == 0:
                    order.append(nextCity)
            i += 1
        return order

    def computeLongest(self, order: List[str]) -> None:
        # Walk the order backwards, so all next cities are already solved.
        for city in reversed(order):
            best = 0
            for nextCity in self.graph[city]:
                best = max(best, self.longest[nextCity])
            self.longest[city] = best + 1

    def buildRoute(self) -> str:
        # Pick the best first city, then keep moving to the best next city.
        maxCities = max(self.longest.values())

        current = None
        for city in self.graph:
            if self.longest[city] == maxCities and self.comesFirst(city, current, maxCities):
                current = city

        route = [current]

        while self.longest[current] > 1:
            citiesLeft = self.longest[current] - 1
            best = None
            for nextCity in self.graph[current]:
                # only a city that still gives the longest route can be next
                if self.longest[nextCity] == citiesLeft and self.comesFirst(nextCity, best, citiesLeft):
                    best = nextCity
            route.append(best)
            current = best
        return "->".join(route)

    def comesFirst(self, city: str, best: Optional[str], citiesLeft: int) -> bool:
        """
        Returns True if 'city' makes a smaller route string than 'best'.
        We compare the exact text each city adds to the route:
        "name->" when more cities follow, and just "name" for the last city.
        """
        if best is None:
            return True
        if citiesLeft == 1:
            return city < best
        return city + "->" < best + "->"
```