# Service Deployment Order in Java

#### Problem Statement
[https://codezym.com/question/481-service-deployment-order](https://codezym.com/question/481-service-deployment-order)

---

## Core Idea

Think of every service as a box that stays **locked** until all the services it needs are running. A dependency `"A,B"` simply means: **B unlocks A**.


At the start, services that need nothing are already unlocked. When we start one of them, it may unlock a few others. We keep repeating this until nothing unlocked is left.


This "start what is free, then unlock the next ones" idea is the well known **topological sort** (Kahn's algorithm). The only twist is that when many services are ready, we must pick the **smallest ID**. A **min-heap** (`PriorityQueue`) gives us that smallest ready ID quickly.


If some services never get unlocked, they are stuck in a cycle, so we return an empty list.

---

## Understanding the Problem

Direction matters, so let us be clear about it.

| Input | Meaning |
|---|---|
| `"3,0"` | 0 must be running before 3 can start |
| `"0,0"` | 0 needs itself, so it can never start |
| `"1,2"` given twice | same as giving it once |


**The picking rule:** at every step, look at the services that are not started yet and whose needs are all running. Start the one with the smallest ID.


**Example 1:** `serviceCount = 5`, dependencies `"0,2", "1,2", "3,0", "3,1"`

- Ready at start: 2 and 4. Start **2**.
- 2 is running, so 0 and 1 become ready. Ready: 0, 1, 4. Start **0**.
- Ready: 1, 4. Start **1**.
- 0 and 1 are running, so 3 becomes ready. Ready: 3, 4. Start **3**.
- Ready: 4. Start **4**.

Answer: `[2, 0, 1, 3, 4]`


Notice that 4 was ready from the very beginning, but it still went last. Smaller IDs that became ready later jumped ahead of it. Keep this in mind, because it is the reason we need a heap later.

---

## Solution 1: Brute Force (Simulate Step by Step)

### Idea

Just do exactly what the problem says.


For each step:

1. Scan IDs from `0` to `serviceCount - 1`.
2. Skip services that already started.
3. For each remaining service, check if all its needed services are running.
4. The first one that passes is the smallest ready ID. Start it.
5. If no service passes, the rest are stuck in a cycle. Return `[]`.


We need only two things:

- `needs`: for every service, the list of services it needs.
- `started`: a boolean array that marks running services.

### Code

```java
import java.util.*;

public class ServiceDeploymentOrder {

    public ServiceDeploymentOrder() {
    }

    public List<Integer> findDeploymentOrder(int serviceCount, List<String> dependencies) {
        // needs.get(a) = services that must be running before a can start
        List<List<Integer>> needs = new ArrayList<>();
        for (int i = 0; i < serviceCount; i++) {
            needs.add(new ArrayList<>());
        }
        for (String dependency : dependencies) {
            String[] parts = dependency.split(",");
            int service = Integer.parseInt(parts[0]);
            int requiredService = Integer.parseInt(parts[1]);
            needs.get(service).add(requiredService);
        }

        boolean[] started = new boolean[serviceCount];
        List<Integer> order = new ArrayList<>();

        // Each step starts exactly one service
        for (int step = 0; step < serviceCount; step++) {
            int pick = -1;

            // Scan IDs from smallest to largest, the first ready one wins
            for (int id = 0; id < serviceCount && pick == -1; id++) {
                if (started[id]) {
                    continue;
                }
                boolean allRunning = true;
                for (int required : needs.get(id)) {
                    if (!started[required]) {
                        allRunning = false;
                        break;
                    }
                }
                if (allRunning) {
                    pick = id;
                }
            }

            // Nobody can start, so the remaining services form a cycle
            if (pick == -1) {
                return new ArrayList<>();
            }
            started[pick] = true;
            order.add(pick);
        }
        return order;
    }
}
```

### Complexity

- **Time:** `O(N × (N + E))`, where `N` is `serviceCount` and `E` is the number of dependencies. Each of the `N` steps may scan every service and every dependency list.
- **Space:** `O(N + E)`

### Problem With This Approach

The answer is correct, but it is too slow for big inputs.


With `N = 100,000`, there are 100,000 steps and each step may scan 100,000 services. That is billions of operations.


There are two kinds of wasted work:

1. **Rechecking the same needs again and again.** A service with 5 needs gets all 5 checked on every step, even when nothing changed for it.
2. **Scanning from ID 0 every time** just to find the smallest ready service.


Let us fix both.

---

## Solution 2: Optimal (Counting + Min-Heap)

### Fix 1: Keep a Count Instead of Rechecking

For every service, keep one number:

`waitingOn[i]` = how many of its needed services are **not running yet**.


A service is ready exactly when `waitingOn[i] == 0`. We never recheck lists anymore, we just update a counter.

### Fix 2: Know Whom to Notify

When service `B` starts, who cares about it? Only the services that need `B`.


So instead of storing "what does A need", we store the reverse:

`dependents.get(B)` = list of services waiting for `B`.


When `B` starts, we walk this list and reduce each service's `waitingOn` by 1. If a count hits 0, that service just became ready.


Now every dependency is touched only once during the whole run.

### Fix 3: Min-Heap for the Smallest Ready ID

We now have a pool of ready services that keeps growing, and we always want the smallest one.

- **Sorting once at the start does not work.** New services become ready later. In Example 1, services 0 and 1 became ready after 4, but still had to go before 4.
- **A plain queue does not work either.** It gives a valid order, but not the smallest one. For Example 1 it gives `[2, 4, 0, 1, 3]`.
- **A min-heap works.** `PriorityQueue` in Java hands out the smallest element and accepts new ones, both in `O(log N)`.

### Detecting a Cycle

Services inside a cycle wait for each other forever. Their count never reaches 0, so they never enter the heap.


So at the end, if `order.size() < serviceCount`, some services were stuck. We return an empty list.

### What About Duplicate Pairs?

If `"0,2"` appears twice, we add 0 to `dependents.get(2)` twice, and `waitingOn[0]` becomes 2.


When 2 starts, we walk its list and reduce the count of 0 twice, so it reaches 0 at the right moment. The extra copy adds 1 and later removes 1, so it cancels out.


Service 0 is added to the heap only once, at the exact moment its count becomes 0. No special handling is needed.

### Key Data Structures

| Data structure | Why we need it |
|---|---|
| `int[] waitingOn` | Tells us in `O(1)` if a service is ready, without rechecking its needs |
| `List<List<Integer>> dependents` | When a service starts, tells us exactly which services to update |
| `PriorityQueue<Integer> ready` | Always gives the smallest ready ID, even as new ones keep arriving |
| `List<Integer> order` | The final start order. Its size also tells us if there was a cycle |

### Dry Run on Example 2

`serviceCount = 4`, dependencies `"0,1", "1,0", "3,2"`

- `waitingOn = [1, 1, 0, 1]`
- Ready heap: `{2}`
- Start 2. Service 3's count drops to 0. Heap: `{3}`
- Start 3. Heap is empty.
- `order = [2, 3]`. Its size is 2, which is less than 4, so we return `[]`.


Services 0 and 1 kept waiting for each other and never started.

### Code

```java
import java.util.*;

public class ServiceDeploymentOrder {

    public ServiceDeploymentOrder() {
    }

    public List<Integer> findDeploymentOrder(int serviceCount, List<String> dependencies) {
        // waitingOn[i] = number of dependencies of service i that have not started yet
        int[] waitingOn = new int[serviceCount];

        // dependents.get(b) = services that are waiting for b to start
        List<List<Integer>> dependents = new ArrayList<>();
        for (int i = 0; i < serviceCount; i++) {
            dependents.add(new ArrayList<>());
        }

        // "A,B" means B must start before A
        for (String dependency : dependencies) {
            String[] parts = dependency.split(",");
            int service = Integer.parseInt(parts[0]);
            int needs = Integer.parseInt(parts[1]);
            dependents.get(needs).add(service);
            waitingOn[service]++;
        }

        // Min-heap: always gives the smallest ID among ready services
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < serviceCount; i++) {
            if (waitingOn[i] == 0) {
                ready.add(i);
            }
        }

        List<Integer> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int current = ready.poll();
            order.add(current);

            // current is running now, so everyone waiting on it has one less thing to wait for
            for (int next : dependents.get(current)) {
                waitingOn[next]--;
                if (waitingOn[next] == 0) {
                    ready.add(next);
                }
            }
        }

        // Some services never became ready, so there is a cycle
        if (order.size() < serviceCount) {
            return new ArrayList<>();
        }
        return order;
    }
}
```

### Complexity

- **Time:** `O(N log N + E)`. Each service enters and leaves the heap at most once, which costs `O(log N)` each time. Each dependency is read once and processed once.
- **Space:** `O(N + E)` for the dependents lists, the counts and the heap.

---

## Key Takeaways

- "Start what is free, then unlock the next ones" is topological sort.
- Swapping the normal queue for a **min-heap** turns it into "smallest ID first".
- If the final order is shorter than `serviceCount`, there is a cycle.