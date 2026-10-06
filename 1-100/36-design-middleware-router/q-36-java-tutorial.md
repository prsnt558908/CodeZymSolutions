# Design a Middleware Router in Java

#### Problem Statement
[https://codezym.com/question/36-design-middleware-router](https://codezym.com/question/36-design-middleware-router)


Split each path into segments, compare matching routes, and return the most specific one. An ordered map keeps routes in their original order, even when their results change. For these fixed rules, a small `Route` class and simple helper methods are clearer than adding a design pattern.


## Start with a simple scan

We could store patterns and results in a list. For each request, split every pattern, check whether it matches, and count its static and parameter segments.


This works, but it repeats the same splitting and counting on every request. Updating an existing pattern also requires searching the list.


Keep the scan, but parse each pattern only when it is added. Use a map to find an existing pattern directly when updating its result.


## Store each route once

`Route` holds the parsed segments, the current result, and two counts: static segments and parameter segments. The counts make precedence checks simple.


`LinkedHashMap<String, Route>` maps the original pattern to its route and remembers insertion order. Updating a route changes only its result. Distinct patterns such as `/users/:id` and `/users/:name` remain separate entries.


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


Adding or updating a route takes `O(P)` expected time, including map-key hashing and parsing a new pattern. Both lookup methods take `O(R × P)` time because they scan the stored routes.


Stored patterns and their parsed segments use `O(R × P)` space, in addition to the result strings. A lookup uses `O(P)` temporary space for its parsed input. Search also needs up to `O(R)` space for its returned list.


## Code

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Router {
    Map<String, Route> routes;

    public Router() {
        routes = new LinkedHashMap<>();
    }

    /** Add a pattern, or update its result without changing its order. */
    public void addRoute(String pathPattern, String result) {
        Route existing = routes.get(pathPattern);
        if (existing != null) {
            existing.result = result;
            return;
        }

        routes.put(pathPattern, new Route(splitPath(pathPattern), result));
    }

    /** Return the most specific matching route. */
    public String callRoute(String path) {
        String[] segments = splitPath(path);
        Route best = null;

        for (Route route : routes.values()) {
            if (compatible(route.segments, segments) && isBetter(route, best)) {
                best = route;
            }
        }

        return best == null ? "NOT_FOUND" : best.result;
    }

    /** Return all compatible results in their original insertion order. */
    public List<String> searchRoutes(String wildcardPattern) {
        String[] query = splitPath(wildcardPattern);
        List<String> results = new ArrayList<>();

        for (Route route : routes.values()) {
            if (compatible(route.segments, query)) {
                results.add(route.result);
            }
        }

        return results;
    }

    String[] splitPath(String path) {
        if (path.equals("/")) {
            return new String[0];
        }

        // Keep empty segments so placeholders cannot match an empty value.
        return path.substring(1).split("/", -1);
    }

    boolean isDynamic(String segment) {
        return segment.equals("*") || segment.startsWith(":");
    }

    boolean compatible(String[] first, String[] second) {
        if (first.length != second.length) {
            return false;
        }

        for (int i = 0; i < first.length; i++) {
            String left = first[i];
            String right = second[i];

            // Either side may be a placeholder during searchRoutes.
            if (isDynamic(left) || isDynamic(right)) {
                if (left.isEmpty() || right.isEmpty()) {
                    return false;
                }
            } else if (!left.equals(right)) {
                return false;
            }
        }

        return true;
    }

    boolean isBetter(Route candidate, Route best) {
        if (best == null) {
            return true;
        }
        if (candidate.staticCount != best.staticCount) {
            return candidate.staticCount > best.staticCount;
        }
        if (candidate.paramCount != best.paramCount) {
            return candidate.paramCount > best.paramCount;
        }

        // Returning false on a complete tie keeps the earlier route.
        return candidate.segments.length > best.segments.length;
    }
}

/** One stored pattern, its current result, and its specificity counts. */
class Route {
    String[] segments;
    String result;
    int staticCount;
    int paramCount;

    Route(String[] segments, String result) {
        this.segments = segments;
        this.result = result;

        // Count specificity once when the pattern is added.
        for (String segment : segments) {
            if (segment.startsWith(":")) {
                paramCount++;
            } else if (!segment.equals("*")) {
                staticCount++;
            }
        }
    }
}
```
