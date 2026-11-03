# Can All Factory Machines Start in Java

#### Problem Statement
[https://codezym.com/question/422-can-all-factory-machines-start](https://codezym.com/question/422-can-all-factory-machines-start)


Each requirement `"machine,requiredMachine"` simply means: start `requiredMachine` first, then `machine`.


Only one thing can make the answer `false`: a **circular wait**. For example, machine `0` waits for `3`, `3` waits for `1`, and `1` waits for `0`. Nobody in this circle can ever go first.


So we do what a real factory would do. Start every machine that is not waiting for anything. Each started machine may free up more machines. Keep going until nothing more can start. If every machine got started, the answer is `true`.


We will first write a simple version that works in rounds. Then we will make it fast by counting how many machines each machine is still waiting for. This fast version is the well known **topological sort** (Kahn's algorithm).


## Picture It as Arrows

Draw every requirement as an arrow from `requiredMachine` to `machine`. Read `0 ---> 2` as "0 must start before 2".


**Example 1:** `machineCount = 6`, requirements `["2,0", "2,1", "3,2", "5,4"]`

```text
0 ---\
      >---> 2 ---> 3
1 ---/

4 ---------> 5
```

Machines `0`, `1` and `4` have no arrow coming in, so they start first. Then `2` and `5` can start, and finally `3`. Answer: `true`.


**Example 2:** `machineCount = 5`, requirements `["1,0", "3,1", "0,3", "4,2"]`

```text
0 ---> 1 ---> 3
^             |
|             |
+-------------+

2 ---> 4
```

Machines `2` and `4` can start. But `0`, `1` and `3` form a circle, so none of them can ever start. Answer: `false`.


## Solution 1: Brute Force, Start Machines Round by Round

The simplest idea is to copy the factory step by step.


First, for every machine, save the list of machines it needs in `requiredMachines`. Then repeat rounds. In each round, check every machine that is not running yet. If all the machines it needs are running, start it.


Stop when a full round starts nothing new. If every machine is running at that point, return `true`.

```java
// requiredMachines.get(m) = machines that must be running before machine m
boolean[] running = new boolean[machineCount];
int startedCount = 0;
boolean startedInThisRound = true;

while (startedInThisRound) {
    startedInThisRound = false;

    for (int machine = 0; machine < machineCount; machine++) {
        // start the machine if everything it needs is already running
        if (!running[machine] && allRunning(requiredMachines.get(machine), running)) {
            running[machine] = true;
            startedCount++;
            startedInThisRound = true;
        }
    }
}

return startedCount == machineCount;
```

`allRunning()` just loops over the given machines and checks that each one is running.


### Problems With This Approach

This gives the right answer, but it does a lot of repeated work.

- A machine that is not ready gets checked again in every round, even when nothing it needs has changed.
- In the worst case, only one machine starts per round, for example when `0` needs `1`, `1` needs `2`, `2` needs `3`, and so on. That is up to `N` rounds, and every round looks at all machines and all requirements.


So the time is `O(N * (N + R))`, where `N` is `machineCount` and `R` is the number of requirements.


The key observation: a machine's status can change **only** when one of the machines it needs starts. So instead of checking everyone again and again, we should update only the machines that are affected.


## Solution 2: Optimal, Count What Each Machine Is Waiting For

Instead of asking every machine "are you ready now?" in every round, a machine that starts tells only the machines that were waiting for it.


### Key Data Structures

| Data structure | Why we need it |
|---|---|
| `waitingCount` (int array) | `waitingCount[m]` is how many of machine `m`'s required machines are not running yet. When it hits `0`, the machine is ready. One number replaces checking all its requirements again |
| `dependents` (list of lists) | `dependents.get(m)` holds the machines waiting for machine `m`. When `m` starts, we jump straight to the machines it affects |
| `readyMachines` (queue) | Holds machines that are ready but not started yet. Machines may start in any valid order, so a simple queue is enough |


### Steps

1. For each requirement `"machine,requiredMachine"`, add `machine` to `dependents` of `requiredMachine`, and increase `waitingCount[machine]` by 1.
2. Add every machine whose `waitingCount` is `0` to the queue.
3. Take a machine from the queue and start it. Increase `startedCount`.
4. For each dependent of this machine, decrease its `waitingCount` by 1. If it becomes `0`, add it to the queue.
5. Repeat until the queue is empty.
6. Return `startedCount == machineCount`.


### Why This Works

A machine inside a circle always waits for another machine of the same circle. So its count never drops to `0`, and it never enters the queue.


The same is true for any machine that waits on a circle, directly or through other machines. These machines are never started, so `startedCount` ends up smaller than `machineCount` and we return `false`.


A requirement like `"3,3"`, where a machine needs itself, is just a circle of size one. The code handles it without any special check.


### Dry Run on Example 1

Requirements: `["2,0", "2,1", "3,2", "5,4"]`

- `waitingCount = [0, 0, 2, 1, 0, 1]`
- `dependents`: `0 -> [2]`, `1 -> [2]`, `2 -> [3]`, `4 -> [5]`
- Queue at the start: `[0, 1, 4]`

| Started | What changes | Queue after |
|---|---|---|
| `0` | `waitingCount[2]` becomes `1` | `[1, 4]` |
| `1` | `waitingCount[2]` becomes `0`, add `2` | `[4, 2]` |
| `4` | `waitingCount[5]` becomes `0`, add `5` | `[2, 5]` |
| `2` | `waitingCount[3]` becomes `0`, add `3` | `[5, 3]` |
| `5` | nothing | `[3]` |
| `3` | nothing | `[]` |

All `6` machines started, so the answer is `true`.


In Example 2, the queue starts with only machine `2`, which then frees machine `4`. Machines `0`, `1` and `3` stay stuck at a count of `1`, so only `2` of `5` machines start and the answer is `false`.


### Complexity

- **Time:** `O(N + R)`. Each machine enters the queue at most once, and each requirement lowers a count at most once.
- **Space:** `O(N + R)` for `dependents`, `waitingCount` and the queue.


### Java Code

```java
import java.util.*;

public class FactoryStartupPlanner {

    public FactoryStartupPlanner() {

    }

    /**
     * Returns true if every machine can be started.
     * Machines start as soon as everything they need is running.
     * If some machines never get started, the requirements have a circular wait.
     */
    public boolean canStartAllMachines(int machineCount, List<String> startupRequirements) {
        // dependents.get(m) = machines that are waiting for machine m
        List<List<Integer>> dependents = new ArrayList<>();
        for (int i = 0; i < machineCount; i++) {
            dependents.add(new ArrayList<>());
        }

        // waitingCount[m] = how many of m's required machines are not running yet
        int[] waitingCount = new int[machineCount];

        for (String requirement : startupRequirements) {
            // "machine,requiredMachine" means requiredMachine must run before machine
            String[] parts = requirement.split(",");
            int machine = Integer.parseInt(parts[0]);
            int requiredMachine = Integer.parseInt(parts[1]);

            dependents.get(requiredMachine).add(machine);
            waitingCount[machine]++;
        }

        // machines that are not waiting for anything can start right away
        Queue<Integer> readyMachines = new ArrayDeque<>();
        for (int machine = 0; machine < machineCount; machine++) {
            if (waitingCount[machine] == 0) {
                readyMachines.add(machine);
            }
        }

        int startedCount = 0;
        while (!readyMachines.isEmpty()) {
            int machine = readyMachines.poll();
            startedCount++;

            // this machine is running now, so each dependent waits for one machine less
            for (int dependent : dependents.get(machine)) {
                waitingCount[dependent]--;
                if (waitingCount[dependent] == 0) {
                    readyMachines.add(dependent);
                }
            }
        }

        // if there is a circular wait, some machines never start
        return startedCount == machineCount;
    }
}
```


## Key Takeaways

- All machines can start exactly when the requirements have no circular wait.
- Counting what each machine still waits for means we never check the same machine again and again.
- "Start what is free, then unlock the next ones" is topological sort. The same idea solves many scheduling problems.