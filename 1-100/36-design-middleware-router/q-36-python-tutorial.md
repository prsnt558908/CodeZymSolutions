# Design a Middleware Router in Python

#### Problem Statement
[https://codezym.com/question/36-design-middleware-router](https://codezym.com/question/36-design-middleware-router)


Split each path into segments, compare matching routes, and return the most specific one. A dictionary keeps routes in their original order, even when their results change. For these fixed rules, a small `Route` class and simple helper methods are clearer than adding a design pattern.


## Start with a simple scan

We could store patterns and results in a list. For each request, split every pattern, check whether it matches, and count its static and parameter segments.


This works, but it repeats the same splitting and counting on every request. Updating an existing pattern also requires searching the list.


Keep the scan, but parse each pattern only when it is added. Use a dictionary to find an existing pattern directly when updating its result.


## Store each route once

`Route` is a dataclass holding the parsed segments, the current result, and two counts: static segments and parameter segments. Its `__post_init__` method calculates the counts once after construction.


`routes` is a dictionary mapping the original pattern to its route. Dictionaries preserve insertion order. Updating a route changes only its result. Distinct patterns such as `/users/:id` and `/users/:name` remain separate entries.


The API returns only the result string. Parameter names do not affect precedence, and no map of captured values is needed to produce that result.


## Compare paths segment by segment

Two paths must have the same number of segments. At each position:

- Two static segments must be equal.
- `*` matches exactly one nonempty segment.
- A segment starting with `:` also matches exactly one nonempty segment.


For example, `/bar/*/baz` matches `/bar/a/baz`, but it does not match `/bar/a/b/baz` or `/bar//baz`.


Treat `/` as zero segments. Preserve other empty segments when splitting so a placeholder cannot accidentally match an empty value.


## Choose the best route

`callRoute` scans all matching routes and compares them in this order:

1. More static segments wins.
2. With equal static counts, more parameter segments wins. This favors parameters over wildcards.
3. If still tied, more segments wins.
4. On a complete tie, the earlier route wins.


Specificity uses the total counts, not the position of the first static segment. For `/a/b/c`, `/*/b/c` beats `/a/*/*` because it has two static segments instead of one.


Every matching route has the same segment count as the request, so the length comparison cannot change the winner here. The code keeps that comparison to reflect the stated rule.


Iteration already follows insertion order. Replacing the best route only when a later route is strictly better automatically preserves the earliest route on a tie.


## Search for compatible routes

`searchRoutes` checks whether the query and a stored pattern could match at least one common concrete path. A placeholder on either side can match a nonempty segment on the other side.


This explains why the static query `/bar/a/baz` also finds `/bar/*/baz` and `/bar/:x/baz`. It also finds the static route `/bar/a/baz` itself.


Use the same comparison helper for both methods. In `callRoute`, one side is a concrete path. In `searchRoutes`, either side may contain placeholders.


Search results stay in insertion order. Return one result for each compatible route, including duplicate result strings. Do not sort by specificity.


## A quick walkthrough

Add `/bar/*/baz` with result `wild`, then `/bar/:x/baz` with result `param`, and finally `/bar/a/baz` with result `static`.


`callRoute("/bar/a/baz")` returns `"static"`. All three routes match, but the static route has the most static segments. For `/bar/b/baz`, the parameter route wins over the wildcard route.


Update `/bar/*/baz` to result `wild-v2`. Now `searchRoutes("/bar/*/baz")` returns `["wild-v2", "param", "static"]`. The update preserves the original position.


## Why simple classes fit

Strategy would help if matching policies needed to be swapped independently. Here, the three segment rules are fixed and share one small comparison. Separate Strategy classes would add structure without simplifying these methods.


The `Route` class groups route data, and `Router` handles storage, matching, and precedence. That is enough for the required single-threaded API.


## Complexity

Let `R` be the number of distinct stored patterns and `P` the maximum path or pattern length in characters.


Adding or updating a route takes `O(P)` expected time, including dictionary-key hashing and parsing a new pattern. Both lookup methods take `O(R × P)` time because they scan the stored routes.


Stored patterns and their parsed segments use `O(R × P)` space, in addition to the result strings. A lookup uses `O(P)` temporary space for its parsed input. Search also needs up to `O(R)` space for its returned list.


## Code

```python
from dataclasses import dataclass, field


class Router:
    def __init__(self):
        self.routes = {}

    def addRoute(self, pathPattern, result):
        """Add a pattern, or update its result without changing its order."""
        existing = self.routes.get(pathPattern)
        if existing is not None:
            existing.result = result
            return

        self.routes[pathPattern] = Route(self.split_path(pathPattern), result)

    def callRoute(self, path):
        """Return the most specific matching route."""
        segments = self.split_path(path)
        best = None

        for route in self.routes.values():
            if self.compatible(route.segments, segments) and self.is_better(route, best):
                best = route

        return "NOT_FOUND" if best is None else best.result

    def searchRoutes(self, wildcardPattern):
        """Return all compatible results in their original insertion order."""
        query = self.split_path(wildcardPattern)
        results = []

        for route in self.routes.values():
            if self.compatible(route.segments, query):
                results.append(route.result)

        return results

    def split_path(self, path):
        if path == "/":
            return []

        # Keep empty segments so placeholders cannot match an empty value.
        return path[1:].split("/")

    def is_dynamic(self, segment):
        return segment == "*" or segment.startswith(":")

    def compatible(self, first, second):
        if len(first) != len(second):
            return False

        for i in range(len(first)):
            left = first[i]
            right = second[i]

            # Either side may be a placeholder during searchRoutes.
            if self.is_dynamic(left) or self.is_dynamic(right):
                if not left or not right:
                    return False
            elif left != right:
                return False

        return True

    def is_better(self, candidate, best):
        if best is None:
            return True
        if candidate.static_count != best.static_count:
            return candidate.static_count > best.static_count
        if candidate.param_count != best.param_count:
            return candidate.param_count > best.param_count

        # Returning False on a complete tie keeps the earlier route.
        return len(candidate.segments) > len(best.segments)


@dataclass
class Route:
    """One stored pattern, its current result, and its specificity counts."""

    segments: list[str]
    result: str
    static_count: int = field(init=False, default=0)
    param_count: int = field(init=False, default=0)

    def __post_init__(self):
        # Count specificity once when the pattern is added.
        for segment in self.segments:
            if segment.startswith(":"):
                self.param_count += 1
            elif segment != "*":
                self.static_count += 1
```
