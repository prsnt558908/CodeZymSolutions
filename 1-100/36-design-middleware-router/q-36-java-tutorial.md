# Design a Middleware Router in Java

#### Problem Statement
[https://codezym.com/question/36-design-middleware-router](https://codezym.com/question/36-design-middleware-router)

A router keeps a table of path patterns and, for each request, returns the result of the most specific pattern that matches. We start with the brute force, which checks every route on every request. Then we build a trie of path segments that looks only at routes that could match, and skips any branch that cannot beat the best match found so far.

## Pin down the rules

A few small details decide whether a solution passes, so settle them before writing code.

**Segments.** Drop the leading `/` and split on `/`, keeping empty pieces. `/bar/a/baz` is `["bar", "a", "baz"]`, `/bar/` is `["bar", ""]`, and `/` on its own has zero segments.

**Matching.** A pattern matches a path when both have the same number of segments and every pair of segments matches. A static segment must be equal. A `*` or `:name` segment accepts any non-empty segment, so `/bar/*` does not match `/bar/`.

**Rank.** When several patterns match, the winner is decided by:

1. More static segments wins.
2. Then more `:param` segments wins. Both patterns have the same length, so more params means fewer wildcards.
3. Then the route added earlier wins.

The statement also says a longer pattern wins, but that rule never decides anything here: two patterns that match the same path always have the same length.

Specificity is counted over the whole pattern. It is not decided at the first position where two patterns differ. For `/a/b/c`, the pattern `/*/b/c` beats `/a/*/*` because it has two static segments instead of one. Keep this in mind, because it breaks the most common trie shortcut.

**Search.** `searchRoutes` takes a pattern that may contain placeholders and returns every stored route that could match at least one common concrete path. Placeholders can appear on either side:

| Stored segment ↓ / query segment → | static `q` | `*` or `:name` |
|---|---|---|
| static `s` | compatible if `s` equals `q` | compatible if `s` is non-empty |
| `*` or `:name` | compatible if `q` is non-empty | always compatible |

That is why the static query `/bar/a/baz` also returns `/bar/*/baz` and `/bar/:x/baz`. Results come back in insertion order, one per route even when two routes share a result string, and they are never sorted by rank.

**Updates.** Adding a pattern that already exists changes only its result. Its position stays the same, which matters both for tie-breaks and for search order. `/a/:x/c` and `/a/:y/c` are different strings, so they are two different routes.

Request paths in `callRoute` are concrete, so they only ever use the `static q` column of the table. Using the same compatibility rule for both methods keeps the code small and consistent.

## Brute force: check every route

Store two lists in insertion order: the patterns and their results.

- `addRoute` looks for the pattern in the list. If it is there, replace the result in place. Otherwise append it.
- `callRoute` splits the request. For every stored pattern, it splits the pattern, checks compatibility, counts static and param segments, and keeps the best route. We scan in insertion order and replace the best only when a route is strictly better, so a full tie keeps the earlier route.
- `searchRoutes` returns the result of every compatible route. The list is already in insertion order.

```java
import java.util.ArrayList;
import java.util.List;

public class Router {
    private final List<String> patterns = new ArrayList<>(); // insertion order
    private final List<String> results = new ArrayList<>();  // results.get(i) belongs to patterns.get(i)

    public Router() {
    }

    public void addRoute(String pathPattern, String result) {
        int index = patterns.indexOf(pathPattern); // linear search for an existing pattern
        if (index >= 0) {
            results.set(index, result);            // same position, new result
            return;
        }
        patterns.add(pathPattern);
        results.add(result);
    }

    public String callRoute(String path) {
        String[] request = split(path);
        String bestResult = "NOT_FOUND";
        int bestStatic = -1;
        int bestParams = -1;

        for (int i = 0; i < patterns.size(); i++) {
            String[] pattern = split(patterns.get(i)); // parsed again on every call
            if (!compatible(pattern, request)) {
                continue;
            }
            int statics = 0;
            int params = 0;
            for (String segment : pattern) {
                if (segment.startsWith(":")) {
                    params++;
                } else if (!segment.equals("*")) {
                    statics++;
                }
            }
            // Only a strictly better route replaces the best one, so a full tie keeps the earlier route.
            if (statics > bestStatic || (statics == bestStatic && params > bestParams)) {
                bestResult = results.get(i);
                bestStatic = statics;
                bestParams = params;
            }
        }
        return bestResult;
    }

    public List<String> searchRoutes(String wildcardPattern) {
        String[] query = split(wildcardPattern);
        List<String> found = new ArrayList<>();
        for (int i = 0; i < patterns.size(); i++) {
            if (compatible(split(patterns.get(i)), query)) {
                found.add(results.get(i));
            }
        }
        return found;
    }

    /** "/" has no segments. Other empty segments are kept: "/a/" is ["a", ""]. */
    private static String[] split(String path) {
        if (path.equals("/")) {
            return new String[0];
        }
        return path.substring(1).split("/", -1);
    }

    private static boolean isPlaceholder(String segment) {
        return segment.equals("*") || segment.startsWith(":");
    }

    /** Could one concrete path match both? Placeholders may be on either side. */
    private static boolean compatible(String[] a, String[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (isPlaceholder(a[i]) || isPlaceholder(b[i])) {
                if (a[i].isEmpty() || b[i].isEmpty()) {
                    return false; // a placeholder needs a non-empty segment
                }
            } else if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }
}
```

With `R` routes and paths of up to `P` characters, every operation costs `O(R × P)`.

This is correct, and it is a fine first answer in an interview. But every request repeats work it does not need:

- Every pattern is split and counted again on every call.
- Routes with the wrong number of segments are still checked, although they can never match.
- Routes that share a prefix, such as hundreds of routes under `/api/v1`, compare that prefix again for each route.
- `addRoute` scans the whole list to find an existing pattern.

Parsing each pattern once and storing routes in a `LinkedHashMap` fixes the first and last points. The lookup still visits every route, though. Fixing the middle two needs a different structure.

## Three observations

1. **Length is a hard filter.** A pattern with three segments can only match a path with three segments. If routes are grouped by segment count, a request never looks at routes of another length.

2. **A route's rank does not depend on the request.** Its static count, param count and insertion order all come from the pattern. We can compute them once when the route is added and compare two routes without looking at the request at all.

3. **Routes share prefixes, and static segments can be looked up by hash.** `/users/:id` and `/users/:id/orders/:orderId` both start with `users` followed by a param. A trie keyed by segment stores each shared prefix once and finds the matching static child at each level in `O(1)`.

## Better approach: a segment trie with pruning

### The structure

- `Route` holds the result, the insertion order, and the two rank counts, computed once.
- `routesByPattern` maps the exact pattern string to its `Route`, so an update is a single hash lookup and keeps the order.
- `triesByLength` holds one trie per segment count (observation 1).
- Each `TrieNode` has:
  - `staticChildren`, a `HashMap` from segment to child
  - `paramChild`, one child shared by every `:name`
  - `wildcardChild`, the child for `*`
  - `routes`, the routes that end at this node, oldest first
  - `bestInSubtree`, the highest-ranked route at or below this node (observation 2)

All `:name` segments share one child because a param's name changes neither what it matches nor how the route ranks. So `/a/:x/c` and `/a/:y/c` end at the same node, and its `routes` list keeps them in insertion order. That is exactly the tie-break from Example 5.

Here is the length-3 trie after the routes from the search example. The numbers are insertion order:

```
#0  /bar/*/baz   server-wild-v2   rank: 2 static, 0 params
#1  /bar/:x/baz  server-param     rank: 2 static, 1 param
#2  /bar/a/baz   server-static    rank: 3 static
#3  /bar/*/qux   server-qux       rank: 2 static, 0 params

trie for length 3                     bestInSubtree
root                                  #2
└── bar                               #2
    ├── a     (static)                #2
    │   └── baz    routes: [#2]       #2
    ├── :     (param)                 #1
    │   └── baz    routes: [#1]       #1
    └── *     (wildcard)              #0   (#0 and #3 tie on counts; #0 is older)
        ├── baz    routes: [#0]       #0
        └── qux    routes: [#3]       #3
```

### addRoute

If the pattern already exists, update its result and stop. Otherwise create a `Route` with the next order number, pick the trie for its length, and walk down, creating children as needed. At every node on the way, root included, offer the new route as `bestInSubtree`. It replaces the current one only if it outranks it. Finally, add the route to the last node's `routes`. That is `L` steps for a pattern with `L` segments.

### callRoute: the first match is not enough

A common trie shortcut is to try the static child first, then the param child, then the wildcard child, and to return the first complete match. That is fast, but it implements positional precedence, where a static segment near the start beats anything after it. In this problem, precedence is counted. With routes `/a/*/*` and `/*/b/c`, the shortcut reaches `/a/*/*` first through the static child `a` and returns it, but `/*/b/c` has more static segments and must win.

So we have to consider every compatible branch and keep the best route found. Doing that blindly would visit the whole compatible part of the trie. `bestInSubtree` lets us skip most of it. This technique is called branch and bound:

- Visit children in the order static, param, wildcard. Static branches tend to hold the strongest routes, so a good candidate appears early.
- Before entering a node, compare its `bestInSubtree` with the best match so far. `bestInSubtree` is the strongest route in that subtree, whether it matches or not. If it does not outrank the current best, nothing below it can, so skip the whole subtree.
- Once the walk has used every segment, every route stored at that node has the same shape and therefore the same counts, so the oldest one wins. That is the node's `bestInSubtree`.

Each trie holds routes of a single length, which keeps the bound tight. For example, once a fully static route matches, every other compatible branch contains a placeholder, has fewer static segments, and is skipped without being entered.

The tie-break no longer depends on the order in which nodes are visited. `outranks` compares insertion order explicitly, so the iteration order of a `HashMap` cannot change the answer.

### searchRoutes

It uses the same walk, with two differences:

- A placeholder in the query can face any non-empty static segment. At a `*` or `:name` query segment, visit every static child with a non-empty key, plus the param and wildcard children.
- We want every compatible route, so nothing is pruned. Once the walk has used every segment, collect all of the node's `routes`, then sort what was collected by insertion order.

Both methods ask a node for `childrenCompatibleWith(segment)`, and that one method encodes the compatibility table. The two methods cannot disagree about what matches.

### Walkthrough

Using the trie above:

- `callRoute("/bar/a/baz")` goes root → `bar` → static `a` → `baz` and finds #2, with 3 static segments. Back at `bar`, the param branch's best is #1 and the wildcard branch's best is #0. Neither outranks #2, so both branches are skipped without being entered. The answer is `server-static`.
- `callRoute("/bar/b/baz")` finds no static child `b`. The param branch yields #1. The wildcard branch's best, #0, has the same static count but fewer params, so it is skipped. The answer is `server-param`.
- `searchRoutes("/bar/*/baz")` reaches the `*` query segment at `bar` and visits `a`, `:` and `*`. Each has a `baz` child, so it collects #2, #1 and #0, then sorts them into `["server-wild-v2", "server-param", "server-static"]`. The update to #0 changed its result, not its position.

## Which data structures and design patterns help

Most of the improvement comes from data structures:

- **A `HashMap` from exact pattern to route** turns an update into one hash lookup that keeps insertion order.
- **Bucketing by segment count** is an index on the one property every match checks first.
- **A trie over segments, with static children in a `HashMap`,** stores and compares each shared prefix once, and each static step is an `O(1)` lookup.
- **A subtree summary on each node** (`bestInSubtree`) makes the pruning possible. It is the same idea as storing a subtree maximum in a segment tree or an augmented binary search tree, and it costs `O(L)` per insert to maintain.
- **Sorting only the `k` search results** restores insertion order without walking every route in order.

Design patterns play a smaller role:

- **Chain of Responsibility.** The word "middleware" suggests it: each route is a handler that either takes the request or passes it on. A plain chain returns the first handler that takes the request, which is wrong here unless the chain is sorted by rank. Observation 2 makes that sorting possible, and "keep the routes sorted by rank, return the first match" is a reasonable intermediate answer. Each request still walks the chain, though, so the worst case stays `O(R × L)`. Chain of Responsibility fits what middleware does after routing, such as running auth, then logging, then the handler.
- **Strategy for segment types.** Each kind of segment could be a class with `matches()` and `rank()` methods. With three fixed kinds that the trie already stores in separate slots, those classes would add code without removing any logic. The small `SegmentType` enum is the place to grow: if you add regex-constrained params such as `:id(\d+)` or a catch-all segment, it can become a `SegmentMatcher` interface with one class per kind.
- **Composite.** The trie is recursive, but every node has the same type and the same job, so it is simply a tree. Calling it a Composite adds nothing.

The design choice that matters most here is not a named pattern: keep each rule in one place. The matching rule lives only in `TrieNode.childrenCompatibleWith`, and the ranking rule lives only in `Route.outranks`. Both public lookups are built from those two methods.

## Complexity

Let `R` be the number of distinct patterns, `L` the number of segments in the request or query, `P` its length in characters, `V` the number of trie nodes a lookup visits, and `k` the number of search results. Treat hashing or comparing one segment as `O(1)`, since segments are short. Splitting a path then costs `O(P)`.

| Approach | `addRoute` | `callRoute` | `searchRoutes` |
|---|---|---|---|
| Brute force | `O(R × P)` | `O(R × P)` | `O(R × P)` |
| Pre-parsed scan | `O(P)` | `O(P + R × L)` | `O(P + R × L)` |
| Segment trie | `O(P)` | `O(P + V)` | `O(P + V + k log k)` |

All three use space proportional to the total size of the stored patterns.

The trie's cost depends on `V`, not on `R`. For a table of mostly static routes with some params, `V` stays close to `L`, so a lookup takes time proportional to the request alone. In a quick benchmark with about 20,000 REST-style routes, the trie answered `callRoute` several hundred times faster than the pre-parsed scan, with identical results.

The worst case does not improve. A table built so that many branches stay compatible and nothing can be pruned makes `V` reach every node of the trie, which costs the same `O(R × L)` as a scan. A search such as `/*/*/*` must visit every node of that length by definition, since every route of that length is a result.

## Edge cases

- `/` has zero segments, and only the pattern `/` matches it.
- A trailing or double slash creates an empty segment. The pattern `/bar/` matches the path `/bar/`, but `/bar/*` does not, because a placeholder needs a non-empty segment.
- `/a/:x/c` and `/a/:y/c` are separate routes. The earlier one wins `callRoute`, and `searchRoutes` returns both.
- An update keeps the route's position, both for the tie-break and for search order.
- Search returns one entry per route, so a result string shared by two routes appears twice.
- In a search query, `:name` behaves exactly like `*`.

## Code

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Router {
    // Exact pattern string -> its route, so adding a pattern again is an O(1) update.
    private final Map<String, Route> routesByPattern = new HashMap<>();
    // One trie per segment count: a pattern can only match paths of its own length.
    private final Map<Integer, TrieNode> triesByLength = new HashMap<>();
    private int nextOrder = 0;

    public Router() {
    }

    /** Adds a pattern, or replaces its result while keeping its original position. */
    public void addRoute(String pathPattern, String result) {
        Route existing = routesByPattern.get(pathPattern);
        if (existing != null) {
            existing.result = result;
            return;
        }

        String[] segments = splitPath(pathPattern);
        Route route = new Route(segments, result, nextOrder++);
        routesByPattern.put(pathPattern, route);

        TrieNode node = triesByLength.computeIfAbsent(segments.length, length -> new TrieNode());
        node.offer(route);
        for (String segment : segments) {
            node = node.childFor(segment);
            node.offer(route);
        }
        node.routes.add(route);
    }

    /** Returns the result of the highest-ranked matching route, or "NOT_FOUND". */
    public String callRoute(String path) {
        String[] segments = splitPath(path);
        Route best = findBest(triesByLength.get(segments.length), segments, 0, null);
        return best == null ? "NOT_FOUND" : best.result;
    }

    /** Returns the results of all routes compatible with the query, in insertion order. */
    public List<String> searchRoutes(String wildcardPattern) {
        String[] segments = splitPath(wildcardPattern);
        List<Route> found = new ArrayList<>();
        collect(triesByLength.get(segments.length), segments, 0, found);

        found.sort(Comparator.comparingInt(route -> route.order));
        List<String> results = new ArrayList<>(found.size());
        for (Route route : found) {
            results.add(route.result);
        }
        return results;
    }

    /**
     * Returns the best route under node that matches segments[depth..],
     * or best if nothing under node outranks it.
     */
    private Route findBest(TrieNode node, String[] segments, int depth, Route best) {
        if (node == null) {
            return best;
        }
        // Branch and bound: if the best route anywhere below cannot win, skip the whole subtree.
        if (best != null && !node.bestInSubtree.outranks(best)) {
            return best;
        }
        if (depth == segments.length) {
            // All routes here have the same shape, so the oldest one (bestInSubtree) wins.
            return node.bestInSubtree;
        }
        for (TrieNode child : node.childrenCompatibleWith(segments[depth])) {
            best = findBest(child, segments, depth + 1, best);
        }
        return best;
    }

    /** Adds every route under node that is compatible with segments[depth..]. */
    private void collect(TrieNode node, String[] segments, int depth, List<Route> found) {
        if (node == null) {
            return;
        }
        if (depth == segments.length) {
            found.addAll(node.routes);
            return;
        }
        for (TrieNode child : node.childrenCompatibleWith(segments[depth])) {
            collect(child, segments, depth + 1, found);
        }
    }

    /** "/" has no segments. Other empty segments are kept: "/a/" is ["a", ""]. */
    static String[] splitPath(String path) {
        if (path.equals("/")) {
            return new String[0];
        }
        return path.substring(1).split("/", -1);
    }
}

/** The three kinds of segment a pattern can contain. */
enum SegmentType {
    STATIC, PARAM, WILDCARD;

    static SegmentType of(String segment) {
        if (segment.equals("*")) {
            return WILDCARD;
        }
        if (segment.startsWith(":")) {
            return PARAM;
        }
        return STATIC;
    }
}

/** A stored pattern. Its rank depends only on the pattern, never on the request. */
class Route {
    final int order;  // insertion position; an update keeps it
    final int staticCount;
    final int paramCount;
    String result;

    Route(String[] segments, String result, int order) {
        this.result = result;
        this.order = order;
        int statics = 0;
        int params = 0;
        for (String segment : segments) {
            SegmentType type = SegmentType.of(segment);
            if (type == SegmentType.STATIC) {
                statics++;
            } else if (type == SegmentType.PARAM) {
                params++;
            }
        }
        this.staticCount = statics;
        this.paramCount = params;
    }

    /** True if this route beats other when both match the same path. */
    boolean outranks(Route other) {
        if (staticCount != other.staticCount) {
            return staticCount > other.staticCount;
        }
        if (paramCount != other.paramCount) {
            return paramCount > other.paramCount;
        }
        return order < other.order;
    }
}

/** One level of path segments. */
class TrieNode {
    final Map<String, TrieNode> staticChildren = new HashMap<>();
    TrieNode paramChild;     // shared by every ":name"; names affect neither matching nor rank
    TrieNode wildcardChild;
    final List<Route> routes = new ArrayList<>(); // routes ending here, oldest first
    Route bestInSubtree;     // highest-ranked route at or below this node

    void offer(Route route) {
        if (bestInSubtree == null || route.outranks(bestInSubtree)) {
            bestInSubtree = route;
        }
    }

    /** Returns the child for a pattern segment, creating it if needed. */
    TrieNode childFor(String segment) {
        SegmentType type = SegmentType.of(segment);
        if (type == SegmentType.WILDCARD) {
            if (wildcardChild == null) {
                wildcardChild = new TrieNode();
            }
            return wildcardChild;
        }
        if (type == SegmentType.PARAM) {
            if (paramChild == null) {
                paramChild = new TrieNode();
            }
            return paramChild;
        }
        return staticChildren.computeIfAbsent(segment, key -> new TrieNode());
    }

    /**
     * Children whose segment can match the same request segment as the given one.
     * Static comes first, then param, then wildcard, so strong candidates are found early.
     */
    List<TrieNode> childrenCompatibleWith(String segment) {
        List<TrieNode> children = new ArrayList<>();
        if (SegmentType.of(segment) == SegmentType.STATIC) {
            TrieNode exact = staticChildren.get(segment);
            if (exact != null) {
                children.add(exact);
            }
        } else {
            // A placeholder in a search query can stand for any non-empty static segment.
            for (Map.Entry<String, TrieNode> entry : staticChildren.entrySet()) {
                if (!entry.getKey().isEmpty()) {
                    children.add(entry.getValue());
                }
            }
        }
        // Stored placeholders match any non-empty segment.
        if (!segment.isEmpty()) {
            if (paramChild != null) {
                children.add(paramChild);
            }
            if (wildcardChild != null) {
                children.add(wildcardChild);
            }
        }
        return children;
    }
}
```

## Follow-ups an interviewer may ask

- **Return the captured params.** Store each route's param names and positions. After `callRoute` picks the winner, read the request segments at those positions. That is `O(L)` extra work and nothing changes during the walk.
- **Delete a route.** Remove it from its node's `routes`, then recompute `bestInSubtree` on the way back up to the root. Each node takes the best of its children's values, plus its own oldest route if it has one.
- **Catch-all segments,** such as `**` matching the rest of the path, break the "same length" rule. Use one trie for all lengths and give each node a catch-all slot that ends the walk early.
- **HTTP methods.** Keep a separate set of tries per method.
- **Concurrency.** Routers are read far more often than written. On a write, copy the nodes along the new route's path and publish the new roots through an `AtomicReference`, so reads never lock. A `ReadWriteLock` is the simpler alternative.