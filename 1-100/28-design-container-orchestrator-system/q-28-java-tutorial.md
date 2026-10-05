# Design a Container Orchestrator System in Java

#### Problem Statement
[https://codezym.com/question/28-design-container-orchestrator-system](https://codezym.com/question/28-design-container-orchestrator-system)

## Core Idea

Think of every machine as a box with two counters: **free CPU** and **free memory**. Running a container means finding the best box that still has room for it, then subtracting the container's CPU and memory from that box. Stopping a container simply adds them back.

The only thing that changes between calls is how we judge the "best" box: the most free CPU when `criteria = 0`, or the most free memory when `criteria = 1`. This is exactly what the **Strategy pattern** is for. Each rule becomes its own tiny class, and `ContainerManager` picks the right one using `criteria`.

Strategy alone is the best fit here. Mixing in more patterns would only add classes without making the code easier to follow.

We will first build a simple version that checks every machine on each call. Then we will make the search much faster by keeping the machines in sorted sets.

## Rules in Short

- A machine can host a container only if its free CPU ≥ `cpuUnits` **and** its free memory ≥ `memMb`.
- Among the machines that can host it:
  - `criteria = 0`: pick the one with the most free CPU.
  - `criteria = 1`: pick the one with the most free memory.
  - On a tie, pick the lexicographically smallest `machineId`.
- If no machine can host it, return `""` and save nothing. This is why `c5` can be retried with the same name in the example.
- `stop(name)` gives the container's CPU and memory back to its machine. It returns `true` only if the container exists and is not already `STOPPED`.

## Choosing the Design Pattern

### Strategy Pattern (used)

The rule for picking a machine is the part that varies. Without a pattern, `assignMachine` would grow an `if-else` like this:

```java
if (criteria == 0) {
    // compare free CPU of machines
} else if (criteria == 1) {
    // compare free memory of machines
}
```

This works for two rules, but each new rule means editing this method again.

With Strategy, each rule is a separate class behind one small interface. Adding a new rule means adding one class and one line in the constructor. The search code never changes.

Our strategy answers just one question: **"which free resource of a machine should be as large as possible?"**

### Patterns we did not use

- **State pattern** for the container status: the API has only one move, `RUNNING` to `STOPPED`. One enum field handles it. State pattern pays off when many states each react differently to many actions (start, pause, resume, stop).
- **Factory pattern** for creating strategies: we only need to turn `criteria` into a strategy object. A list where the index is the `criteria` value already does that. A factory class would just wrap the same two lines.

## Solution 1: Check Every Machine

### Main classes

- **`Machine`**: holds the machine id and its **free** CPU and memory. We never need the total capacity again, only what is left. Its methods `canFit`, `reserve` and `release` keep all the resource math in one place.
- **`Container`**: remembers its size, the `Machine` it runs on and its status. `stop` needs exactly this to know what to give back, and to which machine.
- **`MachineSelectionStrategy`**: the strategy interface with one method, `freeAmount(machine)`. `MostFreeCpuStrategy` returns free CPU and `MostFreeMemoryStrategy` returns free memory.
- **`ContainerManager`**: keeps the list of machines, the list of strategies and a map from container name to `Container`. The map lets `stop` find a container in O(1).

### How `assignMachine` works

1. Pick the strategy using `criteria`.
2. Go over every machine and skip the ones that cannot fit the container.
3. Remember the best machine so far. A machine is better if the strategy gives it a bigger free amount, or the same amount with a smaller id.
4. If nothing fits, return `""`. Otherwise reserve the resources on the chosen machine, save the container and return the machine id.

### How `stop` works

Look up the container by name. If it does not exist or is already stopped, return `false`.

Otherwise give its CPU and memory back to its machine, mark it `STOPPED` and return `true`.

### Complexity

For `M` machines:

- `assignMachine`: O(M)
- `stop`: O(1)

### Code

```java
import java.util.*;

/**
 * Strategy: tells which free resource of a machine should be as large as possible.
 * Each criteria value gets its own small strategy class.
 */
interface MachineSelectionStrategy {
    int freeAmount(Machine machine);
}

/** criteria = 0 : prefer the machine with the most free CPU units */
class MostFreeCpuStrategy implements MachineSelectionStrategy {
    public int freeAmount(Machine machine) {
        return machine.freeCpu;
    }
}

/** criteria = 1 : prefer the machine with the most free memory */
class MostFreeMemoryStrategy implements MachineSelectionStrategy {
    public int freeAmount(Machine machine) {
        return machine.freeMemory;
    }
}

/** A cloud machine. We only track what is still free on it. */
class Machine {
    String id;
    int freeCpu;
    int freeMemory;

    Machine(String id, int totalCpu, int totalMemory) {
        this.id = id;
        this.freeCpu = totalCpu;
        this.freeMemory = totalMemory;
    }

    boolean canFit(int cpuUnits, int memMb) {
        return freeCpu >= cpuUnits && freeMemory >= memMb;
    }

    void reserve(int cpuUnits, int memMb) {
        freeCpu -= cpuUnits;
        freeMemory -= memMb;
    }

    void release(int cpuUnits, int memMb) {
        freeCpu += cpuUnits;
        freeMemory += memMb;
    }
}

enum ContainerStatus {
    RUNNING, STOPPED
}

/** A container remembers its machine and size, so stop() knows what to give back. */
class Container {
    String name;
    String imageUrl;
    int cpuUnits;
    int memoryMb;
    Machine machine;
    ContainerStatus status;

    Container(String name, String imageUrl, int cpuUnits, int memoryMb, Machine machine) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.cpuUnits = cpuUnits;
        this.memoryMb = memoryMb;
        this.machine = machine;
        this.status = ContainerStatus.RUNNING;
    }
}

public class ContainerManager {
    List<Machine> machines = new ArrayList<>();
    // container name -> container, for a quick lookup in stop()
    Map<String, Container> containers = new HashMap<>();
    // index in this list is the criteria value
    List<MachineSelectionStrategy> strategies = new ArrayList<>();

    public ContainerManager(List<String> machineRows) {
        strategies.add(new MostFreeCpuStrategy());    // criteria 0
        strategies.add(new MostFreeMemoryStrategy()); // criteria 1

        // each row looks like "machineId,totalCpuUnits,totalMemoryInMB"
        for (String row : machineRows) {
            String[] parts = row.split(",");
            machines.add(new Machine(parts[0].trim(),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())));
        }
    }

    public String assignMachine(int criteria, String containerName, String imageUrl, int cpuUnits, int memMb) {
        if (criteria < 0 || criteria >= strategies.size()) return "";
        MachineSelectionStrategy strategy = strategies.get(criteria);

        Machine best = null;
        for (Machine machine : machines) {
            if (!machine.canFit(cpuUnits, memMb)) continue;
            if (best == null || isBetter(machine, best, strategy)) best = machine;
        }
        if (best == null) return ""; // a failed request stores nothing

        best.reserve(cpuUnits, memMb);
        containers.put(containerName, new Container(containerName, imageUrl, cpuUnits, memMb, best));
        return best.id;
    }

    /** More free amount wins. On a tie, the lexicographically smaller id wins. */
    boolean isBetter(Machine candidate, Machine best, MachineSelectionStrategy strategy) {
        int candidateFree = strategy.freeAmount(candidate);
        int bestFree = strategy.freeAmount(best);
        if (candidateFree != bestFree) return candidateFree > bestFree;
        return candidate.id.compareTo(best.id) < 0;
    }

    public boolean stop(String name) {
        Container container = containers.get(name);
        if (container == null || container.status == ContainerStatus.STOPPED) return false;

        container.machine.release(container.cpuUnits, container.memoryMb);
        container.status = ContainerStatus.STOPPED;
        return true;
    }
}
```

## What Can Be Improved

Every `assignMachine` call checks all `M` machines. Even when the machine with the most free CPU can easily host the container, we still look at every other machine just to be sure.

With thousands of machines and many requests, most of this work is wasted.

## Solution 2: Sorted Sets With Early Stop

### Idea

Keep the machines already ordered from best to worst, one sorted set per criteria:

- For `criteria = 0`: more free CPU first, then smaller id first.
- For `criteria = 1`: more free memory first, then smaller id first.

Now walk the matching set from the top. The **first machine that can fit** the container is the answer, because every machine above it ranks higher but cannot fit.

We can also **stop early**. For `criteria = 0`, once we reach a machine whose free CPU is less than `cpuUnits`, every machine below it has the same or less free CPU. None of them can fit, so we return `""` right away. The same is true for memory when `criteria = 1`.

### One more method in the strategy

To stop early, the strategy must also tell how much of its resource the container needs. So we add `neededAmount(cpuUnits, memMb)`. The CPU strategy returns `cpuUnits` and the memory strategy returns `memMb`.

Everything else in the design stays the same. We only drop the plain list of machines and the `isBetter` method, because the sorted sets already hold every machine in the right order.

### Why a sorted set (`TreeSet`)

A sorted set keeps the machines in order at all times. Adding or removing a machine costs O(log M), and walking it from the top is cheap. So we never have to sort all machines again after a change.

We only use three simple operations on it: `add`, `remove` and a normal `for` loop from the top.

### Keeping the sorted sets correct

A `TreeSet` places a machine based on its free values at the moment it is added. If those values change while the machine is inside, the set searches in the wrong place later, and `remove` can silently fail.

So whenever a machine's free resources change (in `assignMachine` and in `stop`) we:

1. remove it from every sorted set,
2. update its free CPU and memory,
3. add it back to every sorted set.

The tie-break on the unique `machineId` also makes sure two different machines never look "equal" to the set, so no machine gets dropped.

### Example of the early stop

After `c1`, `c2`, `c3` and `c4` from the example, free memory is mC = 23000, mA = 13000 and mB = 7000.

Now `assignMachine(1, "c5", "img://e", 2, 25000)` is called:

- The memory set starts with `mC`, which has 23000 MB free.
- 23000 < 25000, and every machine below `mC` has even less free memory.
- So we return `""` after looking at just one machine.

### Complexity

For `M` machines:

- `assignMachine`: updating the sorted sets costs O(log M). The scan usually stops within the first few machines, because a machine near the top normally fits. In the worst case, many top machines have plenty of one resource but too little of the other, and the scan can still visit up to `M` machines.
- `stop`: O(log M)

### Code

```java
import java.util.*;

/**
 * Strategy: tells which resource decides the "best" machine.
 * Each criteria value gets its own small strategy class.
 */
interface MachineSelectionStrategy {
    // free amount of the deciding resource on a machine
    int freeAmount(Machine machine);

    // amount of the same resource that the new container needs
    int neededAmount(int cpuUnits, int memMb);
}

/** criteria = 0 : prefer the machine with the most free CPU units */
class MostFreeCpuStrategy implements MachineSelectionStrategy {
    public int freeAmount(Machine machine) {
        return machine.freeCpu;
    }

    public int neededAmount(int cpuUnits, int memMb) {
        return cpuUnits;
    }
}

/** criteria = 1 : prefer the machine with the most free memory */
class MostFreeMemoryStrategy implements MachineSelectionStrategy {
    public int freeAmount(Machine machine) {
        return machine.freeMemory;
    }

    public int neededAmount(int cpuUnits, int memMb) {
        return memMb;
    }
}

/** A cloud machine. We only track what is still free on it. */
class Machine {
    String id;
    int freeCpu;
    int freeMemory;

    Machine(String id, int totalCpu, int totalMemory) {
        this.id = id;
        this.freeCpu = totalCpu;
        this.freeMemory = totalMemory;
    }

    boolean canFit(int cpuUnits, int memMb) {
        return freeCpu >= cpuUnits && freeMemory >= memMb;
    }

    void reserve(int cpuUnits, int memMb) {
        freeCpu -= cpuUnits;
        freeMemory -= memMb;
    }

    void release(int cpuUnits, int memMb) {
        freeCpu += cpuUnits;
        freeMemory += memMb;
    }
}

enum ContainerStatus {
    RUNNING, STOPPED
}

/** A container remembers its machine and size, so stop() knows what to give back. */
class Container {
    String name;
    String imageUrl;
    int cpuUnits;
    int memoryMb;
    Machine machine;
    ContainerStatus status;

    Container(String name, String imageUrl, int cpuUnits, int memoryMb, Machine machine) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.cpuUnits = cpuUnits;
        this.memoryMb = memoryMb;
        this.machine = machine;
        this.status = ContainerStatus.RUNNING;
    }
}

public class ContainerManager {
    // index in this list is the criteria value
    List<MachineSelectionStrategy> strategies = new ArrayList<>();
    // sortedMachines.get(i) holds every machine, best first, for strategies.get(i)
    List<TreeSet<Machine>> sortedMachines = new ArrayList<>();
    // container name -> container, for a quick lookup in stop()
    Map<String, Container> containers = new HashMap<>();

    public ContainerManager(List<String> machineRows) {
        strategies.add(new MostFreeCpuStrategy());    // criteria 0
        strategies.add(new MostFreeMemoryStrategy()); // criteria 1

        for (MachineSelectionStrategy strategy : strategies) {
            // best first: more free amount first, then smaller machine id first
            sortedMachines.add(new TreeSet<>((a, b) -> {
                int freeA = strategy.freeAmount(a);
                int freeB = strategy.freeAmount(b);
                if (freeA != freeB) return Integer.compare(freeB, freeA);
                return a.id.compareTo(b.id);
            }));
        }

        // each row looks like "machineId,totalCpuUnits,totalMemoryInMB"
        for (String row : machineRows) {
            String[] parts = row.split(",");
            Machine machine = new Machine(parts[0].trim(),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
            addToSortedSets(machine);
        }
    }

    public String assignMachine(int criteria, String containerName, String imageUrl, int cpuUnits, int memMb) {
        if (criteria < 0 || criteria >= strategies.size()) return "";
        MachineSelectionStrategy strategy = strategies.get(criteria);
        int needed = strategy.neededAmount(cpuUnits, memMb);

        Machine chosen = null;
        for (Machine machine : sortedMachines.get(criteria)) {
            // machines after this one have the same or less of this resource, so none can fit
            if (strategy.freeAmount(machine) < needed) break;
            if (machine.canFit(cpuUnits, memMb)) {
                chosen = machine; // the first machine that fits is the best one
                break;
            }
        }
        if (chosen == null) return ""; // a failed request stores nothing

        removeFromSortedSets(chosen);
        chosen.reserve(cpuUnits, memMb);
        addToSortedSets(chosen);

        containers.put(containerName, new Container(containerName, imageUrl, cpuUnits, memMb, chosen));
        return chosen.id;
    }

    public boolean stop(String name) {
        Container container = containers.get(name);
        if (container == null || container.status == ContainerStatus.STOPPED) return false;

        Machine machine = container.machine;
        removeFromSortedSets(machine);
        machine.release(container.cpuUnits, container.memoryMb);
        addToSortedSets(machine);

        container.status = ContainerStatus.STOPPED;
        return true;
    }

    /*
     * A TreeSet finds a machine using its free values. So a machine must be
     * removed before those values change and added back after the change.
     */
    void removeFromSortedSets(Machine machine) {
        for (TreeSet<Machine> sortedSet : sortedMachines) sortedSet.remove(machine);
    }

    void addToSortedSets(Machine machine) {
        for (TreeSet<Machine> sortedSet : sortedMachines) sortedSet.add(machine);
    }
}
```