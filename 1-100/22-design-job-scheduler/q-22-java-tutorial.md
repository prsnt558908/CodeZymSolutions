# Design a Job Scheduler System in Java

#### Problem Statement
[https://codezym.com/question/22-design-job-scheduler](https://codezym.com/question/22-design-job-scheduler)


The core idea is simple. To assign a job, first **filter** the machines that have every required capability, then **select** one of them using the given criteria. The selection rule must be easy to extend, so we use the **Strategy pattern**: each criteria becomes its own small class, and the scheduler just picks the right one by its number.


Strategy alone is the best fit here. Only one part of the problem varies (how to pick a machine), and that is exactly what Strategy is built for. Adding more patterns would only add classes, not clarity.


The rest is simple bookkeeping. In the final design, a `Machine` class keeps its capabilities and job counters together. Machines are kept sorted by `machineId`, which settles ties for free. A map from `jobId` to machine lets `jobCompleted` update the right counters.


We first build a simple one-class solution, see where it falls short, and then improve it with Strategy.


## Key Rules

- Capabilities are case insensitive and may have extra spaces around them, like `" Speech To Text Conversion "`. So we trim them and convert them to lowercase before storing or comparing.
- A machine can run a job only if it has **all** the required capabilities. Having extra ones is fine.
- If several machines are equally good, return the lexicographically smallest `machineId`. This is plain string comparison, so `"m-10"` comes before `"m-2"` because `'1'` is smaller than `'2'`.
- If no machine can run the job, return `""` and change nothing.


## Solution 1: Simple Approach (Everything in One Class)

### Idea

Keep three maps keyed by `machineId`: its capabilities, its unfinished job count and its finished job count. A fourth map remembers which machine runs each job.


The helper `cleanCapabilities()` trims and lowercases every capability. We use it both when a machine is added and when a job lists what it needs, so the two always match.


To assign a job, loop over all machines, skip the ones missing a required capability, and keep the best one seen so far. The helper `isBetter()` has one `if` branch per criteria. When two machines are equal on the criteria, it compares their ids.


`jobCompleted` looks up the job's machine, moves one job from unfinished to finished, and removes the job from the map.

### Java Code

```java
import java.util.*;

public class JobScheduler {

    // machineId -> its capabilities (trimmed, lowercase)
    Map<String, Set<String>> machineCapabilities = new HashMap<>();

    // machineId -> number of unfinished jobs
    Map<String, Integer> unfinishedJobs = new HashMap<>();

    // machineId -> number of finished jobs
    Map<String, Integer> finishedJobs = new HashMap<>();

    // jobId -> machineId running that job
    Map<String, String> jobToMachine = new HashMap<>();

    public JobScheduler() {
    }

    public void addMachine(String machineId, String[] capabilities) {
        machineCapabilities.put(machineId, cleanCapabilities(capabilities));
        unfinishedJobs.put(machineId, 0);
        finishedJobs.put(machineId, 0);
    }

    public String assignMachineToJob(String jobId, String[] capabilitiesRequired, int criteria) {
        // unknown criteria
        if (criteria != 0 && criteria != 1) return "";

        Set<String> required = cleanCapabilities(capabilitiesRequired);
        String best = ""; // no machine picked yet
        for (String machineId : machineCapabilities.keySet()) {
            // skip machines that miss any required capability
            if (!machineCapabilities.get(machineId).containsAll(required)) continue;
            if (best.isEmpty() || isBetter(machineId, best, criteria)) best = machineId;
        }
        if (best.isEmpty()) return "";

        unfinishedJobs.put(best, unfinishedJobs.get(best) + 1);
        jobToMachine.put(jobId, best);
        return best;
    }

    public void jobCompleted(String jobId) {
        // a finished job is never needed again, so remove it
        String machineId = jobToMachine.remove(jobId);
        if (machineId == null) return;
        unfinishedJobs.put(machineId, unfinishedJobs.get(machineId) - 1);
        finishedJobs.put(machineId, finishedJobs.get(machineId) + 1);
    }

    /** Returns true if machine 'a' should be picked over machine 'b'. */
    boolean isBetter(String a, String b, int criteria) {
        if (criteria == 0) {
            // fewer unfinished jobs wins
            int diff = unfinishedJobs.get(a) - unfinishedJobs.get(b);
            if (diff != 0) return diff < 0;
        } else if (criteria == 1) {
            // more finished jobs wins
            int diff = finishedJobs.get(a) - finishedJobs.get(b);
            if (diff != 0) return diff > 0;
        }
        // tie: lexicographically smaller machineId wins
        return a.compareTo(b) < 0;
    }

    /**
     * Capabilities are case insensitive and may have extra spaces around them,
     * so we always trim them and keep them in lowercase.
     */
    Set<String> cleanCapabilities(String[] capabilities) {
        Set<String> result = new HashSet<>();
        for (String capability : capabilities) result.add(capability.trim().toLowerCase());
        return result;
    }
}
```


## Solution 2: Clean Design Using Strategy Pattern

### What Is Wrong With Solution 1?

It gives correct answers, but it is hard to grow.

- **Hard to extend:** a new criteria means another `else if` inside `isBetter()` and another edit to the criteria check at the top of `assignMachineToJob`. The core of the scheduler keeps changing, and every change can break rules that already work. This goes against the extensibility requirement.
- **Scattered data:** one machine's data is spread over three maps. It is easy to update one map and forget another.


Both problems are about design, not speed. With at most 100 machines, scanning all of them on each call is already fast.

### Why Strategy Pattern?

Strategy puts each algorithm in its own class behind a common interface. Here every criteria is one algorithm: "from these candidate machines, pick one".

- Adding a criteria means writing one new class and registering it. The scheduler itself does not change.
- A strategy gets the whole candidate list, so even rules that are not a simple comparison, like round robin, fit the same shape.
- Each criteria is small and can be read and tested on its own.

### Patterns That Look Useful But Are Not Needed

- **Factory:** Turning a criteria number into a strategy object sounds like a factory's job. But each strategy is created only once, in the constructor, and reused for every call. A `Map<Integer, MachineSelectionStrategy>` does that lookup in one line, so a separate factory class would only add code.
- **State:** A job goes from running to finished, so the State pattern can look tempting. But there are only two states, and finishing a job just updates two counters. A map from `jobId` to its machine is enough.

### Classes

| Class | Why we need it |
|---|---|
| `JobScheduler` | Filters machines, asks the right strategy to pick one, and updates counters. |
| `Machine` | Keeps a machine's id, capabilities and counters in one place. Counters change only through `assignJob()` and `completeJob()`, so they always stay correct. |
| `MachineSelectionStrategy` | The common interface for every criteria: take the candidates, return one machine. |
| `LeastUnfinishedJobsStrategy` | Criteria `0`: least number of unfinished jobs. |
| `MostFinishedJobsStrategy` | Criteria `1`: most number of finished jobs. |

### Data Structures

- **`List<Machine> machines`, kept sorted by `machineId`:** candidates come out of the filter in the same sorted order. Each strategy moves to a new machine only when it is *strictly* better, so on a tie the earlier machine (smaller id) stays. No extra tie-break code is needed.
- **`Set<String> capabilities` inside `Machine`:** checking if a machine has a capability takes O(1).
- **`Map<String, Machine> jobToMachine`:** lets `jobCompleted` find the machine in O(1). A job is removed once it is completed, since it is never needed again.
- **`Map<Integer, MachineSelectionStrategy> strategies`:** links each criteria number to its algorithm.

### How `assignMachineToJob` Works

1. Look up the strategy for `criteria`. If there is none, return `""`.
2. Clean the required capabilities: trim the spaces and convert to lowercase.
3. Filter: collect the machines that have all of them. They stay sorted by id.
4. If no machine qualifies, return `""`.
5. Let the strategy pick one machine, increase its unfinished count, save `jobId → machine` and return the machine's id.


**Quick trace of Examples 1 and 2:** `machines` is sorted as `[m-10, m-2]`. For `job-A` (criteria 0), both qualify and both have 0 unfinished jobs, so the strategy keeps the first one: `m-10`. After `jobCompleted("job-A")`, `m-10` has 1 finished job, so criteria 1 picks `m-10` for `job-B` too.

### Adding a New Criteria

1. Create a class that implements `MachineSelectionStrategy`.
2. Register it in the constructor, for example `strategies.put(2, new FewestTotalJobsStrategy())`.


Nothing else changes.

### Complexity

Let `M` be the number of machines, `R` the number of required capabilities and `C` the capabilities of one machine.

- `addMachine`: O(C + M log M) because of the sort, which is tiny since `M ≤ 100`.
- `assignMachineToJob`: O(M × R) to filter, plus O(M) for the strategy.
- `jobCompleted`: O(1).

### Java Code

```java
import java.util.*;

/**
 * Finds the machines that can run a job, lets the chosen strategy
 * pick one of them, and keeps every machine's job counters up to date.
 */
public class JobScheduler {

    // all machines, always kept sorted by machineId
    List<Machine> machines = new ArrayList<>();

    // jobId -> machine running that job (only unfinished jobs are kept)
    Map<String, Machine> jobToMachine = new HashMap<>();

    // criteria number -> its machine selection strategy
    Map<Integer, MachineSelectionStrategy> strategies = new HashMap<>();

    public JobScheduler() {
        // to add a new criteria, register its strategy here
        strategies.put(0, new LeastUnfinishedJobsStrategy());
        strategies.put(1, new MostFinishedJobsStrategy());
    }

    public void addMachine(String machineId, String[] capabilities) {
        machines.add(new Machine(machineId, cleanCapabilities(capabilities)));
        // keep machines sorted by id, so among equally good machines the first one has the smallest id
        machines.sort((a, b) -> a.id.compareTo(b.id));
    }

    public String assignMachineToJob(String jobId, String[] capabilitiesRequired, int criteria) {
        MachineSelectionStrategy strategy = strategies.get(criteria);
        if (strategy == null) return "";

        // 1. Filter: machines having every required capability (still sorted by id)
        Set<String> required = cleanCapabilities(capabilitiesRequired);
        List<Machine> candidates = new ArrayList<>();
        for (Machine machine : machines) {
            if (machine.hasAllCapabilities(required)) candidates.add(machine);
        }
        if (candidates.isEmpty()) return "";

        // 2. Select: the strategy picks one candidate
        Machine selected = strategy.selectMachine(candidates);

        // 3. Assign: update the counter and remember where the job runs
        selected.assignJob();
        jobToMachine.put(jobId, selected);
        return selected.id;
    }

    public void jobCompleted(String jobId) {
        // a finished job is never needed again, so remove it
        Machine machine = jobToMachine.remove(jobId);
        if (machine != null) machine.completeJob();
    }

    /**
     * Capabilities are case insensitive and may have extra spaces around them,
     * so we always trim them and keep them in lowercase.
     */
    Set<String> cleanCapabilities(String[] capabilities) {
        Set<String> result = new HashSet<>();
        for (String capability : capabilities) result.add(capability.trim().toLowerCase());
        return result;
    }
}

/**
 * A machine with its capabilities and job counters.
 * Counters change only through assignJob() and completeJob(), so they always stay correct.
 */
class Machine {
    String id;
    Set<String> capabilities; // trimmed, lowercase
    int unfinishedJobs = 0;
    int finishedJobs = 0;

    Machine(String id, Set<String> capabilities) {
        this.id = id;
        this.capabilities = capabilities;
    }

    boolean hasAllCapabilities(Set<String> required) {
        return capabilities.containsAll(required);
    }

    void assignJob() {
        unfinishedJobs++;
    }

    void completeJob() {
        unfinishedJobs--;
        finishedJobs++;
    }
}

/**
 * Strategy: one implementation per assignment criteria.
 */
interface MachineSelectionStrategy {

    /**
     * Picks one machine from a non-empty list of candidates.
     * Candidates are sorted by machineId, so on a tie keep the earlier one.
     */
    Machine selectMachine(List<Machine> candidates);
}

/** Criteria 0: machine with the least number of unfinished jobs. */
class LeastUnfinishedJobsStrategy implements MachineSelectionStrategy {

    public Machine selectMachine(List<Machine> candidates) {
        Machine best = candidates.get(0);
        for (Machine machine : candidates) {
            // strictly fewer, so on a tie the earlier (smaller id) machine stays
            if (machine.unfinishedJobs < best.unfinishedJobs) best = machine;
        }
        return best;
    }
}

/** Criteria 1: machine with the most number of finished jobs. */
class MostFinishedJobsStrategy implements MachineSelectionStrategy {

    public Machine selectMachine(List<Machine> candidates) {
        Machine best = candidates.get(0);
        for (Machine machine : candidates) {
            // strictly more, so on a tie the earlier (smaller id) machine stays
            if (machine.finishedJobs > best.finishedJobs) best = machine;
        }
        return best;
    }
}
```