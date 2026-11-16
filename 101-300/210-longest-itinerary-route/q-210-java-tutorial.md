# Longest Itinerary Route in a Directed Graph in Java

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

```java
// tries every route that starts from 'city'
void explore(String city, String route, int cities) {
    if (cities > bestCount || (cities == bestCount && route.compareTo(bestRoute) < 0)) {
        bestCount = cities;
        bestRoute = route;
    }
    for (String nextCity : graph.get(city)) {
        explore(nextCity, route + "->" + nextCity, cities + 1);
    }
}

// called once for every city
for (String city : graph.keySet()) {
    explore(city, city, 1);
}
```

It gives the right answer, but it is far too slow.

**Too many routes.** Take a "diamond": `A` goes to `B` and `C`, and both go to `D`. That is 2 routes from `A` to `D`. Put 20 diamonds in a row and there are over a million routes (2^20), from just 80 itineraries.

**Same work again and again.** Everything after `D` is explored once through `B` and once more through `C`.

**Deep recursion.** A chain of 100,000 cities means 100,000 nested calls. That can crash with a stack overflow.

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

It is a plain loop, so there is no recursion and no stack overflow.

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

- `Map<String, Set<String>> graph`: next cities of each city. The `Set` drops repeated itineraries like `"A,B"` given twice, so nothing is counted twice.
- `Map<String, Integer> indegree`: incoming itineraries per city. Kahn's algorithm uses it to know when a city is ready.
- `List<String> order`: the topological order. It also works as the queue in Kahn's algorithm, read with an index.
- `Map<String, Integer> longest`: the one number we store per city.

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

- **Time:** `O((V + E) * L)`. Every city and itinerary is handled a few times, and each map lookup or string compare costs up to `L`.
- **Space:** `O((V + E) * L)` for the maps and the answer.

### Java Code

```java
import java.util.*;

public class LongestItineraryRoute {

    // city -> cities we can reach with one itinerary (a set drops duplicates)
    Map<String, Set<String>> graph;

    // city -> number of itineraries coming into the city
    Map<String, Integer> indegree;

    // city -> number of cities in the longest route that starts at this city
    Map<String, Integer> longest;

    public LongestItineraryRoute() {
    }

    public String findLongestItineraryRoute(List<String> allowedItineraries) {
        graph = new HashMap<>();
        indegree = new HashMap<>();
        longest = new HashMap<>();

        buildGraph(allowedItineraries);
        if (graph.isEmpty()) {
            return "";
        }

        List<String> order = topologicalOrder();
        computeLongest(order);
        return buildRoute();
    }

    void buildGraph(List<String> allowedItineraries) {
        for (String itinerary : allowedItineraries) {
            int comma = itinerary.indexOf(',');
            String source = itinerary.substring(0, comma);
            String destination = itinerary.substring(comma + 1);

            addCity(source);
            addCity(destination);

            // add() returns false for a repeated itinerary, so it is counted only once
            if (graph.get(source).add(destination)) {
                indegree.put(destination, indegree.get(destination) + 1);
            }
        }
    }

    void addCity(String city) {
        if (!graph.containsKey(city)) {
            graph.put(city, new HashSet<>());
            indegree.put(city, 0);
        }
    }

    /**
     * Kahn's algorithm. A city joins the order only after every city
     * pointing to it has joined. So each city comes before all the cities it can reach.
     */
    List<String> topologicalOrder() {
        Map<String, Integer> remaining = new HashMap<>(indegree);
        List<String> order = new ArrayList<>();

        for (String city : graph.keySet()) {
            if (remaining.get(city) == 0) {
                order.add(city);
            }
        }

        // 'order' also works as our queue: index i is the next city to process
        for (int i = 0; i < order.size(); i++) {
            for (String nextCity : graph.get(order.get(i))) {
                remaining.put(nextCity, remaining.get(nextCity) - 1);
                if (remaining.get(nextCity) == 0) {
                    order.add(nextCity);
                }
            }
        }
        return order;
    }

    // Walk the order backwards, so all next cities are already solved.
    void computeLongest(List<String> order) {
        for (int i = order.size() - 1; i >= 0; i--) {
            String city = order.get(i);
            int best = 0;
            for (String nextCity : graph.get(city)) {
                best = Math.max(best, longest.get(nextCity));
            }
            longest.put(city, best + 1);
        }
    }

    // Pick the best first city, then keep moving to the best next city.
    String buildRoute() {
        int maxCities = Collections.max(longest.values());

        String current = null;
        for (String city : graph.keySet()) {
            if (longest.get(city) == maxCities && comesFirst(city, current, maxCities)) {
                current = city;
            }
        }

        List<String> route = new ArrayList<>();
        route.add(current);

        while (longest.get(current) > 1) {
            int citiesLeft = longest.get(current) - 1;
            String best = null;
            for (String nextCity : graph.get(current)) {
                // only a city that still gives the longest route can be next
                if (longest.get(nextCity) == citiesLeft && comesFirst(nextCity, best, citiesLeft)) {
                    best = nextCity;
                }
            }
            route.add(best);
            current = best;
        }
        return String.join("->", route);
    }

    /**
     * Returns true if 'city' makes a smaller route string than 'best'.
     * We compare the exact text each city adds to the route:
     * "name->" when more cities follow, and just "name" for the last city.
     */
    boolean comesFirst(String city, String best, int citiesLeft) {
        if (best == null) {
            return true;
        }
        if (citiesLeft == 1) {
            return city.compareTo(best) < 0;
        }
        return (city + "->").compareTo(best + "->") < 0;
    }
}
```