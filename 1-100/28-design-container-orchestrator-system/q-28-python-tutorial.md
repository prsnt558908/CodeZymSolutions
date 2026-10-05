# Design a Container Orchestrator System in Python

#### Problem Statement
[https://codezym.com/question/28-design-container-orchestrator-system](https://codezym.com/question/28-design-container-orchestrator-system)

## Core Idea

Think of every machine as a box with two counters: **free CPU** and **free memory**. Running a container means finding the best box that still has room for it, then subtracting the container's CPU and memory from that box. Stopping a container simply adds them back.

The only thing that changes between calls is how we judge the "best" box: the most free CPU when `criteria = 0`, or the most free memory when `criteria = 1`. This is exactly what the **Strategy pattern** is for. Each rule becomes its own tiny class, and `ContainerManager` picks the right one using `criteria`.

Strategy alone is the best fit here. Mixing in more patterns would only add classes without making the code easier to follow.

We will first build a simple version that checks every machine on each call. Then we will make the search much faster by keeping the machines in sorted lists.

## Rules in Short

- A machine can host a container only if its free CPU ≥ `cpuUnits` **and** its free memory ≥ `memMb`.
- Among the machines that can host it:
  - `criteria = 0`: pick the one with the most free CPU.
  - `criteria = 1`: pick the one with the most free memory.
  - On a tie, pick the lexicographically smallest `machineId`.
- If no machine can host it, return `""` and save nothing. This is why `c5` can be retried with the same name in the example.
- `stop(name)` gives the container's CPU and memory back to its machine. It returns `True` only if the container exists and is not already `STOPPED`.

## Choosing the Design Pattern

### Strategy Pattern (used)

The rule for picking a machine is the part that varies. Without a pattern, `assignMachine` would grow an `if-else` like this:

```python
if criteria == 0:
    pass  # compare free CPU of machines
elif criteria == 1:
    pass  # compare free memory of machines
```

This works for two rules, but each new rule means editing this method again.

With Strategy, each rule is a separate class with the same small set of methods. Adding a new rule means adding one class and one entry in the strategies list. The search code never changes.

Our strategy answers just one question: **"which free resource of a machine should be as large as possible?"**

### Patterns we did not use

- **State pattern** for the container status: the API has only one move, `RUNNING` to `STOPPED`. One `Enum` field handles it. State pattern pays off when many states each react differently to many actions (start, pause, resume, stop).
- **Factory pattern** for creating strategies: we only need to turn `criteria` into a strategy object. A list where the index is the `criteria` value already does that. A factory class would just wrap the same one line.

## Solution 1: Check Every Machine

### Main classes

- **`Machine`**: holds the machine id and its **free** CPU and memory. We never need the total capacity again, only what is left. Its methods `can_fit`, `reserve` and `release` keep all the resource math in one place.
- **`Container`**: remembers its size, the `Machine` it runs on and its status. `stop` needs exactly this to know what to give back, and to which machine.
- **`MachineSelectionStrategy`**: the strategy base class with one method, `free_amount(machine)`. `MostFreeCpuStrategy` returns free CPU and `MostFreeMemoryStrategy` returns free memory.
- **`ContainerManager`**: keeps the list of machines, the list of strategies and a dictionary from container name to `Container`. The dictionary lets `stop` find a container in O(1).

### How `assignMachine` works

1. Pick the strategy using `criteria`.
2. Go over every machine and skip the ones that cannot fit the container.
3. Remember the best machine so far. A machine is better if the strategy gives it a bigger free amount, or the same amount with a smaller id.
4. If nothing fits, return `""`. Otherwise reserve the resources on the chosen machine, save the container and return the machine id.

### How `stop` works

Look up the container by name. If it does not exist or is already stopped, return `False`.

Otherwise give its CPU and memory back to its machine, mark it `STOPPED` and return `True`.

### Complexity

For `M` machines:

- `assignMachine`: O(M)
- `stop`: O(1)

### Code

```python
from enum import Enum


class MachineSelectionStrategy:
    """Strategy: tells which free resource of a machine should be as large as possible.
    Each criteria value gets its own small strategy class."""

    def free_amount(self, machine):
        raise NotImplementedError


class MostFreeCpuStrategy(MachineSelectionStrategy):
    """criteria = 0 : prefer the machine with the most free CPU units"""

    def free_amount(self, machine):
        return machine.free_cpu


class MostFreeMemoryStrategy(MachineSelectionStrategy):
    """criteria = 1 : prefer the machine with the most free memory"""

    def free_amount(self, machine):
        return machine.free_memory


class Machine:
    """A cloud machine. We only track what is still free on it."""

    def __init__(self, machine_id, total_cpu, total_memory):
        self.machine_id = machine_id
        self.free_cpu = total_cpu
        self.free_memory = total_memory

    def can_fit(self, cpu_units, mem_mb):
        return self.free_cpu >= cpu_units and self.free_memory >= mem_mb

    def reserve(self, cpu_units, mem_mb):
        self.free_cpu -= cpu_units
        self.free_memory -= mem_mb

    def release(self, cpu_units, mem_mb):
        self.free_cpu += cpu_units
        self.free_memory += mem_mb


class ContainerStatus(Enum):
    RUNNING = 1
    STOPPED = 2


class Container:
    """A container remembers its machine and size, so stop() knows what to give back."""

    def __init__(self, name, image_url, cpu_units, memory_mb, machine):
        self.name = name
        self.image_url = image_url
        self.cpu_units = cpu_units
        self.memory_mb = memory_mb
        self.machine = machine
        self.status = ContainerStatus.RUNNING


class ContainerManager:
    def __init__(self, machines):
        self.machines = []
        self.containers = {}  # container name -> Container, for a quick lookup in stop()
        # index in this list is the criteria value
        self.strategies = [MostFreeCpuStrategy(), MostFreeMemoryStrategy()]

        # each row looks like "machineId,totalCpuUnits,totalMemoryInMB"
        for row in machines:
            parts = [part.strip() for part in row.split(",")]
            self.machines.append(Machine(parts[0], int(parts[1]), int(parts[2])))

    def assignMachine(self, criteria, containerName, imageUrl, cpuUnits, memMb):
        if criteria < 0 or criteria >= len(self.strategies):
            return ""
        strategy = self.strategies[criteria]

        best = None
        for machine in self.machines:
            if not machine.can_fit(cpuUnits, memMb):
                continue
            if best is None or self.is_better(machine, best, strategy):
                best = machine
        if best is None:
            return ""  # a failed request stores nothing

        best.reserve(cpuUnits, memMb)
        self.containers[containerName] = Container(containerName, imageUrl, cpuUnits, memMb, best)
        return best.machine_id

    def is_better(self, candidate, best, strategy):
        """More free amount wins. On a tie, the lexicographically smaller id wins."""
        candidate_free = strategy.free_amount(candidate)
        best_free = strategy.free_amount(best)
        if candidate_free != best_free:
            return candidate_free > best_free
        return candidate.machine_id < best.machine_id

    def stop(self, name):
        container = self.containers.get(name)
        if container is None or container.status == ContainerStatus.STOPPED:
            return False

        container.machine.release(container.cpu_units, container.memory_mb)
        container.status = ContainerStatus.STOPPED
        return True
```

## What Can Be Improved

Every `assignMachine` call checks all `M` machines. Even when the machine with the most free CPU can easily host the container, we still look at every other machine just to be sure.

With thousands of machines and many requests, most of this work is wasted.

## Solution 2: Sorted Lists With Early Stop

### Idea

Keep the machines already ordered from best to worst, one sorted list per criteria:

- For `criteria = 0`: more free CPU first, then smaller id first.
- For `criteria = 1`: more free memory first, then smaller id first.

Now walk the matching list from the start. The **first machine that can fit** the container is the answer, because every machine before it ranks higher but cannot fit.

We can also **stop early**. For `criteria = 0`, once we reach a machine whose free CPU is less than `cpuUnits`, every machine after it has the same or less free CPU. None of them can fit, so we return `""` right away. The same is true for memory when `criteria = 1`.

### One more method in the strategy

To stop early, the strategy must also tell how much of its resource the container needs. So we add `needed_amount(cpu_units, mem_mb)`. The CPU strategy returns `cpu_units` and the memory strategy returns `mem_mb`.

Everything else in the design stays the same, with two small changes. The `is_better` method goes away, because the sorted lists already keep machines in the right order. And the plain list of machines becomes a dictionary from machine id to `Machine`, so we can go from a sorted entry back to its machine.

### Why a sorted list with `bisect`

Python has no built-in sorted set, so we keep a normal list sorted with the standard `bisect` module.

Each entry is a small tuple `(-free amount, machine id)`. Plain tuple comparison then gives exactly the order we want: more free amount first (thanks to the minus sign), then smaller id first.

We only use three simple operations on it: `bisect.insort` to add, `bisect.bisect_left` with `pop` to remove, and a normal `for` loop from the start.

### Keeping the sorted lists correct

An entry stores the machine's free amount at the moment it is added. If we changed the machine first, we would search for its new amount and never find the old entry.

So whenever a machine's free resources change (in `assignMachine` and in `stop`) we:

1. remove its entry from every sorted list,
2. update its free CPU and memory,
3. add a fresh entry to every sorted list.

Machine ids are unique, so two entries are never equal, and `bisect_left` always lands exactly on the machine's own entry.

### Example of the early stop

After `c1`, `c2`, `c3` and `c4` from the example, free memory is mC = 23000, mA = 13000 and mB = 7000.

Now `assignMachine(1, "c5", "img://e", 2, 25000)` is called:

- The memory list starts with `mC`, which has 23000 MB free.
- 23000 < 25000, and every machine after `mC` has even less free memory.
- So we return `""` after looking at just one machine.

### Complexity

For `M` machines:

- `assignMachine`: the scan usually stops within the first few machines, because a machine near the start of the list normally fits. In the worst case, many machines near the start have plenty of one resource but too little of the other, and the scan can still visit up to `M` machines.
- Updating a sorted list (in `assignMachine` and `stop`): `bisect` finds the position in O(log M). Inserting or removing then shifts the items after it, which is O(M) in theory, but it is a fast low level memory copy, far cheaper than a Python loop over every machine.

### Code

```python
import bisect
from enum import Enum


class MachineSelectionStrategy:
    """Strategy: tells which resource decides the "best" machine.
    Each criteria value gets its own small strategy class."""

    def free_amount(self, machine):
        """Free amount of the deciding resource on a machine."""
        raise NotImplementedError

    def needed_amount(self, cpu_units, mem_mb):
        """Amount of the same resource that the new container needs."""
        raise NotImplementedError


class MostFreeCpuStrategy(MachineSelectionStrategy):
    """criteria = 0 : prefer the machine with the most free CPU units"""

    def free_amount(self, machine):
        return machine.free_cpu

    def needed_amount(self, cpu_units, mem_mb):
        return cpu_units


class MostFreeMemoryStrategy(MachineSelectionStrategy):
    """criteria = 1 : prefer the machine with the most free memory"""

    def free_amount(self, machine):
        return machine.free_memory

    def needed_amount(self, cpu_units, mem_mb):
        return mem_mb


class Machine:
    """A cloud machine. We only track what is still free on it."""

    def __init__(self, machine_id, total_cpu, total_memory):
        self.machine_id = machine_id
        self.free_cpu = total_cpu
        self.free_memory = total_memory

    def can_fit(self, cpu_units, mem_mb):
        return self.free_cpu >= cpu_units and self.free_memory >= mem_mb

    def reserve(self, cpu_units, mem_mb):
        self.free_cpu -= cpu_units
        self.free_memory -= mem_mb

    def release(self, cpu_units, mem_mb):
        self.free_cpu += cpu_units
        self.free_memory += mem_mb


class ContainerStatus(Enum):
    RUNNING = 1
    STOPPED = 2


class Container:
    """A container remembers its machine and size, so stop() knows what to give back."""

    def __init__(self, name, image_url, cpu_units, memory_mb, machine):
        self.name = name
        self.image_url = image_url
        self.cpu_units = cpu_units
        self.memory_mb = memory_mb
        self.machine = machine
        self.status = ContainerStatus.RUNNING


class ContainerManager:
    def __init__(self, machines):
        self.machines = {}    # machine id -> Machine
        self.containers = {}  # container name -> Container, for a quick lookup in stop()
        # index in this list is the criteria value
        self.strategies = [MostFreeCpuStrategy(), MostFreeMemoryStrategy()]
        # sorted_machines[i] is kept sorted, best machine first, for strategies[i].
        # Each entry is a tuple (-free amount, machine id).
        self.sorted_machines = [[] for _ in self.strategies]

        # each row looks like "machineId,totalCpuUnits,totalMemoryInMB"
        for row in machines:
            parts = [part.strip() for part in row.split(",")]
            machine = Machine(parts[0], int(parts[1]), int(parts[2]))
            self.machines[machine.machine_id] = machine
            self.add_to_sorted_lists(machine)

    def assignMachine(self, criteria, containerName, imageUrl, cpuUnits, memMb):
        if criteria < 0 or criteria >= len(self.strategies):
            return ""
        strategy = self.strategies[criteria]
        needed = strategy.needed_amount(cpuUnits, memMb)

        chosen = None
        for _, machine_id in self.sorted_machines[criteria]:
            machine = self.machines[machine_id]
            # machines after this one have the same or less of this resource, so none can fit
            if strategy.free_amount(machine) < needed:
                break
            if machine.can_fit(cpuUnits, memMb):
                chosen = machine  # the first machine that fits is the best one
                break
        if chosen is None:
            return ""  # a failed request stores nothing

        self.remove_from_sorted_lists(chosen)
        chosen.reserve(cpuUnits, memMb)
        self.add_to_sorted_lists(chosen)

        self.containers[containerName] = Container(containerName, imageUrl, cpuUnits, memMb, chosen)
        return chosen.machine_id

    def stop(self, name):
        container = self.containers.get(name)
        if container is None or container.status == ContainerStatus.STOPPED:
            return False

        machine = container.machine
        self.remove_from_sorted_lists(machine)
        machine.release(container.cpu_units, container.memory_mb)
        self.add_to_sorted_lists(machine)

        container.status = ContainerStatus.STOPPED
        return True

    def sort_key(self, strategy, machine):
        """More free amount first, then smaller machine id first."""
        return (-strategy.free_amount(machine), machine.machine_id)

    # An entry stores the machine's free amount at the time it was added.
    # So a machine's entries must be removed before its free values change,
    # and new entries added after the change.
    def remove_from_sorted_lists(self, machine):
        for strategy, sorted_list in zip(self.strategies, self.sorted_machines):
            index = bisect.bisect_left(sorted_list, self.sort_key(strategy, machine))
            sorted_list.pop(index)

    def add_to_sorted_lists(self, machine):
        for strategy, sorted_list in zip(self.strategies, self.sorted_machines):
            bisect.insort(sorted_list, self.sort_key(strategy, machine))
```