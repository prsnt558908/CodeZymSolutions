# Design System for Managing Workflows in Python

#### Problem Statement
[https://codezym.com/question/95-design-system-managing-workflows](https://codezym.com/question/95-design-system-managing-workflows)

## The Core Idea

Every piece of this problem takes a list of strings in and gives a list of strings out. A functionality does it, a task does it, a workflow does it, and even a whole chain of workflows does it. So the design is just small pieces that plug into each other.

The best fit here is a combination of two patterns. **Strategy** is used twice: each of the 8 functionalities is a small strategy class, and the two execution modes (sequential and parallel) are strategies too. A **Simple Factory** turns a name like `"sortByLength"` into the right strategy object.

Connected workflows always form a straight line, so we keep them as a simple linked list. Each workflow remembers its next (downstream) and previous (upstream) workflow, and running a chain is just a loop.

| Level | Example | Works on |
|---|---|---|
| Functionality | `uppercase` | list in, list out |
| Task | `validate` = `removeEmptyStrings`, then `trimEachAndRemoveEmpty` | list in, list out |
| Workflow | `WF-MAIN` = `validate`, `clean`, `refine` (sequential) | list in, list out |
| Chain | `WF-MAIN`, then `WF-NOTIFY` | list in, list out |

We will first build a simple working solution, see where it hurts, and then improve it.

## Solution 1: Simple Approach (One Big If-Else Chain)

### Idea

Store each task as a plain list of functionality names. When a workflow runs, loop over its tasks. For every functionality name, one long `if-else` chain decides what to do with the list.

Each workflow is a small `Workflow` object with its task ids, a boolean for the mode, and two links: `downstream` (next workflow) and `upstream` (previous workflow). All workflows live in a `dict` from id to `Workflow`, so every lookup is O(1).

### Reading Task Definitions

A definition looks like `validate-removeEmptyStrings,trimEachAndRemoveEmpty`. We split it at the **last** `-` using `rsplit("-", 1)`. The part before it is the task id. The part after it is a comma separated list of functionality names.

Why the last `-`? A task id may itself contain a `-`, just like the workflow id `WF-MAIN`. Functionality names never contain a `-`, so the last one is always the real separator.

### Connecting Workflows

`connectWorkflows(source, target)` returns `False` when:

1. Either workflow does not exist.
2. Source and target are the same workflow.
3. Source already has a downstream workflow.
4. Target already has an upstream workflow.
5. The new link would create a cycle.

Since every workflow has at most one downstream and one upstream, workflows always form straight chains like `A -> B -> C`. Checks 3 and 4 also tell us that source is the **end** of its chain and target is the **start** of its chain.

So a loop can only form when both are in the same chain. To check this, start at target and follow the `downstream` links. If we reach source, the new link would close a loop. No DFS or BFS is needed, just one simple walk.

Example: `A -> B -> C` already exists and we call `connectWorkflows("C", "A")`. Walking from `A` we visit `A`, `B`, `C`. We reached the source `C`, so we return `False`.

### Running a Workflow Chain

`executeWorkflow` first copies the input with `list(inputData)`, so the caller's list is never changed. Then it runs the workflow, passes its output to the downstream workflow, and repeats until there is no downstream. The output of the last workflow is the answer.

Inside one workflow:

- **Sequential:** the output of one task becomes the input of the next.
- **Parallel:** every task gets the same input, and the outputs are joined in task order.

If the workflow does not exist, we return an empty list.

### Code

```python
class Workflow:
    """Plain data holder for one workflow."""

    def __init__(self, workflow_id, task_ids, run_in_parallel):
        self.id = workflow_id
        self.task_ids = task_ids
        self.run_in_parallel = run_in_parallel
        self.downstream = None  # next workflow in the chain
        self.upstream = None    # previous workflow in the chain


class WorkflowManager:
    def __init__(self, availableTaskDefinitions):
        self.task_functionalities = {}  # task id -> functionality names, in order
        self.workflows = {}             # workflow id -> Workflow
        for definition in availableTaskDefinitions:
            # split at the LAST '-', functionality names never contain '-'
            task_id, names = definition.rsplit("-", 1)
            functionality_names = [name.strip() for name in names.split(",")]
            self.task_functionalities[task_id] = functionality_names

    def submitWorkflow(self, workflowId, taskIds, runInParallel):
        if workflowId is None or workflowId in self.workflows:
            return False
        if not taskIds:
            return False
        for task_id in taskIds:
            if task_id not in self.task_functionalities:
                return False
        self.workflows[workflowId] = Workflow(workflowId, list(taskIds), runInParallel)
        return True

    def connectWorkflows(self, sourceWorkflowId, targetWorkflowId):
        source = self.workflows.get(sourceWorkflowId)
        target = self.workflows.get(targetWorkflowId)
        if source is None or target is None or source is target:
            return False
        if source.downstream is not None or target.upstream is not None:
            return False
        # if we can reach source by walking down from target, the link makes a loop
        current = target
        while current is not None:
            if current is source:
                return False
            current = current.downstream
        source.downstream = target
        target.upstream = source
        return True

    def executeWorkflow(self, workflowId, inputData):
        current = self.workflows.get(workflowId)
        if current is None:
            return []
        data = list(inputData) if inputData else []

        while current is not None:
            if current.run_in_parallel:
                # every task gets the same input, outputs are joined in task order
                merged = []
                for task_id in current.task_ids:
                    merged.extend(self.run_task(task_id, data))
                data = merged
            else:
                # output of one task is the input of the next
                for task_id in current.task_ids:
                    data = self.run_task(task_id, data)
            current = current.downstream
        return data

    def run_task(self, task_id, items):
        data = items
        for name in self.task_functionalities[task_id]:
            data = self.apply_functionality(name, data)
        return data

    def apply_functionality(self, name, items):
        # One big if-else chain: the logic of every functionality lives here
        if name == "removeEmptyStrings":
            return [s for s in items if s.strip() != ""]
        if name == "trimEachAndRemoveEmpty":
            return [s.strip() for s in items if s.strip() != ""]
        if name == "removeDuplicateStringsIgnoreCaseKeepFirst":
            seen = set()
            result = []
            for s in items:
                if s.lower() not in seen:
                    seen.add(s.lower())
                    result.append(s)
            return result
        if name == "sortStringsLexicographically":
            return sorted(items)
        if name == "sortByLength":
            return sorted(items, key=len)  # stable sort
        if name == "reverseList":
            return list(reversed(items))
        if name == "lowercase":
            return [s.lower() for s in items]
        if name == "uppercase":
            return [s.upper() for s in items]
        return list(items)  # unknown name, keep data as it is

    def getWorkflowDetails(self, workflowId):
        workflow = self.workflows.get(workflowId)
        if workflow is None:
            return "WORKFLOW_NOT_FOUND"
        mode = "PARALLEL" if workflow.run_in_parallel else "SEQUENTIAL"
        downstream_id = workflow.downstream.id if workflow.downstream is not None else ""
        return (f"workflowId={workflow.id}, mode={mode}, "
                f"tasks={'->'.join(workflow.task_ids)}, downstream={downstream_id}")
```

### Problems With This Approach

The code works, but it gets painful as the system grows.

- **One giant if-else chain.** The logic of all 8 functionalities sits inside one method of `WorkflowManager`. Adding a new functionality means editing the core class and touching code that already works.

- **Repeated name matching.** Every run compares functionality names as strings, again and again, for every task. This work could be done just once.

- **Mode is a boolean.** Sequential vs parallel is an `if-else` inside the run loop, and `getWorkflowDetails` checks the same boolean again to print the mode. A third mode would mean editing both places.

- **One class does everything.** Parsing, storing, list logic, running and formatting all live in `WorkflowManager`. It is hard to test a single functionality on its own.

## Solution 2: Strategy + Simple Factory (Better Design)

The connect logic and the chain loop stay exactly the same. What changes is how we organize the functionalities and the execution modes.

### Step 1: Every Functionality Becomes a Strategy

```python
class StringListOperation:
    def apply(self, items):
        raise NotImplementedError
```

Each of the 8 functionalities becomes one small subclass of this base class, like `Uppercase`, `ReverseList` or `SortByLength`.

**Why Strategy?** All 8 functionalities do the same kind of job (list in, list out), just in different ways. Strategy puts each way behind the same `apply()` method. A task does not care which operations it holds. It just calls `apply()` on each of them.

One important rule: every operation returns a **new** list and never changes the list it receives. This matters for parallel workflows. All their tasks share the same input list, so one task must never change it for the others. List comprehensions, `sorted()` and `list(reversed(...))` all build new lists, so this comes for free.

`RemoveDuplicatesIgnoreCase` uses a `set` of lowercase strings. It tells us in O(1) whether we already kept a string, so the first copy stays and later copies are skipped.

### Step 2: A Simple Factory Creates the Strategies

`OperationFactory` keeps a `dict` from each functionality name to its class. `create(name)` turns a name like `"lowercase"` into a `Lowercase` object. It is now the only place in the code that knows the functionality names.

**Why a factory?** The names arrive as plain strings, and someone has to map strings to objects. Doing it in one small class keeps `WorkflowManager` clean. A new functionality is just one new class plus one new entry in that `dict`.

The factory is used only inside the constructor. After that, every `Task` holds ready-made operation objects, so no name matching happens while running.

### Step 3: Task Holds Its Operations

A `Task` has an id and a list of `StringListOperation` objects. Its `run()` method feeds the list through the operations one by one, in the order of the task definition.

### Step 4: Execution Mode Is Also a Strategy

```python
class ExecutionMode:
    def run(self, tasks, items):
        raise NotImplementedError

    def get_name(self):
        raise NotImplementedError
```

- `SequentialMode`: the output of one task is the input of the next.
- `ParallelMode`: every task gets the same input, and the outputs are joined in task order.

Each mode also knows its own name (`SEQUENTIAL` or `PARALLEL`), which `getWorkflowDetails` prints. So everything about a mode lives in one place.

"Parallel" here only means that every task gets the same input. We still run the tasks one by one in a single thread. This also keeps the joined output in the exact task order.

Modes hold no data, so the manager creates one object of each and shares it with every workflow.

### Step 5: Workflow and WorkflowManager

`Workflow` keeps its id, its `Task` objects, its mode, and its `downstream` and `upstream` links. It can run itself and describe itself with `get_details()`.

**Why keep `upstream`?** Only to check "does target already have an upstream workflow?" in O(1). Without it, we would have to scan all workflows.

`WorkflowManager` now only coordinates. It keeps two dicts:

- `tasks`: task id to `Task`, built once in the constructor.
- `workflows`: workflow id to `Workflow`.

Every API method looks things up by id, and a `dict` finds them in O(1).

### Why These Patterns and Not Others?

**Why not keep the if-else chain?** It works for today's 8 functionalities. But every new functionality or mode means editing the same core methods. With Strategy, new behaviour is a new class, and old tested code stays untouched.

**Chain of Responsibility** sounds like a match for connected workflows, because they literally form a chain. But in that pattern each handler decides whether to handle a request or pass it along. Here every workflow always runs and always forwards its output.

So a `downstream` link and a simple loop are enough. The loop is also safer than workflows calling each other. Python's default recursion limit is about 1000, so a long chain of nested calls would crash.

**Template Method** could model the modes as `SequentialWorkflow` and `ParallelWorkflow` subclasses. But there is no shared skeleton with a few changing steps here. The whole run logic is different. Strategy keeps a single `Workflow` class and simply plugs in the mode, which is simpler and easier to extend.

### Complexity

Let `n` be the size of the input list, `k` the number of tasks in a workflow and `L` the number of workflows in a chain.

- **Constructor:** O(total length of all task definitions).
- **submitWorkflow:** O(k).
- **connectWorkflows:** O(L), one walk down the target's chain.
- **executeWorkflow:** O(n) for most functionalities and O(n log n) for the two sorts, repeated for every functionality of every task of every workflow in the chain. A parallel workflow can return a longer list than it received, because it joins the outputs of all its tasks.
- **getWorkflowDetails:** O(k).

### Code

```python
class StringListOperation:
    """Strategy: one simple string-list functionality.
    Every operation returns a NEW list and never changes the list it receives."""

    def apply(self, items):
        raise NotImplementedError


class RemoveEmptyStrings(StringListOperation):
    # removeEmptyStrings: drop strings that are empty or contain only spaces
    def apply(self, items):
        return [s for s in items if s.strip() != ""]


class TrimEachAndRemoveEmpty(StringListOperation):
    # trimEachAndRemoveEmpty: trim every string, then drop the ones that became empty
    def apply(self, items):
        trimmed = [s.strip() for s in items]
        return [s for s in trimmed if s != ""]


class RemoveDuplicatesIgnoreCase(StringListOperation):
    # removeDuplicateStringsIgnoreCaseKeepFirst: keep only the first copy, ignoring case
    def apply(self, items):
        seen = set()  # lowercase forms already kept
        result = []
        for s in items:
            key = s.lower()
            if key not in seen:
                seen.add(key)
                result.append(s)
        return result


class SortLexicographically(StringListOperation):
    # sortStringsLexicographically: normal dictionary order
    def apply(self, items):
        return sorted(items)


class SortByLength(StringListOperation):
    # sortByLength: shorter strings first, equal lengths keep their old order
    def apply(self, items):
        # sorted() is stable, so equal lengths keep their relative order
        return sorted(items, key=len)


class ReverseList(StringListOperation):
    # reverseList: last string comes first
    def apply(self, items):
        return list(reversed(items))


class Lowercase(StringListOperation):
    # lowercase: every string in lowercase
    def apply(self, items):
        return [s.lower() for s in items]


class Uppercase(StringListOperation):
    # uppercase: every string in uppercase
    def apply(self, items):
        return [s.upper() for s in items]


class OperationFactory:
    """Simple Factory: turns a functionality name into its operation object.
    This is the only class that knows all the functionality names."""

    def __init__(self):
        # functionality name -> class that implements it
        self.operation_classes = {
            "removeEmptyStrings": RemoveEmptyStrings,
            "trimEachAndRemoveEmpty": TrimEachAndRemoveEmpty,
            "removeDuplicateStringsIgnoreCaseKeepFirst": RemoveDuplicatesIgnoreCase,
            "sortStringsLexicographically": SortLexicographically,
            "sortByLength": SortByLength,
            "reverseList": ReverseList,
            "lowercase": Lowercase,
            "uppercase": Uppercase,
        }

    def create(self, name):
        operation_class = self.operation_classes.get(name)
        if operation_class is None:
            return None  # never happens, inputs are guaranteed valid
        return operation_class()


class Task:
    """A task is an id plus its operations, applied one after another."""

    def __init__(self, task_id, operations):
        self.id = task_id
        self.operations = operations

    def run(self, items):
        data = items
        for operation in self.operations:
            data = operation.apply(data)
        return data


class ExecutionMode:
    """Strategy: how the tasks of one workflow are run."""

    def run(self, tasks, items):
        raise NotImplementedError

    def get_name(self):
        # "SEQUENTIAL" or "PARALLEL", used in workflow details
        raise NotImplementedError


class SequentialMode(ExecutionMode):
    """Output of one task becomes the input of the next task."""

    def run(self, tasks, items):
        data = items
        for task in tasks:
            data = task.run(data)
        return data

    def get_name(self):
        return "SEQUENTIAL"


class ParallelMode(ExecutionMode):
    """Every task gets the same input. Their outputs are joined in task order."""

    def run(self, tasks, items):
        result = []
        for task in tasks:
            result.extend(task.run(items))
        return result

    def get_name(self):
        return "PARALLEL"


class Workflow:
    """A workflow: its tasks, how to run them, and its links in the chain."""

    def __init__(self, workflow_id, tasks, mode):
        self.id = workflow_id
        self.tasks = tasks
        self.mode = mode
        self.downstream = None  # next workflow in the chain
        self.upstream = None    # previous workflow in the chain

    def run(self, items):
        return self.mode.run(self.tasks, items)

    def get_details(self):
        task_ids = "->".join(task.id for task in self.tasks)
        downstream_id = self.downstream.id if self.downstream is not None else ""
        return (f"workflowId={self.id}, mode={self.mode.get_name()}, "
                f"tasks={task_ids}, downstream={downstream_id}")


class WorkflowManager:
    def __init__(self, availableTaskDefinitions):
        self.tasks = {}      # task id -> Task
        self.workflows = {}  # workflow id -> Workflow

        # modes keep no data, so all workflows can share these two objects
        self.sequential_mode = SequentialMode()
        self.parallel_mode = ParallelMode()

        factory = OperationFactory()
        for definition in availableTaskDefinitions:
            # split at the LAST '-', functionality names never contain '-'
            task_id, names = definition.rsplit("-", 1)
            operations = []
            for name in names.split(","):
                operation = factory.create(name.strip())
                if operation is not None:
                    operations.append(operation)
            self.tasks[task_id] = Task(task_id, operations)

    def submitWorkflow(self, workflowId, taskIds, runInParallel):
        if workflowId is None or workflowId in self.workflows:
            return False
        if not taskIds:
            return False
        workflow_tasks = []
        for task_id in taskIds:
            if task_id not in self.tasks:
                return False  # unknown task id
            workflow_tasks.append(self.tasks[task_id])
        mode = self.parallel_mode if runInParallel else self.sequential_mode
        self.workflows[workflowId] = Workflow(workflowId, workflow_tasks, mode)
        return True

    def connectWorkflows(self, sourceWorkflowId, targetWorkflowId):
        source = self.workflows.get(sourceWorkflowId)
        target = self.workflows.get(targetWorkflowId)
        if source is None or target is None or source is target:
            return False
        if source.downstream is not None or target.upstream is not None:
            return False
        # Walk down the chain that starts at target.
        # If we meet source, the new link would close a loop.
        current = target
        while current is not None:
            if current is source:
                return False
            current = current.downstream
        source.downstream = target
        target.upstream = source
        return True

    def executeWorkflow(self, workflowId, inputData):
        current = self.workflows.get(workflowId)
        if current is None:
            return []
        # copy, so the caller's list is never touched
        data = list(inputData) if inputData else []

        # run this workflow, then every workflow after it in the chain
        while current is not None:
            data = current.run(data)
            current = current.downstream
        return data

    def getWorkflowDetails(self, workflowId):
        workflow = self.workflows.get(workflowId)
        if workflow is None:
            return "WORKFLOW_NOT_FOUND"
        return workflow.get_details()
```