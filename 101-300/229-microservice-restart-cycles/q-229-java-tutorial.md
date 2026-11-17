# Microservice Restart Cycles in Java

#### Problem Statement
[https://codezym.com/question/229-microservice-restart-cycles](https://codezym.com/question/229-microservice-restart-cycles)


## Core Idea

Every service waits for its dependencies, so the services form a dependency graph. We need the cycle in which each service starts, and the answer is the largest one.


The checking order (index `0` to `n - 1`) gives us a simple rule. A dependency with a **smaller index** lets the service start in the same cycle as that dependency. A dependency with a **bigger index** makes the service wait one extra cycle.


So we can calculate the start cycle of every service directly from its dependencies, without running the cycles one by one. We do this in dependency order using **topological sort** (Kahn's algorithm). It also detects circular dependencies for us.


## Approach 1: Simulate Every Cycle (Brute Force)

The simplest idea is to do exactly what the problem says.


In each cycle, go from service `0` to `n - 1`. If a service is not started and all its dependencies are started, start it right away. Because we mark it as started immediately, services checked later in the same cycle can count on it.


If a full cycle starts nothing, the remaining services can never start because of a circular dependency. So we return `-1`.

```java
// deps.get(i) = list of dependencies of service i (parsed from the input strings)
boolean[] started = new boolean[n];
int startedCount = 0, cycles = 0;

while (startedCount < n) {
    cycles++;
    int startedThisCycle = 0;

    for (int i = 0; i < n; i++) {
        if (started[i]) continue;

        boolean ready = true;
        for (int d : deps.get(i)) {
            if (!started[d]) { ready = false; break; }
        }

        if (ready) {
            started[i] = true;   // services after i in this cycle can use it
            startedCount++;
            startedThisCycle++;
        }
    }

    // nothing started in this cycle, so the rest can never start
    if (startedThisCycle == 0) return -1;
}
return cycles;
```


### Why is this slow?

The number of cycles can be as large as `n`.


Example: service `i` depends on service `i + 1`, and the last service has no dependencies. Every service is checked before the service it needs, so only one new service starts per cycle. We need `n` cycles, and each cycle scans all `n` services.


So the time is `O(n * (n + E))`, where `E` is the total number of dependency values. For `n = 100,000` that is about `10^10` checks. Most of them are wasted, because we keep checking services whose dependencies have not changed.


## Approach 2: Topological Sort (Optimal)

### Key Observation

Say service `s` depends on service `d`, and `d` starts in cycle `c`.

- **`d < s`**: `d` is checked before `s` in cycle `c`. So `s` can also start in cycle `c`.
- **`d > s`**: `s` is checked before `d`. In cycle `c`, when `s` is checked, `d` has not started yet. So `s` can start only in cycle `c + 1`.


A service must wait for all its dependencies, so it starts in the latest of these cycles:

```
startCycle[s] = 1                       if s has no dependencies

startCycle[s] = max over each dependency d of:
                    startCycle[d]       if d < s
                    startCycle[d] + 1   if d > s
```

The answer is the largest `startCycle` among all services.


This also explains Example 1. Every dependency there has a smaller index, so all four services start in cycle `1`.


### In Which Order Do We Calculate?

To calculate `startCycle[s]`, we must already know the start cycle of every dependency of `s`. Dependencies can have bigger indices, so a simple loop from `0` to `n - 1` does not work.


We need **topological order**: a service is processed only after all its dependencies are processed. Kahn's algorithm gives us this order:

1. For each service, count how many dependencies it is still waiting for.
2. Put every service with no dependencies in a queue. They start in cycle `1`.
3. Take a service out of the queue. For each service that depends on it, update its start cycle using the formula and reduce its waiting count by `1`.
4. When a waiting count becomes `0`, all dependencies of that service are done, so its start cycle is final. Add it to the queue.


### Detecting Circular Dependencies

Services in a circle wait for each other forever. Their waiting count never becomes `0`, so they never enter the queue. Any service that depends on them gets stuck too.


So if fewer than `n` services are processed, we return `-1`.


### Important Data Structures

- **`dependents`** (list of lists): `dependents.get(d)` holds all services that depend on `d`. The input tells us what each service depends on, but when `d` is done we need the opposite: who is waiting for `d`. With this list we update only those services, instead of scanning everyone like the brute force.
- **`waitingCount`** (int array): how many dependencies of each service are not processed yet. It tells us the exact moment a service is ready.
- **`startCycle`** (int array): the cycle in which each service starts.
- **`queue`** (`ArrayDeque`): services whose start cycle is final and who still need to update their dependents. The order in which they leave the queue does not matter, because a service is only processed after all its dependencies are final.


### Dry Run (Example 2)

`n = 4`, `dependencies = ["1,1", "0", "1,3", "0"]`


Service `0` depends on `1`. Service `2` depends on `3`. Services `1` and `3` have no dependencies.

| Step | Take from queue | Update | Queue after |
|------|-----------------|--------|-------------|
| Start | - | `startCycle[1] = 1`, `startCycle[3] = 1` | `[1, 3]` |
| 1 | `1` | `1 > 0`, so `startCycle[0] = 1 + 1 = 2` | `[3, 0]` |
| 2 | `3` | `3 > 2`, so `startCycle[2] = 1 + 1 = 2` | `[0, 2]` |
| 3 | `0` | no dependents | `[2]` |
| 4 | `2` | no dependents | `[]` |

All `4` services were processed and the largest start cycle is `2`. So the answer is `2`.


### Complexity

- **Time:** `O(n + E)`. Each service enters the queue once, and each dependency is looked at once.
- **Space:** `O(n + E)` for the `dependents` lists and the arrays.


### Java Code

```java
import java.util.*;

public class MicroserviceRestartCycles {

    public MicroserviceRestartCycles() {

    }

    /**
     * Returns the number of restart cycles needed to start all services,
     * or -1 if some services can never start because of a circular dependency.
     */
    public int minimumCycles(int n, List<String> dependencies) {
        // dependents.get(d) = services that depend on service d
        List<List<Integer>> dependents = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            dependents.add(new ArrayList<>());
        }

        // waitingCount[s] = dependencies of service s that are not processed yet
        int[] waitingCount = new int[n];

        for (int s = 0; s < n; s++) {
            // format is "K,dep1,dep2,...", so the dependencies start at index 1
            String[] parts = dependencies.get(s).split(",");
            for (int j = 1; j < parts.length; j++) {
                int d = Integer.parseInt(parts[j]);
                dependents.get(d).add(s);
                waitingCount[s]++;
            }
        }

        // startCycle[s] = the cycle in which service s starts
        int[] startCycle = new int[n];
        Queue<Integer> queue = new ArrayDeque<>();

        // services with no dependencies start in the first cycle
        for (int s = 0; s < n; s++) {
            if (waitingCount[s] == 0) {
                startCycle[s] = 1;
                queue.add(s);
            }
        }

        int processed = 0;
        int answer = 0;

        while (!queue.isEmpty()) {
            int d = queue.poll();
            processed++;
            answer = Math.max(answer, startCycle[d]);

            for (int s : dependents.get(d)) {
                // a dependency with a bigger index starts after s was checked,
                // so s has to wait for the next cycle
                int earliest = (d < s) ? startCycle[d] : startCycle[d] + 1;
                startCycle[s] = Math.max(startCycle[s], earliest);

                waitingCount[s]--;
                if (waitingCount[s] == 0) {
                    // all dependencies of s are done, so startCycle[s] is final
                    queue.add(s);
                }
            }
        }

        // services stuck in a circular dependency never reach the queue
        return (processed == n) ? answer : -1;
    }
}
```