# Route Handler Matching in Python

#### Problem Statement
[https://codezym.com/question/410-route-handler-matching](https://codezym.com/question/410-route-handler-matching)


A route is just a list of segments. `/stores/{storeId}/items` is `stores`, then a parameter, then `items`. So we can put all routes into a tree where every level is one segment. Routes that start the same way share the same branch.


To match a URL, we walk down this tree one URL segment at a time. From every node we can go two ways: to the child with exactly the same name, or to the parameter child, which accepts any value. We keep every node that still matches. When the URL ends, we pick the route with the most static segments.


We will first write a simple solution that checks every route, see why it is slow, and then build the tree. The follow-up method needs only a few extra lines after that.


## The Rules We Must Follow

- A route and a URL match only if they have the same number of segments.
- A static segment like `items` must be exactly equal to the URL segment. `Items` and `items` are different.
- A parameter like `{itemId}` matches any one URL segment.
- If many routes match, the route with the most static segments wins. If there is still a tie, the route registered first wins.


## Basic Solution: Check Every Route

Keep all routes in a list. For each URL, compare it with every route, segment by segment, and remember the best match.


To split, we drop the first `/` and split on `/`. So `/stores/42` becomes `["stores", "42"]`. In the code, the pieces of a route are called **segments** and the pieces of a URL are called **parts**.


```python
class RouteHandler:
    def __init__(self):
        self.routes = []

    def registerHandler(self, route: str) -> None:
        self.routes.append(route)

    def is_parameter(self, segment: str) -> bool:
        return segment.startswith("{") and segment.endswith("}")

    def getRouteHandler(self, url: str) -> str:
        parts = url[1:].split("/")
        best = ""
        best_static_count = -1

        for route in self.routes:  # check every route, in registration order
            segments = route[1:].split("/")
            if len(segments) != len(parts):
                continue

            static_count = 0
            matches = True
            for segment, part in zip(segments, parts):
                if self.is_parameter(segment):
                    continue  # a parameter takes any part
                if segment != part:
                    matches = False
                    break
                static_count += 1
            # ">" and not ">=", so on a tie the earlier route stays
            if matches and static_count > best_static_count:
                best = route
                best_static_count = static_count
        return best
```


This gives the right answer, but it has two problems.


**1. Every call checks every route.** With 10,000 routes and 100,000 calls, that is close to 1 billion route checks. We also split every route again on every call.


**2. The same work is done again and again.** If 500 routes start with `/stores/{storeId}`, we compare `stores` with the URL 500 times in a single call.


Splitting each route only once, when it is registered, fixes the small part of problem 1. Grouping routes by their number of segments also helps a bit. But routes of the same length are still checked one by one. To really fix this, routes that start the same way must share that work.


## Better Solution: A Tree of Segments

Let's put all routes into a tree, also called a **trie**. Every level of the tree is one segment. Take these three routes:

1. `/stores/{storeId}/items/{itemId}`
2. `/stores/sale/items/{itemId}`
3. `/teams/{teamId}`

```
(root)
 ├── stores
 │    ├── sale
 │    │    └── items
 │    │         └── {param}    ← /stores/sale/items/{itemId} ends here
 │    └── {param}
 │         └── items
 │              └── {param}    ← /stores/{storeId}/items/{itemId} ends here
 └── teams
      └── {param}              ← /teams/{teamId} ends here
```


Now a URL that starts with `/teams` never looks at the `stores` branch. And the two routes that start with `stores` share one `stores` node, so `stores` is compared only once.


### The TrieNode class

```python
class TrieNode:
    def __init__(self):
        self.static_children = {}
        self.param_child = None
        self.route = None
```


- **`static_children`** is a dictionary from a segment like `stores` to its child node. A dictionary finds the right child in one quick lookup, so we never loop over all children.
- **`param_child`** is a single child shared by all parameters. While matching, `{storeId}` and `{id}` behave the same way: both accept any segment. So the name does not matter inside the tree. We read the names later, from the route itself.
- **`route`** is the route that ends at this node, if there is one.


### Routes with the same shape

`/users/{id}` and `/users/{name}` end at the same node. They match exactly the same URLs and have the same number of static segments. So the one registered first will win every tie.


That's why each node keeps only the **first** route that ends there. A later route with the same shape can never be the answer.


### The Route class

```python
class Route:
    def __init__(self, path: str, order: int):
        self.path = path                     # the route exactly as it was registered
        self.segments = path[1:].split("/")  # split only once
        self.order = order                   # registration order, used to break ties
        self.static_count = 0                # how many segments are not parameters
        for segment in self.segments:
            if not self.is_parameter(segment):
                self.static_count += 1
```


We split each route once, when it is registered, and count its static segments right away. Then comparing two routes is just a check of two numbers: more static segments wins, and on a tie the smaller `order` wins. This is the `is_better_than()` method.


### Walking the URL

From a node, the next URL part can go two ways:

1. to the static child with the same name, if there is one
2. to the parameter child, if there is one


Can't we just pick the static child whenever it exists? No. Look at this case:

- Routes: `/a/{x}/{y}` and `/{p}/b/c`
- URL: `/a/b/c`


If we always prefer the static child, we go into `a` and end at `/a/{x}/{y}`, which has only 1 static segment. But `/{p}/b/c` has 2 static segments, so it should win.


So we follow **both** ways. We keep a list of **alive nodes**, the nodes that still match the URL so far. For each URL part, every alive node adds its matching static child and its parameter child to the next list. If the list ever becomes empty, nothing can match and we stop early.


When the URL ends, every alive node sits at the same depth as the URL. So any route stored there has the same number of segments as the URL. Some alive nodes have no route because they are only the beginning of longer routes. We skip those and pick the best route among the rest.


### Dry run

Let's match `/stores/sale/items/28` with the three routes from the tree above.

| URL part | Alive nodes after this step |
|---|---|
| start | root |
| `stores` | `stores` |
| `sale` | `stores/sale`, `stores/{param}` |
| `items` | `stores/sale/items`, `stores/{param}/items` |
| `28` | `stores/sale/items/{param}`, `stores/{param}/items/{param}` |


Both final nodes hold a route:

- `/stores/sale/items/{itemId}` has 3 static segments
- `/stores/{storeId}/items/{itemId}` has 2 static segments


So `/stores/sale/items/{itemId}` wins, just like in Example 3. The `teams` branch was never touched.


## Follow-up: Returning the Parameters

Once we have the best route, getting the parameters is easy. The route and the URL have the same number of segments, so they line up one to one:

```
route:  stores   {storeId}   items   {itemId}
url:    stores   42          items   900
```


For every parameter segment in the route, we take its name without the braces, and the URL part at the same position. `zip()` walks both lists together. That gives `storeId=42` and `itemId=900`. Going from left to right keeps them in route order.


## Complexity

Let `R` be the number of routes, `L` the length of the route or URL, and `V` the number of tree nodes that stay alive during one walk.

| Method | Basic solution | Tree solution |
|---|---|---|
| `registerHandler` | O(1) | O(L) |
| `getRouteHandler` | O(R × L) | O(L + V) |
| `getRouteHandlerWithParameters` | O(R × L) | O(L + V) |


Most branches die at the first static segment that does not match, so `V` stays small for normal routes. In the worst case, when many routes have parameters at the same positions, `V` grows. But it can never be bigger than the number of nodes in the tree.


Space is O(total length of all routes) for both solutions.


## Final Python Code

```python
from typing import List, Optional


class Route:
    """A registered route. We split it into segments once, here, so we never split it again."""

    def __init__(self, path: str, order: int):
        self.path = path                     # the route exactly as it was registered
        self.segments = path[1:].split("/")  # "/stores/{storeId}" becomes ["stores", "{storeId}"]
        self.order = order                   # 0 for the first registered route, 1 for the next ...
        self.static_count = 0                # how many segments are not parameters
        for segment in self.segments:
            if not self.is_parameter(segment):
                self.static_count += 1

    def is_parameter(self, segment: str) -> bool:
        """A parameter segment looks like {name}."""
        return segment.startswith("{") and segment.endswith("}")

    def is_better_than(self, other: Optional["Route"]) -> bool:
        """More static segments wins. On a tie, the route registered first wins."""
        if other is None:
            return True
        if self.static_count != other.static_count:
            return self.static_count > other.static_count
        return self.order < other.order


class TrieNode:
    """One node of the route tree. A node at depth d stands for the d-th segment of a route."""

    def __init__(self):
        self.static_children = {}  # one child for each static segment
        self.param_child = None    # one shared child for any parameter, its name does not matter
        self.route = None          # the first registered route that ends exactly at this node


class RouteHandler:
    def __init__(self):
        self.root = TrieNode()
        self.route_count = 0  # number of routes registered so far

    def registerHandler(self, route: str) -> None:
        new_route = Route(route, self.route_count)
        self.route_count += 1

        node = self.root
        for segment in new_route.segments:
            if new_route.is_parameter(segment):
                if node.param_child is None:
                    node.param_child = TrieNode()
                node = node.param_child
            else:
                if segment not in node.static_children:
                    node.static_children[segment] = TrieNode()
                node = node.static_children[segment]
        # If a route with the same shape came earlier, it wins every tie, so we keep it.
        if node.route is None:
            node.route = new_route

    def getRouteHandler(self, url: str) -> str:
        best = self.find_best_route(url[1:].split("/"))
        return "" if best is None else best.path

    def getRouteHandlerWithParameters(self, url: str) -> List[str]:
        parts = url[1:].split("/")
        best = self.find_best_route(parts)
        if best is None:
            return []

        result = [best.path]
        # The route matched, so its i-th segment lines up with the i-th URL part.
        for segment, part in zip(best.segments, parts):
            if best.is_parameter(segment):
                result.append(segment[1:-1] + "=" + part)
        return result

    def find_best_route(self, parts: List[str]) -> Optional[Route]:
        """Walks down the tree one URL part at a time. Returns the best matching route or None."""
        alive = [self.root]  # nodes that still match the URL so far

        for part in parts:
            next_nodes = []
            for node in alive:
                # A static child must be exactly equal to this part.
                child = node.static_children.get(part)
                if child is not None:
                    next_nodes.append(child)
                # A parameter child accepts any part.
                if node.param_child is not None:
                    next_nodes.append(node.param_child)
            if not next_nodes:
                return None  # no route can match anymore
            alive = next_nodes

        # Every alive node now sits at the same depth as the URL, so its route has
        # the same number of segments. Pick the best one.
        best = None
        for node in alive:
            if node.route is not None and node.route.is_better_than(best):
                best = node.route
        return best
```