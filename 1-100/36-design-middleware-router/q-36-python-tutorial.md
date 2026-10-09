# Design a Middleware Router in Python

#### Problem Statement
[https://codezym.com/question/36-design-middleware-router](https://codezym.com/question/36-design-middleware-router)

A router keeps a table of path patterns and, for each request, returns the result of the most specific pattern that matches. We start with the brute force, which checks every route on every request. Then we build a trie of path segments that looks only at routes that could match, and skips any branch that cannot beat the best match found so far.

## Pin down the rules

A few small details decide whether a solution passes, so settle them before writing code.

**Segments.** Drop the leading `/` and split on `/`, keeping empty pieces. `/bar/a/baz` is `["bar", "a", "baz"]`, `/bar/` is `["bar", ""]`, and `/` on its own has zero segments. Python's `str.split("/")` already keeps empty pieces, so `path[1:].split("/")` does the job once `/` is handled separately.

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

Store a list of `[pattern, result]` pairs in insertion order.

- `addRoute` looks for the pattern in the list. If it is there, replace the result in place. Otherwise append a new pair.
- `callRoute` splits the request. For every stored pattern, it splits the pattern, checks compatibility, and computes the rank `(static count, param count)`. Python compares tuples element by element, so `rank > best_rank` applies rules 1 and 2 in one comparison. We scan in insertion order and replace the best only when a rank is strictly larger, so a full tie keeps the earlier route.
- `searchRoutes` returns the result of every compatible route. The list is already in insertion order.

```python
class Router:
    def __init__(self):
        self.routes = []  # [pattern, result] pairs in insertion order

    def addRoute(self, pathPattern, result):
        for route in self.routes:          # linear search for an existing pattern
            if route[0] == pathPattern:
                route[1] = result          # same position, new result
                return
        self.routes.append([pathPattern, result])

    def callRoute(self, path):
        request = split_path(path)
        best_result, best_rank = "NOT_FOUND", None
        for pattern, result in self.routes:
            segments = split_path(pattern)  # parsed again on every call
            if not compatible(segments, request):
                continue
            rank = (sum(not is_placeholder(s) for s in segments),
                    sum(s.startswith(":") for s in segments))
            # Only a strictly better rank replaces the best, so a full tie keeps the earlier route.
            if best_rank is None or rank > best_rank:
                best_result, best_rank = result, rank
        return best_result

    def searchRoutes(self, wildcardPattern):
        query = split_path(wildcardPattern)
        return [result for pattern, result in self.routes
                if compatible(split_path(pattern), query)]


def split_path(path):
    """"/" has no segments. Other empty segments are kept: "/a/" is ["a", ""]."""
    if path == "/":
        return []
    return path[1:].split("/")


def is_placeholder(segment):
    return segment == "*" or segment.startswith(":")


def compatible(a, b):
    """Could one concrete path match both? Placeholders may be on either side."""
    if len(a) != len(b):
        return False
    for x, y in zip(a, b):
        if is_placeholder(x) or is_placeholder(y):
            if not x or not y:
                return False  # a placeholder needs a non-empty segment
        elif x != y:
            return False
    return True
```

With `R` routes and paths of up to `P` characters, every operation costs `O(R × P)`.

This is correct, and it is a fine first answer in an interview. But every request repeats work it does not need:

- Every pattern is split and counted again on every call.
- Routes with the wrong number of segments are still checked, although they can never match.
- Routes that share a prefix, such as hundreds of routes under `/api/v1`, compare that prefix again for each route.
- `addRoute` scans the whole list to find an existing pattern.

Parsing each pattern once and storing routes in a `dict` keyed by pattern fixes the first and last points. A `dict` keeps insertion order (guaranteed since Python 3.7), so search order comes for free. The lookup still visits every route, though. Fixing the middle two needs a different structure.

## Three observations

1. **Length is a hard filter.** A pattern with three segments can only match a path with three segments. If routes are grouped by segment count, a request never looks at routes of another length.

2. **A route's rank does not depend on the request.** Its static count, param count and insertion order all come from the pattern. We can compute them once when the route is added and compare two routes without looking at the request at all.

3. **Routes share prefixes, and static segments can be looked up by hash.** `/users/:id` and `/users/:id/orders/:orderId` both start with `users` followed by a param. A trie keyed by segment stores each shared prefix once and finds the matching static child at each level in `O(1)`.

## Better approach: a segment trie with pruning

### The structure

- `Route` holds the result, the insertion order, and a `rank` tuple, `(static count, param count, -order)`, computed once. This one tuple is the whole precedence rule: a larger tuple wins, and negating the order makes the earlier route larger when the counts tie.
- `routes_by_pattern` maps the exact pattern string to its `Route`, so an update is a single dict lookup and keeps the order.
- `tries_by_length` holds one trie per segment count (observation 1).
- Each `TrieNode` has:
  - `static_children`, a `dict` from segment to child
  - `param_child`, one child shared by every `:name`
  - `wildcard_child`, the child for `*`
  - `routes`, the routes that end at this node, oldest first
  - `best_in_subtree`, the highest-ranked route at or below this node (observation 2)

All `:name` segments share one child because a param's name changes neither what it matches nor how the route ranks. So `/a/:x/c` and `/a/:y/c` end at the same node, and its `routes` list keeps them in insertion order. That is exactly the tie-break from Example 5.

Here is the length-3 trie after the routes from the search example. The numbers are insertion order:

```
#0  /bar/*/baz   server-wild-v2   rank: (2, 0, 0)
#1  /bar/:x/baz  server-param     rank: (2, 1, -1)
#2  /bar/a/baz   server-static    rank: (3, 0, -2)
#3  /bar/*/qux   server-qux       rank: (2, 0, -3)

trie for length 3                     best_in_subtree
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

If the pattern already exists, update its result and stop. Otherwise create a `Route` with the next order number, pick the trie for its length, and walk down, creating children as needed. At every node on the way, root included, offer the new route as `best_in_subtree`. It replaces the current one only if its rank is larger. Finally, append the route to the last node's `routes`. That is `L` steps for a pattern with `L` segments.

### callRoute: the first match is not enough

A common trie shortcut is to try the static child first, then the param child, then the wildcard child, and to return the first complete match. That is fast, but it implements positional precedence, where a static segment near the start beats anything after it. In this problem, precedence is counted. With routes `/a/*/*` and `/*/b/c`, the shortcut reaches `/a/*/*` first through the static child `a` and returns it, but `/*/b/c` has more static segments and must win.

So we have to consider every compatible branch and keep the best route found. Doing that blindly would visit the whole compatible part of the trie. `best_in_subtree` lets us skip most of it. This technique is called branch and bound:

- Visit children in the order static, param, wildcard. Static branches tend to hold the strongest routes, so a good candidate appears early.
- Before entering a node, compare the rank of its `best_in_subtree` with the best match so far. `best_in_subtree` is the strongest route in that subtree, whether it matches or not. If its rank is not larger, nothing below it can win, so skip the whole subtree.
- Once the walk has used every segment, every route stored at that node has the same shape and therefore the same counts, so the oldest one wins. That is the node's `best_in_subtree`.

Each trie holds routes of a single length, which keeps the bound tight. For example, once a fully static route matches, every other compatible branch contains a placeholder, has fewer static segments, and is skipped without being entered.

The tie-break no longer depends on the order in which nodes are visited. The rank includes the insertion order, so the iteration order of a `dict` cannot change the answer.

### searchRoutes

It uses the same walk, with two differences:

- A placeholder in the query can face any non-empty static segment. At a `*` or `:name` query segment, visit every static child with a non-empty key, plus the param and wildcard children.
- We want every compatible route, so nothing is pruned. Once the walk has used every segment, collect all of the node's `routes`, then sort what was collected by insertion order.

Both methods ask a node for `children_compatible_with(segment)`, and that one method encodes the compatibility table. It is a generator, so it hands back children one at a time without building a list. The two methods cannot disagree about what matches.

### Walkthrough

Using the trie above:

- `callRoute("/bar/a/baz")` goes root → `bar` → static `a` → `baz` and finds #2, with 3 static segments. Back at `bar`, the param branch's best is #1 and the wildcard branch's best is #0. Neither ranks above #2, so both branches are skipped without being entered. The answer is `server-static`.
- `callRoute("/bar/b/baz")` finds no static child `b`. The param branch yields #1. The wildcard branch's best, #0, has the same static count but fewer params, so it is skipped. The answer is `server-param`.
- `searchRoutes("/bar/*/baz")` reaches the `*` query segment at `bar` and visits `a`, `:` and `*`. Each has a `baz` child, so it collects #2, #1 and #0, then sorts them into `["server-wild-v2", "server-param", "server-static"]`. The update to #0 changed its result, not its position.

## Which data structures and design patterns help

Most of the improvement comes from data structures:

- **A `dict` from exact pattern to route** turns an update into one lookup that keeps insertion order.
- **Bucketing by segment count** is an index on the one property every match checks first.
- **A trie over segments, with static children in a `dict`,** stores and compares each shared prefix once, and each static step is an `O(1)` lookup.
- **A subtree summary on each node** (`best_in_subtree`) makes the pruning possible. It is the same idea as storing a subtree maximum in a segment tree or an augmented binary search tree, and it costs `O(L)` per insert to maintain.
- **A rank tuple** puts the whole precedence rule into one comparable value, so every comparison in the code is a single `>` or `<=`.
- **Sorting only the `k` search results** restores insertion order without walking every route in order.

Design patterns play a smaller role:

- **Chain of Responsibility.** The word "middleware" suggests it: each route is a handler that either takes the request or passes it on. A plain chain returns the first handler that takes the request, which is wrong here unless the chain is sorted by rank. Observation 2 makes that sorting possible, and "keep the routes sorted by rank, return the first match" is a reasonable intermediate answer. Each request still walks the chain, though, so the worst case stays `O(R × L)`. Chain of Responsibility fits what middleware does after routing, such as running auth, then logging, then the handler.
- **Strategy for segment types.** Each kind of segment could be a class with `matches()` and `rank()` methods. With three fixed kinds that the trie already stores in separate slots, those classes would add code without removing any logic. The small `SegmentType` enum is the place to grow: if you add regex-constrained params such as `:id(\d+)` or a catch-all segment, it can become a `SegmentMatcher` base class with one subclass per kind.
- **Composite.** The trie is recursive, but every node has the same type and the same job, so it is simply a tree. Calling it a Composite adds nothing.

The design choice that matters most here is not a named pattern: keep each rule in one place. The matching rule lives only in `TrieNode.children_compatible_with`, and the ranking rule lives only in `Route.rank`. Both public lookups are built from those two.

## Complexity

Let `R` be the number of distinct patterns, `L` the number of segments in the request or query, `P` its length in characters, `V` the number of trie nodes a lookup visits, and `k` the number of search results. Treat hashing or comparing one segment as `O(1)`, since segments are short. Splitting a path then costs `O(P)`.

| Approach | `addRoute` | `callRoute` | `searchRoutes` |
|---|---|---|---|
| Brute force | `O(R × P)` | `O(R × P)` | `O(R × P)` |
| Pre-parsed scan | `O(P)` | `O(P + R × L)` | `O(P + R × L)` |
| Segment trie | `O(P)` | `O(P + V)` | `O(P + V + k log k)` |

All three use space proportional to the total size of the stored patterns.

The trie's cost depends on `V`, not on `R`. For a table of mostly static routes with some params, `V` stays close to `L`, so a lookup takes time proportional to the request alone. In a quick benchmark with about 20,000 REST-style routes, the trie answered `callRoute` more than a thousand times faster than the pre-parsed scan, with identical results.

The worst case does not improve. A table built so that many branches stay compatible and nothing can be pruned makes `V` reach every node of the trie, which costs the same `O(R × L)` as a scan. A search such as `/*/*/*` must visit every node of that length by definition, since every route of that length is a result.

## Edge cases

- `/` has zero segments, and only the pattern `/` matches it.
- A trailing or double slash creates an empty segment. The pattern `/bar/` matches the path `/bar/`, but `/bar/*` does not, because a placeholder needs a non-empty segment.
- `/a/:x/c` and `/a/:y/c` are separate routes. The earlier one wins `callRoute`, and `searchRoutes` returns both.
- An update keeps the route's position, both for the tie-break and for search order.
- Search returns one entry per route, so a result string shared by two routes appears twice.
- In a search query, `:name` behaves exactly like `*`.

## Code

```python
from enum import Enum


class SegmentType(Enum):
    """The three kinds of segment a pattern can contain."""
    STATIC = 1
    PARAM = 2
    WILDCARD = 3

    @staticmethod
    def of(segment):
        if segment == "*":
            return SegmentType.WILDCARD
        if segment.startswith(":"):
            return SegmentType.PARAM
        return SegmentType.STATIC


class Route:
    """A stored pattern. Its rank depends only on the pattern, never on the request."""

    def __init__(self, segments, result, order):
        types = [SegmentType.of(segment) for segment in segments]
        self.result = result
        self.order = order  # insertion position; an update keeps it
        # Higher rank wins: more static segments, then more params, then the earlier route.
        self.rank = (types.count(SegmentType.STATIC), types.count(SegmentType.PARAM), -order)


class TrieNode:
    """One level of path segments."""

    def __init__(self):
        self.static_children = {}
        self.param_child = None      # shared by every ":name"; names affect neither matching nor rank
        self.wildcard_child = None
        self.routes = []             # routes ending here, oldest first
        self.best_in_subtree = None  # highest-ranked route at or below this node

    def offer(self, route):
        if self.best_in_subtree is None or route.rank > self.best_in_subtree.rank:
            self.best_in_subtree = route

    def child_for(self, segment):
        """Returns the child for a pattern segment, creating it if needed."""
        kind = SegmentType.of(segment)
        if kind is SegmentType.WILDCARD:
            if self.wildcard_child is None:
                self.wildcard_child = TrieNode()
            return self.wildcard_child
        if kind is SegmentType.PARAM:
            if self.param_child is None:
                self.param_child = TrieNode()
            return self.param_child
        child = self.static_children.get(segment)
        if child is None:
            child = self.static_children[segment] = TrieNode()
        return child

    def children_compatible_with(self, segment):
        """Yields children whose segment can match the same request segment as the given one.
        Static comes first, then param, then wildcard, so strong candidates are found early."""
        if SegmentType.of(segment) is SegmentType.STATIC:
            exact = self.static_children.get(segment)
            if exact is not None:
                yield exact
        else:
            # A placeholder in a search query can stand for any non-empty static segment.
            for key, child in self.static_children.items():
                if key:
                    yield child
        # Stored placeholders match any non-empty segment.
        if segment:
            if self.param_child is not None:
                yield self.param_child
            if self.wildcard_child is not None:
                yield self.wildcard_child


class Router:
    def __init__(self):
        self.routes_by_pattern = {}  # exact pattern string -> Route, so an update is one lookup
        self.tries_by_length = {}    # one trie per segment count
        self.next_order = 0

    def addRoute(self, pathPattern, result):
        """Adds a pattern, or replaces its result while keeping its original position."""
        existing = self.routes_by_pattern.get(pathPattern)
        if existing is not None:
            existing.result = result
            return

        segments = split_path(pathPattern)
        route = Route(segments, result, self.next_order)
        self.next_order += 1
        self.routes_by_pattern[pathPattern] = route

        node = self.tries_by_length.get(len(segments))
        if node is None:
            node = self.tries_by_length[len(segments)] = TrieNode()
        node.offer(route)
        for segment in segments:
            node = node.child_for(segment)
            node.offer(route)
        node.routes.append(route)

    def callRoute(self, path):
        """Returns the result of the highest-ranked matching route, or "NOT_FOUND"."""
        segments = split_path(path)
        best = self._find_best(self.tries_by_length.get(len(segments)), segments, 0, None)
        return "NOT_FOUND" if best is None else best.result

    def searchRoutes(self, wildcardPattern):
        """Returns the results of all routes compatible with the query, in insertion order."""
        segments = split_path(wildcardPattern)
        found = []
        self._collect(self.tries_by_length.get(len(segments)), segments, 0, found)
        found.sort(key=lambda route: route.order)
        return [route.result for route in found]

    def _find_best(self, node, segments, depth, best):
        """Returns the best route under node that matches segments[depth:],
        or best if nothing under node outranks it."""
        if node is None:
            return best
        # Branch and bound: if the best route anywhere below cannot win, skip the whole subtree.
        if best is not None and node.best_in_subtree.rank <= best.rank:
            return best
        if depth == len(segments):
            # All routes here have the same shape, so the oldest one (best_in_subtree) wins.
            return node.best_in_subtree
        for child in node.children_compatible_with(segments[depth]):
            best = self._find_best(child, segments, depth + 1, best)
        return best

    def _collect(self, node, segments, depth, found):
        """Adds every route under node that is compatible with segments[depth:]."""
        if node is None:
            return
        if depth == len(segments):
            found.extend(node.routes)
            return
        for child in node.children_compatible_with(segments[depth]):
            self._collect(child, segments, depth + 1, found)


def split_path(path):
    """"/" has no segments. Other empty segments are kept: "/a/" is ["a", ""]."""
    if path == "/":
        return []
    return path[1:].split("/")
```

## Follow-ups an interviewer may ask

- **Return the captured params.** Store each route's param names and positions. After `callRoute` picks the winner, read the request segments at those positions. That is `O(L)` extra work and nothing changes during the walk.
- **Delete a route.** Remove it from its node's `routes`, then recompute `best_in_subtree` on the way back up to the root. Each node takes the best of its children's values, plus its own oldest route if it has one.
- **Catch-all segments,** such as `**` matching the rest of the path, break the "same length" rule. Use one trie for all lengths and give each node a catch-all slot that ends the walk early.
- **HTTP methods.** Keep a separate set of tries per method.
- **Concurrency.** Routers are read far more often than written. On a write, copy the nodes along the new route's path and swap in the new roots with a single assignment, so a reader always sees one complete trie. A `threading.Lock` around every call is the simpler alternative.