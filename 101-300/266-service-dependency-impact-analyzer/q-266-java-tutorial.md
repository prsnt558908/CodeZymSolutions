# Design Service Dependency Impact Analyzer in Java

#### Problem Statement

[https://codezym.com/question/266-service-dependency-impact-analyzer](https://codezym.com/question/266-service-dependency-impact-analyzer)

Store, for each service, the services that depend on it. When a service fails, use breadth-first search, or BFS, to follow the failure one level at a time. This gives the smallest impact depth for every affected service. For cycle detection, repeatedly remove services with no remaining dependencies. Both operations use maps, sets, and a queue.

## 1. Start with a simple scan

The entry `"order,payment"` means that `order` needs `payment`. To find what a payment failure affects, look for entries whose second name is `payment`.

A simple BFS can scan the entire dependency list whenever it removes a service from its queue. Its inner step looks like this:

```java
for (String entry : dependencies) {
    String[] parts = entry.split(",", 2);
    String service = parts[0];
    String dependency = parts[1];

    if (dependency.equals(current) && !depth.containsKey(service)) {
        depth.put(service, depth.get(current) + 1);
        queue.add(service);
    }
}
```

This works, but it repeatedly scans unrelated dependencies. With `V` services and `E` dependency entries, the traversal can take `O(V * E)` time.

## 2. Build a map of dependents

Read the dependency list once. For each `"service,dependency"` entry, add `service` to the set stored under `dependency`.

For example, `"order,payment"` adds `order` to `dependents["payment"]`. We have reversed the input edge so it points in the direction a failure spreads.

Use a set for each group because duplicate dependency entries count only once. Initialize an empty set for every service, including isolated ones.

Now BFS reads only the dependents of the current service.

## 3. Find the minimum impact depths

Start with the failed service at depth `0` and put it in a queue. Whenever BFS discovers an unseen dependent, give it the current depth plus `1` and add it to the queue.

The depth map also records which services have been visited. Mark a service when adding it to the queue, so it is queued only once.

BFS processes all services at depth `1` before depth `2`, and so on. Therefore, the first visit gives the shortest path, even when another route is longer.

Marking the failed service before traversal also prevents a cycle from adding it again. Remove it from the returned list, then sort the other services by numeric depth and service name.

`getImpactDepth` takes the largest value in the depth map. This is the greatest **minimum** depth, not the longest possible path. When nothing else is affected, the map contains only the failed service at depth `0`.

### A small example

```text
services = ["payment", "checkout", "order", "alerts", "audit"]

dependencies = [
    "checkout,payment",
    "order,checkout",
    "order,payment",
    "alerts,checkout",
    "order,payment"
]

failedService = "payment"
```

The arrows below show how a failure spreads:

```mermaid
flowchart TD
    payment --> checkout
    payment --> order
    checkout --> order
    checkout --> alerts
```

Both `checkout` and `order` have depth `1`. Although `order` can also be reached through `checkout`, its direct route is shorter. `alerts` has depth `2`. The duplicate entry changes nothing, and `audit` is unaffected.

```text
getImpactedServices: ["checkout,1", "order,1", "alerts,2"]
getImpactDepth: 2
hasCyclicDependency: false
```

## 4. Detect cycles across the whole graph

Use **Kahn's algorithm**, a queue-based method for topological sorting. The same reversed graph works because reversing every edge preserves whether a directed cycle exists.

Count the incoming edges for each service in the reversed graph. This count, called its `inDegree`, is the number of dependencies it still has.

1. Queue every service whose count is `0`, across all connected and disconnected groups.
2. Remove one queued service and increase the processed count.
3. Decrease the count of each of its dependents. Queue a dependent when its count becomes `0`.
4. If fewer than `V` services were processed, return `true`.

Why does this work? In an acyclic graph, every nonempty remaining part has a service with no remaining dependencies. A cycle cannot be fully removed because each member still needs another member. Services outside the cycle may also remain blocked, but any unprocessed services prove that a cycle exists.

Count edges from the sets built earlier so duplicates cannot inflate the counts. This method is iterative, so a long chain does not risk a recursion stack overflow.

## Complexity

Let `V` be the number of services, `E` the number of input dependency entries, and `K` the number of impacted services.

- `getImpactedServices`: `O(V + E + K log K)` time, including result sorting.
- `getImpactDepth`: `O(V + E)` time. It does not sort anything.
- `hasCyclicDependency`: `O(V + E)` time.
- Each method uses `O(V + E)` extra space.

These bounds use average constant-time map and set operations and treat the bounded service-name length as constant.

## Complete Solution

`ServiceDependencyImpactAnalyzer` keeps the required public methods together. One helper builds the graph, and another shares the BFS logic between the two impact methods. Each call builds fresh data from its arguments and leaves the input lists unchanged.

```java
import java.util.*;

public class ServiceDependencyImpactAnalyzer {

    public ServiceDependencyImpactAnalyzer() {
    }

    public List<String> getImpactedServices(
            List<String> services,
            List<String> dependencies,
            String failedService) {

        Map<String, Set<String>> dependents = buildDependents(services, dependencies);
        Map<String, Integer> depth = findImpactDepths(dependents, failedService);

        List<String> impacted = new ArrayList<>(depth.keySet());
        impacted.remove(failedService);

        // Sort by numeric depth first, then by service name.
        impacted.sort((first, second) -> {
            int comparison = Integer.compare(depth.get(first), depth.get(second));
            if (comparison != 0) {
                return comparison;
            }
            return first.compareTo(second);
        });

        List<String> result = new ArrayList<>();
        for (String service : impacted) {
            result.add(service + "," + depth.get(service));
        }
        return result;
    }

    public int getImpactDepth(
            List<String> services,
            List<String> dependencies,
            String failedService) {

        Map<String, Set<String>> dependents = buildDependents(services, dependencies);
        Map<String, Integer> depth = findImpactDepths(dependents, failedService);

        int maximumDepth = 0;
        for (int value : depth.values()) {
            maximumDepth = Math.max(maximumDepth, value);
        }
        return maximumDepth;
    }

    public boolean hasCyclicDependency(
            List<String> services,
            List<String> dependencies) {

        Map<String, Set<String>> dependents = buildDependents(services, dependencies);
        Map<String, Integer> inDegree = new HashMap<>();
        for (String service : services) {
            inDegree.put(service, 0);
        }

        // Count incoming edges in the reversed graph, after deduplication.
        for (Set<String> nextServices : dependents.values()) {
            for (String service : nextServices) {
                inDegree.put(service, inDegree.get(service) + 1);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (String service : services) {
            if (inDegree.get(service) == 0) {
                queue.add(service);
            }
        }

        int removedCount = 0;
        while (!queue.isEmpty()) {
            String current = queue.remove();
            removedCount++;

            for (String next : dependents.get(current)) {
                int remaining = inDegree.get(next) - 1;
                inDegree.put(next, remaining);
                if (remaining == 0) {
                    queue.add(next);
                }
            }
        }

        return removedCount < services.size();
    }

    Map<String, Set<String>> buildDependents(
            List<String> services,
            List<String> dependencies) {

        Map<String, Set<String>> dependents = new HashMap<>();
        for (String service : services) {
            dependents.put(service, new HashSet<>());
        }

        for (String dependency : dependencies) {
            String[] parts = dependency.split(",", 2);
            // "order,payment" becomes an impact edge from payment to order.
            dependents.get(parts[1]).add(parts[0]);
        }
        return dependents;
    }

    Map<String, Integer> findImpactDepths(
            Map<String, Set<String>> dependents,
            String failedService) {

        Map<String, Integer> depth = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();

        // Mark the source before traversal so a cycle cannot add it again.
        depth.put(failedService, 0);
        queue.add(failedService);

        while (!queue.isEmpty()) {
            String current = queue.remove();
            for (String next : dependents.get(current)) {
                if (!depth.containsKey(next)) {
                    depth.put(next, depth.get(current) + 1);
                    queue.add(next);
                }
            }
        }
        return depth;
    }
}
```
