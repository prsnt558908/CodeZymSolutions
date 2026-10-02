# Design a Job Scheduler System in Python

#### Problem Statement
[https://codezym.com/question/22-design-job-scheduler](https://codezym.com/question/22-design-job-scheduler)


The core idea is simple. To assign a job, first **filter** the machines that have every required capability, then **select** one of them using the given criteria. The selection rule must be easy to extend, so we use the **Strategy pattern**: each criteria becomes its own small class, and the scheduler just picks the right one by its number.


Strategy alone is the best fit here. Only one part of the problem varies (how to pick a machine), and that is exactly what Strategy is built for. Adding more patterns would only add classes, not clarity.


The rest is simple bookkeeping. In the final design, a `Machine` class keeps its capabilities and job counters together. Machines are kept sorted by `machineId`, which settles ties for free. A dictionary from `jobId` to machine lets `jobCompleted` update the right counters.


We first build a simple one-class solution, see where it falls short, and then improve it with Strategy.


## Key Rules

- Capabilities are case insensitive and may have extra spaces around them, like `" Speech To Text Conversion "`. So we trim them and convert them to lowercase before storing or comparing.
- A machine can run a job only if it has **all** the required capabilities. Having extra ones is fine.
- If several machines are equally good, return the lexicographically smallest `machineId`. This is plain string comparison, so `"m-10"` comes before `"m-2"` because `'1'` is smaller than `'2'`.
- If no machine can run the job, return `""` and change nothing.


## Solution 1: Simple Approach (Everything in One Class)

### Idea

Keep three dictionaries keyed by `machineId`: its capabilities (a `set`), its unfinished job count and its finished job count. A fourth dictionary remembers which machine runs each job.


The helper `clean_capabilities()` trims and lowercases every capability. We use it both when a machine is added and when a job lists what it needs, so the two always match.


To assign a job, loop over all machines, skip the ones missing a required capability, and keep the best one seen so far. The helper `is_better()` has one `if` branch per criteria. When two machines are equal on the criteria, it compares their ids.


`jobCompleted` looks up the job's machine, moves one job from unfinished to finished, and removes the job from the dictionary.


The three public method names come from the problem, so they stay in camelCase. Our own helpers use normal Python snake_case.

### Python Code

```python
class JobScheduler:
    def __init__(self):
        self.machine_capabilities = {}  # machineId -> set of cleaned capabilities
        self.unfinished_jobs = {}  # machineId -> number of unfinished jobs
        self.finished_jobs = {}  # machineId -> number of finished jobs
        self.job_to_machine = {}  # jobId -> machineId running that job

    def addMachine(self, machineId, capabilities):
        self.machine_capabilities[machineId] = self.clean_capabilities(capabilities)
        self.unfinished_jobs[machineId] = 0
        self.finished_jobs[machineId] = 0

    def assignMachineToJob(self, jobId, capabilitiesRequired, criteria):
        # unknown criteria
        if criteria not in (0, 1):
            return ""

        required = self.clean_capabilities(capabilitiesRequired)
        best = ""  # no machine picked yet
        for machine_id, capabilities in self.machine_capabilities.items():
            # skip machines that miss any required capability
            if not required.issubset(capabilities):
                continue
            if best == "" or self.is_better(machine_id, best, criteria):
                best = machine_id
        if best == "":
            return ""

        self.unfinished_jobs[best] += 1
        self.job_to_machine[jobId] = best
        return best

    def jobCompleted(self, jobId):
        # a finished job is never needed again, so remove it
        machine_id = self.job_to_machine.pop(jobId, None)
        if machine_id is None:
            return
        self.unfinished_jobs[machine_id] -= 1
        self.finished_jobs[machine_id] += 1

    def is_better(self, a, b, criteria):
        """Returns True if machine a should be picked over machine b."""
        if criteria == 0:
            # fewer unfinished jobs wins
            if self.unfinished_jobs[a] != self.unfinished_jobs[b]:
                return self.unfinished_jobs[a] < self.unfinished_jobs[b]
        elif criteria == 1:
            # more finished jobs wins
            if self.finished_jobs[a] != self.finished_jobs[b]:
                return self.finished_jobs[a] > self.finished_jobs[b]
        # tie: lexicographically smaller machineId wins
        return a < b

    def clean_capabilities(self, capabilities):
        """
        Capabilities are case insensitive and may have extra spaces around them,
        so we always trim them and keep them in lowercase.
        """
        return {capability.strip().lower() for capability in capabilities}
```


## Solution 2: Clean Design Using Strategy Pattern

### What Is Wrong With Solution 1?

It gives correct answers, but it is hard to grow.

- **Hard to extend:** a new criteria means another `elif` inside `is_better()` and another edit to the criteria check at the top of `assignMachineToJob`. The core of the scheduler keeps changing, and every change can break rules that already work. This goes against the extensibility requirement.
- **Scattered data:** one machine's data is spread over three dictionaries. It is easy to update one and forget another.


Both problems are about design, not speed. With at most 100 machines, scanning all of them on each call is already fast.

### Why Strategy Pattern?

Strategy puts each algorithm in its own class behind a common interface. Here every criteria is one algorithm: "from these candidate machines, pick one".

- Adding a criteria means writing one new class and registering it. The scheduler itself does not change.
- A strategy gets the whole candidate list, so even rules that are not a simple comparison, like round robin, fit the same shape.
- Each criteria is small and can be read and tested on its own.

### Patterns That Look Useful But Are Not Needed

- **Factory:** Turning a criteria number into a strategy object sounds like a factory's job. But each strategy is created only once, in `__init__`, and reused for every call. A dictionary from criteria number to strategy does that lookup in one line, so a separate factory class would only add code.
- **State:** A job goes from running to finished, so the State pattern can look tempting. But there are only two states, and finishing a job just updates two counters. A dictionary from `jobId` to its machine is enough.

### Classes

| Class | Why we need it |
|---|---|
| `JobScheduler` | Filters machines, asks the right strategy to pick one, and updates counters. |
| `Machine` | Keeps a machine's id, capabilities and counters in one place. Counters change only through `assign_job()` and `complete_job()`, so they always stay correct. |
| `MachineSelectionStrategy` | The common interface for every criteria: take the candidates, return one machine. It is an abstract base class (`ABC`), Python's way to declare an interface. |
| `LeastUnfinishedJobsStrategy` | Criteria `0`: least number of unfinished jobs. |
| `MostFinishedJobsStrategy` | Criteria `1`: most number of finished jobs. |

### Data Structures

- **`self.machines`, a list kept sorted by machine id:** candidates come out of the filter in the same sorted order. Each strategy moves to a new machine only when it is *strictly* better, so on a tie the earlier machine (smaller id) stays. No extra tie-break code is needed.
- **`capabilities`, a `set` inside `Machine`:** checking if a machine has a capability takes O(1).
- **`self.job_to_machine`, a dictionary:** lets `jobCompleted` find the machine in O(1). A job is removed once it is completed, since it is never needed again.
- **`self.strategies`, a dictionary:** links each criteria number to its algorithm.

### How `assignMachineToJob` Works

1. Look up the strategy for `criteria`. If there is none, return `""`.
2. Clean the required capabilities: trim the spaces and convert to lowercase.
3. Filter: collect the machines that have all of them. They stay sorted by id.
4. If no machine qualifies, return `""`.
5. Let the strategy pick one machine, increase its unfinished count, save `jobId → machine` and return the machine's id.


**Quick trace of Examples 1 and 2:** `self.machines` is sorted as `[m-10, m-2]`. For `job-A` (criteria 0), both qualify and both have 0 unfinished jobs, so the strategy keeps the first one: `m-10`. After `jobCompleted("job-A")`, `m-10` has 1 finished job, so criteria 1 picks `m-10` for `job-B` too.

### Adding a New Criteria

1. Create a class that extends `MachineSelectionStrategy` and implements `select_machine()`.
2. Register it in `__init__`, for example `2: FewestTotalJobsStrategy()` inside `self.strategies`.


Nothing else changes.

### Complexity

Let `M` be the number of machines, `R` the number of required capabilities and `C` the capabilities of one machine.

- `addMachine`: O(C + M log M) because of the sort, which is tiny since `M ≤ 100`.
- `assignMachineToJob`: O(M × R) to filter, plus O(M) for the strategy.
- `jobCompleted`: O(1).

### Python Code

```python
from abc import ABC, abstractmethod


class Machine:
    """
    A machine with its capabilities and job counters.
    Counters change only through assign_job() and complete_job(),
    so they always stay correct.
    """

    def __init__(self, machine_id, capabilities):
        self.id = machine_id
        self.capabilities = capabilities  # set of cleaned capabilities
        self.unfinished_jobs = 0
        self.finished_jobs = 0

    def has_all_capabilities(self, required):
        return required.issubset(self.capabilities)

    def assign_job(self):
        self.unfinished_jobs += 1

    def complete_job(self):
        self.unfinished_jobs -= 1
        self.finished_jobs += 1


class MachineSelectionStrategy(ABC):
    """Strategy: one implementation per assignment criteria."""

    @abstractmethod
    def select_machine(self, candidates):
        """
        Picks one machine from a non-empty list of candidates.
        Candidates are sorted by machine id, so on a tie keep the earlier one.
        """


class LeastUnfinishedJobsStrategy(MachineSelectionStrategy):
    """Criteria 0: machine with the least number of unfinished jobs."""

    def select_machine(self, candidates):
        best = candidates[0]
        for machine in candidates:
            # strictly fewer, so on a tie the earlier (smaller id) machine stays
            if machine.unfinished_jobs < best.unfinished_jobs:
                best = machine
        return best


class MostFinishedJobsStrategy(MachineSelectionStrategy):
    """Criteria 1: machine with the most number of finished jobs."""

    def select_machine(self, candidates):
        best = candidates[0]
        for machine in candidates:
            # strictly more, so on a tie the earlier (smaller id) machine stays
            if machine.finished_jobs > best.finished_jobs:
                best = machine
        return best


class JobScheduler:
    """
    Finds the machines that can run a job, lets the chosen strategy
    pick one of them, and keeps every machine's job counters up to date.
    """

    def __init__(self):
        # all machines, always kept sorted by machine id
        self.machines = []

        # jobId -> machine running that job (only unfinished jobs are kept)
        self.job_to_machine = {}

        # criteria number -> its machine selection strategy
        # to add a new criteria, register its strategy here
        self.strategies = {
            0: LeastUnfinishedJobsStrategy(),
            1: MostFinishedJobsStrategy(),
        }

    def addMachine(self, machineId, capabilities):
        machine = Machine(machineId, self.clean_capabilities(capabilities))
        self.machines.append(machine)
        # keep machines sorted by id, so among equally good machines
        # the first one has the smallest id
        self.machines.sort(key=lambda m: m.id)

    def assignMachineToJob(self, jobId, capabilitiesRequired, criteria):
        strategy = self.strategies.get(criteria)
        if strategy is None:
            return ""

        # 1. Filter: machines having every required capability (still sorted by id)
        required = self.clean_capabilities(capabilitiesRequired)
        candidates = []
        for machine in self.machines:
            if machine.has_all_capabilities(required):
                candidates.append(machine)
        if not candidates:
            return ""

        # 2. Select: the strategy picks one candidate
        selected = strategy.select_machine(candidates)

        # 3. Assign: update the counter and remember where the job runs
        selected.assign_job()
        self.job_to_machine[jobId] = selected
        return selected.id

    def jobCompleted(self, jobId):
        # a finished job is never needed again, so remove it
        machine = self.job_to_machine.pop(jobId, None)
        if machine is not None:
            machine.complete_job()

    def clean_capabilities(self, capabilities):
        """
        Capabilities are case insensitive and may have extra spaces around them,
        so we always trim them and keep them in lowercase.
        """
        return {capability.strip().lower() for capability in capabilities}
```