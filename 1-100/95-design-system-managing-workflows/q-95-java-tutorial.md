# Design System for Managing Workflows in Java

#### Problem Statement
[https://codezym.com/question/95-design-system-managing-workflows](https://codezym.com/question/95-design-system-managing-workflows)

## The Core Idea

Every piece of this problem takes a `List<String>` in and gives a `List<String>` out. A functionality does it, a task does it, a workflow does it, and even a whole chain of workflows does it. So the design is just small pieces that plug into each other.

The best fit here is a combination of two patterns. **Strategy** is used twice: each of the 8 functionalities is a small strategy class, and the two execution modes (sequential and parallel) are strategies too. A **Simple Factory** turns a name like `"sortByLength"` into the right strategy object.

Connected workflows always form a straight line, so we keep them as a simple linked list. Each workflow remembers its next (downstream) and previous (upstream) workflow, and running a chain is just a loop.

| Level | Example | Works on |
|---|---|---|
| Functionality | `uppercase` | list in, list out |
| Task | `validate` = `removeEmptyStrings`, then `trimEachAndRemoveEmpty` | list in, list out |
| Workflow | `WF-MAIN` = `validate`, `clean`, `refine` (sequential) | list in, list out |
| Chain | `WF-MAIN`, then `WF-NOTIFY` | list in, list out |

We will first build a simple working solution, see where it hurts, and then improve it.

## Solution 1: Simple Approach (One Big Switch)

### Idea

Store each task as a plain list of functionality names. When a workflow runs, loop over its tasks. For every functionality name, one big `switch` decides what to do with the list.

Each workflow is a small `Workflow` object with its task ids, a boolean for the mode, and two links: `downstream` (next workflow) and `upstream` (previous workflow). All workflows live in a `HashMap` from id to `Workflow`, so every lookup is O(1).

### Reading Task Definitions

A definition looks like `validate-removeEmptyStrings,trimEachAndRemoveEmpty`. We split it at the **last** `-`. The part before it is the task id. The part after it is a comma separated list of functionality names.

Why the last `-`? A task id may itself contain a `-`, just like the workflow id `WF-MAIN`. Functionality names never contain a `-`, so the last one is always the real separator.

### Connecting Workflows

`connectWorkflows(source, target)` returns `false` when:

1. Either workflow does not exist.
2. Source and target are the same workflow.
3. Source already has a downstream workflow.
4. Target already has an upstream workflow.
5. The new link would create a cycle.

Since every workflow has at most one downstream and one upstream, workflows always form straight chains like `A -> B -> C`. Checks 3 and 4 also tell us that source is the **end** of its chain and target is the **start** of its chain.

So a loop can only form when both are in the same chain. To check this, start at target and follow the `downstream` links. If we reach source, the new link would close a loop. No DFS or BFS is needed, just one simple walk.

Example: `A -> B -> C` already exists and we call `connectWorkflows("C", "A")`. Walking from `A` we visit `A`, `B`, `C`. We reached the source `C`, so we return `false`.

### Running a Workflow Chain

`executeWorkflow` first copies the input list, so the caller's list is never changed. Then it runs the workflow, passes its output to the downstream workflow, and repeats until there is no downstream. The output of the last workflow is the answer.

Inside one workflow:

- **Sequential:** the output of one task becomes the input of the next.
- **Parallel:** every task gets the same input, and the outputs are joined in task order.

If the workflow does not exist, we return an empty list.

### Code

```java
import java.util.*;

/** Plain data holder for one workflow. */
class Workflow {
    String id;
    List<String> taskIds;
    boolean runInParallel;
    Workflow downstream; // next workflow in the chain, null if none
    Workflow upstream;   // previous workflow in the chain, null if none

    Workflow(String id, List<String> taskIds, boolean runInParallel) {
        this.id = id;
        this.taskIds = taskIds;
        this.runInParallel = runInParallel;
    }
}

public class WorkflowManager {

    // task id -> its functionality names, in order
    Map<String, List<String>> taskFunctionalities = new HashMap<>();

    // workflow id -> workflow
    Map<String, Workflow> workflows = new HashMap<>();

    public WorkflowManager(List<String> availableTaskDefinitions) {
        for (String definition : availableTaskDefinitions) {
            // split at the LAST '-', functionality names never contain '-'
            int dash = definition.lastIndexOf('-');
            List<String> names = new ArrayList<>();
            for (String name : definition.substring(dash + 1).split(",")) {
                names.add(name.trim());
            }
            taskFunctionalities.put(definition.substring(0, dash), names);
        }
    }

    public boolean submitWorkflow(String workflowId, List<String> taskIds, boolean runInParallel) {
        if (workflowId == null || workflows.containsKey(workflowId)) {
            return false;
        }
        if (taskIds == null || taskIds.isEmpty()) {
            return false;
        }
        for (String taskId : taskIds) {
            if (!taskFunctionalities.containsKey(taskId)) {
                return false;
            }
        }
        List<String> taskIdsCopy = new ArrayList<>(taskIds);
        workflows.put(workflowId, new Workflow(workflowId, taskIdsCopy, runInParallel));
        return true;
    }

    public boolean connectWorkflows(String sourceWorkflowId, String targetWorkflowId) {
        Workflow source = workflows.get(sourceWorkflowId);
        Workflow target = workflows.get(targetWorkflowId);
        if (source == null || target == null || source == target) {
            return false;
        }
        if (source.downstream != null || target.upstream != null) {
            return false;
        }
        // if we can reach source by walking down from target, the link makes a loop
        Workflow current = target;
        while (current != null) {
            if (current == source) {
                return false;
            }
            current = current.downstream;
        }
        source.downstream = target;
        target.upstream = source;
        return true;
    }

    public List<String> executeWorkflow(String workflowId, List<String> inputData) {
        Workflow current = workflows.get(workflowId);
        if (current == null) {
            return new ArrayList<>();
        }
        List<String> data = (inputData == null) ? new ArrayList<>() : new ArrayList<>(inputData);

        while (current != null) {
            if (current.runInParallel) {
                // every task gets the same input, outputs are joined in task order
                List<String> merged = new ArrayList<>();
                for (String taskId : current.taskIds) {
                    merged.addAll(runTask(taskId, data));
                }
                data = merged;
            } else {
                // output of one task is the input of the next
                for (String taskId : current.taskIds) {
                    data = runTask(taskId, data);
                }
            }
            current = current.downstream;
        }
        return data;
    }

    List<String> runTask(String taskId, List<String> input) {
        List<String> data = input;
        for (String name : taskFunctionalities.get(taskId)) {
            data = applyFunctionality(name, data);
        }
        return data;
    }

    // One big switch: the logic of every functionality lives here
    List<String> applyFunctionality(String name, List<String> input) {
        List<String> result = new ArrayList<>();
        switch (name) {
            case "removeEmptyStrings":
                for (String s : input) {
                    if (!s.trim().isEmpty()) {
                        result.add(s);
                    }
                }
                break;
            case "trimEachAndRemoveEmpty":
                for (String s : input) {
                    if (!s.trim().isEmpty()) {
                        result.add(s.trim());
                    }
                }
                break;
            case "removeDuplicateStringsIgnoreCaseKeepFirst": {
                Set<String> seen = new HashSet<>();
                for (String s : input) {
                    if (seen.add(s.toLowerCase())) {
                        result.add(s);
                    }
                }
                break;
            }
            case "sortStringsLexicographically":
                result.addAll(input);
                Collections.sort(result);
                break;
            case "sortByLength":
                result.addAll(input);
                result.sort((a, b) -> a.length() - b.length()); // stable sort
                break;
            case "reverseList":
                result.addAll(input);
                Collections.reverse(result);
                break;
            case "lowercase":
                for (String s : input) {
                    result.add(s.toLowerCase());
                }
                break;
            case "uppercase":
                for (String s : input) {
                    result.add(s.toUpperCase());
                }
                break;
            default:
                result.addAll(input); // unknown name, keep data as it is
        }
        return result;
    }

    public String getWorkflowDetails(String workflowId) {
        Workflow workflow = workflows.get(workflowId);
        if (workflow == null) {
            return "WORKFLOW_NOT_FOUND";
        }
        String mode = workflow.runInParallel ? "PARALLEL" : "SEQUENTIAL";
        String downstreamId = (workflow.downstream == null) ? "" : workflow.downstream.id;
        return "workflowId=" + workflow.id
                + ", mode=" + mode
                + ", tasks=" + String.join("->", workflow.taskIds)
                + ", downstream=" + downstreamId;
    }
}
```

### Problems With This Approach

The code works, but it gets painful as the system grows.

- **One giant switch.** The logic of all 8 functionalities sits inside one method of `WorkflowManager`. Adding a new functionality means editing the core class and touching code that already works.

- **Repeated name matching.** Every run matches functionality names as strings, again and again, for every task. This work could be done just once.

- **Mode is a boolean.** Sequential vs parallel is an `if-else` inside the run loop, and `getWorkflowDetails` checks the same boolean again to print the mode. A third mode would mean editing both places.

- **One class does everything.** Parsing, storing, list logic, running and formatting all live in `WorkflowManager`. It is hard to test a single functionality on its own.

## Solution 2: Strategy + Simple Factory (Better Design)

The connect logic and the chain loop stay exactly the same. What changes is how we organize the functionalities and the execution modes.

### Step 1: Every Functionality Becomes a Strategy

```java
interface StringListOperation {
    List<String> apply(List<String> input);
}
```

Each of the 8 functionalities becomes one small class that implements this interface, like `Uppercase`, `ReverseList` or `SortByLength`.

**Why Strategy?** All 8 functionalities do the same kind of job (list in, list out), just in different ways. Strategy puts each way behind one common interface. A task does not care which operations it holds. It just calls `apply()` on each of them.

One important rule: every operation returns a **new** list and never changes the list it receives. This matters for parallel workflows. All their tasks share the same input list, so one task must never change it for the others.

`RemoveDuplicatesIgnoreCase` uses a `HashSet` of lowercase strings. It tells us in O(1) whether we already kept a string, so the first copy stays and later copies are skipped.

### Step 2: A Simple Factory Creates the Strategies

`OperationFactory.create(name)` turns a name like `"lowercase"` into a `Lowercase` object. It is now the only place in the code that knows the functionality names.

**Why a factory?** The names arrive as plain strings, and someone has to map strings to objects. Doing it in one small class keeps `WorkflowManager` clean. A new functionality is just one new class plus one new line in the factory.

The factory is used only inside the constructor. After that, every `Task` holds ready-made operation objects, so no name matching happens while running.

### Step 3: Task Holds Its Operations

A `Task` has an id and a list of `StringListOperation` objects. Its `run()` method feeds the list through the operations one by one, in the order of the task definition.

### Step 4: Execution Mode Is Also a Strategy

```java
interface ExecutionMode {
    List<String> run(List<Task> tasks, List<String> input);

    String getName();
}
```

- `SequentialMode`: the output of one task is the input of the next.
- `ParallelMode`: every task gets the same input, and the outputs are joined in task order.

Each mode also knows its own name (`SEQUENTIAL` or `PARALLEL`), which `getWorkflowDetails` prints. So everything about a mode lives in one place.

"Parallel" here only means that every task gets the same input. We still run the tasks one by one in a single thread. This also keeps the joined output in the exact task order.

Modes hold no data, so the manager creates one object of each and shares it with every workflow.

### Step 5: Workflow and WorkflowManager

`Workflow` keeps its id, its `Task` objects, its mode, and its `downstream` and `upstream` links. It can run itself and describe itself with `getDetails()`.

**Why keep `upstream`?** Only to check "does target already have an upstream workflow?" in O(1). Without it, we would have to scan all workflows.

`WorkflowManager` now only coordinates. It keeps two `HashMap`s:

- `tasks`: task id to `Task`, built once in the constructor.
- `workflows`: workflow id to `Workflow`.

Every API method looks things up by id, and a `HashMap` finds them in O(1).

### Why These Patterns and Not Others?

**Why not keep the switch?** It works for today's 8 functionalities. But every new functionality or mode means editing the same core methods. With Strategy, new behaviour is a new class, and old tested code stays untouched.

**Chain of Responsibility** sounds like a match for connected workflows, because they literally form a chain. But in that pattern each handler decides whether to handle a request or pass it along. Here every workflow always runs and always forwards its output.

So a `downstream` link and a simple loop are enough. The loop is also safer than workflows calling each other, because a very long chain of nested calls could overflow the stack.

**Template Method** could model the modes as `SequentialWorkflow` and `ParallelWorkflow` subclasses. But there is no shared skeleton with a few changing steps here. The whole run logic is different. Strategy keeps a single `Workflow` class and simply plugs in the mode, which is simpler and easier to extend.

### Complexity

Let `n` be the size of the input list, `k` the number of tasks in a workflow and `L` the number of workflows in a chain.

- **Constructor:** O(total length of all task definitions).
- **submitWorkflow:** O(k).
- **connectWorkflows:** O(L), one walk down the target's chain.
- **executeWorkflow:** O(n) for most functionalities and O(n log n) for the two sorts, repeated for every functionality of every task of every workflow in the chain. A parallel workflow can return a longer list than it received, because it joins the outputs of all its tasks.
- **getWorkflowDetails:** O(k).

### Code

```java
import java.util.*;

/**
 * Strategy: one simple string-list functionality.
 * Every operation returns a NEW list and never changes the list it receives.
 */
interface StringListOperation {
    List<String> apply(List<String> input);
}

// removeEmptyStrings: drop strings that are empty or contain only spaces
class RemoveEmptyStrings implements StringListOperation {
    public List<String> apply(List<String> input) {
        List<String> result = new ArrayList<>();
        for (String s : input) {
            if (!s.trim().isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }
}

// trimEachAndRemoveEmpty: trim every string, then drop the ones that became empty
class TrimEachAndRemoveEmpty implements StringListOperation {
    public List<String> apply(List<String> input) {
        List<String> result = new ArrayList<>();
        for (String s : input) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}

// removeDuplicateStringsIgnoreCaseKeepFirst: keep only the first copy, ignoring case
class RemoveDuplicatesIgnoreCase implements StringListOperation {
    public List<String> apply(List<String> input) {
        Set<String> seen = new HashSet<>(); // lowercase forms already kept
        List<String> result = new ArrayList<>();
        for (String s : input) {
            // add() returns false when this lowercase form is already in the set
            if (seen.add(s.toLowerCase())) {
                result.add(s);
            }
        }
        return result;
    }
}

// sortStringsLexicographically: normal dictionary order
class SortLexicographically implements StringListOperation {
    public List<String> apply(List<String> input) {
        List<String> result = new ArrayList<>(input);
        Collections.sort(result);
        return result;
    }
}

// sortByLength: shorter strings first, equal lengths keep their old order
class SortByLength implements StringListOperation {
    public List<String> apply(List<String> input) {
        List<String> result = new ArrayList<>(input);
        // Java's list sort is stable, so equal lengths keep their relative order
        result.sort((a, b) -> a.length() - b.length());
        return result;
    }
}

// reverseList: last string comes first
class ReverseList implements StringListOperation {
    public List<String> apply(List<String> input) {
        List<String> result = new ArrayList<>(input);
        Collections.reverse(result);
        return result;
    }
}

// lowercase: every string in lowercase
class Lowercase implements StringListOperation {
    public List<String> apply(List<String> input) {
        List<String> result = new ArrayList<>();
        for (String s : input) {
            result.add(s.toLowerCase());
        }
        return result;
    }
}

// uppercase: every string in uppercase
class Uppercase implements StringListOperation {
    public List<String> apply(List<String> input) {
        List<String> result = new ArrayList<>();
        for (String s : input) {
            result.add(s.toUpperCase());
        }
        return result;
    }
}

/**
 * Simple Factory: turns a functionality name into its operation object.
 * This is the only class that knows all the functionality names.
 */
class OperationFactory {
    StringListOperation create(String name) {
        switch (name) {
            case "removeEmptyStrings":
                return new RemoveEmptyStrings();
            case "trimEachAndRemoveEmpty":
                return new TrimEachAndRemoveEmpty();
            case "removeDuplicateStringsIgnoreCaseKeepFirst":
                return new RemoveDuplicatesIgnoreCase();
            case "sortStringsLexicographically":
                return new SortLexicographically();
            case "sortByLength":
                return new SortByLength();
            case "reverseList":
                return new ReverseList();
            case "lowercase":
                return new Lowercase();
            case "uppercase":
                return new Uppercase();
            default:
                return null; // never happens, inputs are guaranteed valid
        }
    }
}

/** A task is an id plus its operations, applied one after another. */
class Task {
    String id;
    List<StringListOperation> operations;

    Task(String id, List<StringListOperation> operations) {
        this.id = id;
        this.operations = operations;
    }

    List<String> run(List<String> input) {
        List<String> data = input;
        for (StringListOperation operation : operations) {
            data = operation.apply(data);
        }
        return data;
    }
}

/** Strategy: how the tasks of one workflow are run. */
interface ExecutionMode {
    List<String> run(List<Task> tasks, List<String> input);

    String getName(); // "SEQUENTIAL" or "PARALLEL", used in workflow details
}

/** Output of one task becomes the input of the next task. */
class SequentialMode implements ExecutionMode {
    public List<String> run(List<Task> tasks, List<String> input) {
        List<String> data = input;
        for (Task task : tasks) {
            data = task.run(data);
        }
        return data;
    }

    public String getName() {
        return "SEQUENTIAL";
    }
}

/** Every task gets the same input. Their outputs are joined in task order. */
class ParallelMode implements ExecutionMode {
    public List<String> run(List<Task> tasks, List<String> input) {
        List<String> result = new ArrayList<>();
        for (Task task : tasks) {
            result.addAll(task.run(input));
        }
        return result;
    }

    public String getName() {
        return "PARALLEL";
    }
}

/** A workflow: its tasks, how to run them, and its links in the chain. */
class Workflow {
    String id;
    List<Task> tasks;
    ExecutionMode mode;
    Workflow downstream; // next workflow in the chain, null if none
    Workflow upstream;   // previous workflow in the chain, null if none

    Workflow(String id, List<Task> tasks, ExecutionMode mode) {
        this.id = id;
        this.tasks = tasks;
        this.mode = mode;
    }

    List<String> run(List<String> input) {
        return mode.run(tasks, input);
    }

    String getDetails() {
        List<String> taskIds = new ArrayList<>();
        for (Task task : tasks) {
            taskIds.add(task.id);
        }
        String downstreamId = (downstream == null) ? "" : downstream.id;
        return "workflowId=" + id
                + ", mode=" + mode.getName()
                + ", tasks=" + String.join("->", taskIds)
                + ", downstream=" + downstreamId;
    }
}

public class WorkflowManager {

    Map<String, Task> tasks = new HashMap<>();         // task id -> task
    Map<String, Workflow> workflows = new HashMap<>(); // workflow id -> workflow

    // modes keep no data, so all workflows can share these two objects
    ExecutionMode sequentialMode = new SequentialMode();
    ExecutionMode parallelMode = new ParallelMode();

    public WorkflowManager(List<String> availableTaskDefinitions) {
        OperationFactory factory = new OperationFactory();
        for (String definition : availableTaskDefinitions) {
            // split at the LAST '-', functionality names never contain '-'
            int dash = definition.lastIndexOf('-');
            String taskId = definition.substring(0, dash);

            List<StringListOperation> operations = new ArrayList<>();
            for (String name : definition.substring(dash + 1).split(",")) {
                StringListOperation operation = factory.create(name.trim());
                if (operation != null) {
                    operations.add(operation);
                }
            }
            tasks.put(taskId, new Task(taskId, operations));
        }
    }

    public boolean submitWorkflow(String workflowId, List<String> taskIds, boolean runInParallel) {
        if (workflowId == null || workflows.containsKey(workflowId)) {
            return false;
        }
        if (taskIds == null || taskIds.isEmpty()) {
            return false;
        }
        List<Task> workflowTasks = new ArrayList<>();
        for (String taskId : taskIds) {
            Task task = tasks.get(taskId);
            if (task == null) {
                return false; // unknown task id
            }
            workflowTasks.add(task);
        }
        ExecutionMode mode = runInParallel ? parallelMode : sequentialMode;
        workflows.put(workflowId, new Workflow(workflowId, workflowTasks, mode));
        return true;
    }

    public boolean connectWorkflows(String sourceWorkflowId, String targetWorkflowId) {
        Workflow source = workflows.get(sourceWorkflowId);
        Workflow target = workflows.get(targetWorkflowId);
        if (source == null || target == null || source == target) {
            return false;
        }
        if (source.downstream != null || target.upstream != null) {
            return false;
        }
        // Walk down the chain that starts at target.
        // If we meet source, the new link would close a loop.
        Workflow current = target;
        while (current != null) {
            if (current == source) {
                return false;
            }
            current = current.downstream;
        }
        source.downstream = target;
        target.upstream = source;
        return true;
    }

    public List<String> executeWorkflow(String workflowId, List<String> inputData) {
        Workflow current = workflows.get(workflowId);
        if (current == null) {
            return new ArrayList<>();
        }
        // copy, so the caller's list is never touched
        List<String> data = (inputData == null) ? new ArrayList<>() : new ArrayList<>(inputData);

        // run this workflow, then every workflow after it in the chain
        while (current != null) {
            data = current.run(data);
            current = current.downstream;
        }
        return data;
    }

    public String getWorkflowDetails(String workflowId) {
        Workflow workflow = workflows.get(workflowId);
        if (workflow == null) {
            return "WORKFLOW_NOT_FOUND";
        }
        return workflow.getDetails();
    }
}
```