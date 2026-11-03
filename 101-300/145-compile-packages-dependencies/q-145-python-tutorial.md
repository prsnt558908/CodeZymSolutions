# Compile Packages with Dependencies in a Multi-Threaded Environment in Python

#### Problem Statement
[https://codezym.com/question/145-compile-packages-dependencies](https://codezym.com/question/145-compile-packages-dependencies)


This problem is a topological sort, which simply means putting packages in an order where every package comes after the packages it depends on. The twist is that packages are compiled in rounds, and one round can compile at most `threadCount` packages.

For every package, we keep a count of its dependencies that are not compiled yet. When the count becomes zero, the package is ready. All ready packages wait in a min-heap, so the smallest name always comes out first.

In each round, we take out up to `threadCount` packages from the heap and compile them. Only after that, we unlock the packages that were waiting for them. If some packages never get unlocked, the graph has a cycle and we return an empty list.

We do not create any real threads. `threadCount` only limits how many packages one round can compile, so a simple loop over rounds is enough.


## Basic Solution: Check Every Package in Every Round

The most direct way is to simulate the rounds exactly as the problem describes them.

Assume we have already read the rows into a dictionary `dependencies` (package -> its direct dependencies). A package that appears only as a dependency gets an empty list.

In every round, we scan all packages and collect the ones that are not compiled yet and whose dependencies are all compiled. Then we sort them and compile the first `threadCount` names.

```python
compiled = set()
result = []

while len(compiled) < len(dependencies):
    # 1. scan every package and collect the ones that are ready now
    ready = []
    for pkg in dependencies:
        if pkg not in compiled and all(dep in compiled for dep in dependencies[pkg]):
            ready.append(pkg)

    # 2. packages are left, but none of them is ready: there is a cycle
    if not ready:
        return []

    # 3. compile at most threadCount packages, smallest names first
    ready.sort()
    for pkg in ready[:threadCount]:
        compiled.add(pkg)
        result.append(pkg)

return result
```

### Why is it slow?

Every round scans all packages and all their dependencies again, even though only a few packages changed since the last round.

With `threadCount = 1`, there is one round per package. So there can be 100,000 rounds, and each one scans up to 100,000 packages and 200,000 dependency links. That is tens of billions of steps.

We also sort the ready list in every round, even the packages that were left over and already sorted in the last round.


## Better Solution: Dependency Counts + Min-Heap

We fix both problems one at a time.

### Fix 1: Count the dependencies that are left

Instead of checking all dependencies again and again, we keep two dictionaries.

- `pending_count`: package -> how many of its dependencies are not compiled yet.
- `dependents`: package -> packages that are waiting for it.

For the row `"app,core,ui"`, we add `app` to the `dependents` list of both `core` and `ui`, and the count of `app` becomes 2.

Now when a package is compiled, we only visit its own `dependents` list and reduce each of their counts by one. When a count becomes zero, that package is ready.

Each dependency link is used only once in the whole run. This idea is known as Kahn's algorithm.

A name that appears only as a dependency (like `ui` in Example 4) still goes into both dictionaries. Its count stays zero, so it is ready from the start.

### Fix 2: Keep ready packages in a min-heap

We always need the smallest names first. Python's `heapq` module turns a plain list into a min-heap, and `heappop` takes out the smallest name in `O(log n)` time.

Packages that were ready but not picked in a round simply stay in the heap. In the next round, they compete with the newly ready ones, and nothing gets sorted again.

### The round rule

A package that gets unlocked during a round can only be compiled in a later round. The basic solution got this for free, because it built the whole ready list before compiling anything.

With a heap, we have to do it on purpose. So every round has two steps:

1. Take out up to `threadCount` packages from the heap. These are this round's packages.
2. Compile them, and only then reduce the counts of their dependents.

The order of these steps matters. Take `packages = ["a,b", "b", "z"]` with `threadCount = 2`.

Round 1 starts with `b` and `z` ready, so the answer is `["b", "z", "a"]`. If we pushed `a` into the heap right after compiling `b`, then `a` would come out before `z` in the same round, and we would wrongly return `["b", "a", "z"]`.

### Finding a cycle

Packages in a cycle wait for each other forever. Their count never becomes zero, so they are never compiled.

So at the end, if we compiled fewer packages than the total, we return an empty list.

### Dry run

Example 2: `packages = ["backend,network,storage", "frontend,ui", "network", "storage", "ui"]`, `threadCount = 2`

| Round | Heap at the start | Compiled | Unlocked after the round |
|---|---|---|---|
| 1 | network, storage, ui | network, storage | backend |
| 2 | backend, ui | backend, ui | frontend |
| 3 | frontend | frontend | none |

`ui` was ready in round 1 but did not fit. In round 2, it competes with `backend`, and `backend` goes first because it is smaller.

Final order: `["network", "storage", "backend", "ui", "frontend"]`


## Final Python Code

```python
import heapq
from typing import List


class PackageCompiler:

    def __init__(self):
        pass

    def compilePackages(self, packages: List[str], threadCount: int) -> List[str]:
        """
        Compiles packages round by round. Each round compiles at most threadCount
        packages that were ready when the round started, smallest names first.
        Returns an empty list if the dependency graph has a cycle.
        """
        # package -> how many of its dependencies are not compiled yet
        pending_count = {}
        # package -> packages that are waiting for it to be compiled
        dependents = {}

        # 1. Build the graph from the rows
        for row in packages:
            names = row.split(",")

            # register every name, even one that only appears as a dependency
            for name in names:
                if name not in pending_count:
                    pending_count[name] = 0
                    dependents[name] = []

            # names[0] depends on every other name in the row
            pkg = names[0]
            for dependency in names[1:]:
                dependents[dependency].append(pkg)
                pending_count[pkg] += 1

        # 2. Packages with no pending dependencies are ready.
        # The min-heap always gives the smallest name first.
        ready = []
        for pkg in pending_count:
            if pending_count[pkg] == 0:
                heapq.heappush(ready, pkg)

        # 3. Compile round by round
        result = []
        while ready:
            # first take out this round's packages, so packages unlocked
            # during this round can only be picked in a later round
            current_round = []
            while ready and len(current_round) < threadCount:
                current_round.append(heapq.heappop(ready))

            # compile them and unlock the packages waiting for them
            for pkg in current_round:
                result.append(pkg)
                for dependent in dependents[pkg]:
                    pending_count[dependent] -= 1
                    if pending_count[dependent] == 0:
                        heapq.heappush(ready, dependent)

        # 4. Some packages were never compiled: the graph has a cycle
        if len(result) < len(pending_count):
            return []
        return result
```


## Complexity

Let `P` be the number of distinct packages and `E` the number of dependency links.

- **Time:** `O(P log P + E)`. Every package enters and leaves the heap once, and every dependency link reduces a count once.
- **Space:** `O(P + E)` for the two dictionaries, the heap and the result.