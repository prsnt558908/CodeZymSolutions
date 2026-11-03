# Can All Factory Machines Start in Python

#### Problem Statement
[https://codezym.com/question/422-can-all-factory-machines-start](https://codezym.com/question/422-can-all-factory-machines-start)


Each requirement `"machine,requiredMachine"` simply means: start `requiredMachine` first, then `machine`.


Only one thing can make the answer `False`: a **circular wait**. For example, machine `0` waits for `3`, `3` waits for `1`, and `1` waits for `0`. Nobody in this circle can ever go first.


So we do what a real factory would do. Start every machine that is not waiting for anything. Each started machine may free up more machines. Keep going until nothing more can start. If every machine got started, the answer is `True`.


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

Machines `0`, `1` and `4` have no arrow coming in, so they start first. Then `2` and `5` can start, and finally `3`. Answer: `True`.


**Example 2:** `machineCount = 5`, requirements `["1,0", "3,1", "0,3", "4,2"]`

```text
0 ---> 1 ---> 3
^             |
|             |
+-------------+

2 ---> 4
```

Machines `2` and `4` can start. But `0`, `1` and `3` form a circle, so none of them can ever start. Answer: `False`.


## Solution 1: Brute Force, Start Machines Round by Round

The simplest idea is to copy the factory step by step.


First, for every machine, save the list of machines it needs in `required_machines`. Then repeat rounds. In each round, check every machine that is not running yet. If all the machines it needs are running, start it.


Stop when a full round starts nothing new. If every machine is running at that point, return `True`.

```python
# required_machines[m] = machines that must be running before machine m
running = [False] * machineCount
started_count = 0
started_in_this_round = True

while started_in_this_round:
    started_in_this_round = False

    for machine in range(machineCount):
        # start the machine if everything it needs is already running
        if not running[machine] and self.allRunning(required_machines[machine], running):
            running[machine] = True
            started_count += 1
            started_in_this_round = True

return started_count == machineCount
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
| `waiting_count` (list of ints) | `waiting_count[m]` is how many of machine `m`'s required machines are not running yet. When it hits `0`, the machine is ready. One number replaces checking all its requirements again |
| `dependents` (list of lists) | `dependents[m]` holds the machines waiting for machine `m`. When `m` starts, we jump straight to the machines it affects |
| `ready_machines` (`deque`) | Holds machines that are ready but not started yet. Machines may start in any valid order, so a simple queue is enough. A `deque` removes from the front in `O(1)` time |


### Steps

1. For each requirement `"machine,requiredMachine"`, add `machine` to `dependents` of `requiredMachine`, and increase `waiting_count[machine]` by 1.
2. Add every machine whose `waiting_count` is `0` to the queue.
3. Take a machine from the queue and start it. Increase `started_count`.
4. For each dependent of this machine, decrease its `waiting_count` by 1. If it becomes `0`, add it to the queue.
5. Repeat until the queue is empty.
6. Return `started_count == machineCount`.


### Why This Works

A machine inside a circle always waits for another machine of the same circle. So its count never drops to `0`, and it never enters the queue.


The same is true for any machine that waits on a circle, directly or through other machines. These machines are never started, so `started_count` ends up smaller than `machineCount` and we return `False`.


A requirement like `"3,3"`, where a machine needs itself, is just a circle of size one. The code handles it without any special check.


### Dry Run on Example 1

Requirements: `["2,0", "2,1", "3,2", "5,4"]`

- `waiting_count = [0, 0, 2, 1, 0, 1]`
- `dependents`: `0 -> [2]`, `1 -> [2]`, `2 -> [3]`, `4 -> [5]`
- Queue at the start: `[0, 1, 4]`

| Started | What changes | Queue after |
|---|---|---|
| `0` | `waiting_count[2]` becomes `1` | `[1, 4]` |
| `1` | `waiting_count[2]` becomes `0`, add `2` | `[4, 2]` |
| `4` | `waiting_count[5]` becomes `0`, add `5` | `[2, 5]` |
| `2` | `waiting_count[3]` becomes `0`, add `3` | `[5, 3]` |
| `5` | nothing | `[3]` |
| `3` | nothing | `[]` |

All `6` machines started, so the answer is `True`.


In Example 2, the queue starts with only machine `2`, which then frees machine `4`. Machines `0`, `1` and `3` stay stuck at a count of `1`, so only `2` of `5` machines start and the answer is `False`.


### Complexity

- **Time:** `O(N + R)`. Each machine enters the queue at most once, and each requirement lowers a count at most once.
- **Space:** `O(N + R)` for `dependents`, `waiting_count` and the queue.


### Python Code

```python
from collections import deque


class FactoryStartupPlanner:
    def __init__(self):
        pass

    def canStartAllMachines(self, machineCount, startupRequirements):
        """
        Returns True if every machine can be started.
        Machines start as soon as everything they need is running.
        If some machines never get started, the requirements have a circular wait.
        """
        # dependents[m] = machines that are waiting for machine m
        dependents = [[] for _ in range(machineCount)]

        # waiting_count[m] = how many of m's required machines are not running yet
        waiting_count = [0] * machineCount

        for requirement in startupRequirements:
            # "machine,requiredMachine" means requiredMachine must run before machine
            first, second = requirement.split(",")
            machine, required_machine = int(first), int(second)

            dependents[required_machine].append(machine)
            waiting_count[machine] += 1

        # machines that are not waiting for anything can start right away
        ready_machines = deque()
        for machine in range(machineCount):
            if waiting_count[machine] == 0:
                ready_machines.append(machine)

        started_count = 0
        while ready_machines:
            machine = ready_machines.popleft()
            started_count += 1

            # this machine is running now, so each dependent waits for one machine less
            for dependent in dependents[machine]:
                waiting_count[dependent] -= 1
                if waiting_count[dependent] == 0:
                    ready_machines.append(dependent)

        # if there is a circular wait, some machines never start
        return started_count == machineCount
```


## Key Takeaways

- All machines can start exactly when the requirements have no circular wait.
- Counting what each machine still waits for means we never check the same machine again and again.
- "Start what is free, then unlock the next ones" is topological sort. The same idea solves many scheduling problems.