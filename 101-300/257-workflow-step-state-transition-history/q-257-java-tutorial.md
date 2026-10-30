# Workflow Step State Transitions History in Java

#### Problem Statement
[https://codezym.com/question/257-workflow-step-state-transition-history](https://codezym.com/question/257-workflow-step-state-transition-history)


This is a low level design problem. Every step is a tiny **state machine**. It starts in `Pending`, can move to `Running`, and from there to `Completed` or `Failed`. Our job is to allow only these moves and to write down every move that succeeds.

The cleanest tool for this is not a big design pattern. It is a small **transition table**: for each state, the set of states it may move to. One lookup in this table answers "is this move allowed?".

The **State** and **Observer** patterns sound like a good fit for "state changes" and "recording changes". But here they would only add extra classes, so we do not use them. We will see why below.

Everything else is simple classes and maps. `WorkflowStateLogger` finds a `Workflow` by id, a `Workflow` finds a `Step` by id, and each `Step` keeps its current state and its list of `Transition`s.


## Quick Recap of the Rules

- Every step starts in `Pending`. Creating a workflow does **not** add a transition.
- History lines look like `"15,Pending,Running"`, oldest first.
- Snapshot lines look like `"build,Running,115"`, sorted by step id. A step that was never updated shows the workflow creation time.
- Errors are checked in the order the problem lists them, and a failed call changes nothing.


## Which Design Pattern Fits?

Short answer: no big pattern is needed. The rules are just data, so we keep them in a table.

### State Pattern (sounds good, but not needed)

Steps have states, so the State pattern looks natural. We would write `PendingState`, `RunningState`, `CompletedState` and `FailedState` classes.

But every one of them answers just one question: "can I move to this state?". Four classes for one yes or no question is too much.

A table says the same thing in four lines, and it shows all the rules in one place. The State pattern pays off only when each state has its own behavior, like retry limits or timeouts.

### Observer Pattern (sounds good, but not needed)

Recording every change sounds like a job for a listener. The step announces "I changed", and a history recorder listens.

But there is only one listener, and the history belongs to the step itself. A listener interface adds moving parts, and changes would be lost if someone forgot to register the listener.

One line inside the method that changes the state does the same job, and it can never be skipped. Observer becomes useful when many parts must react to a change, like alerts or dashboards.


## Solution 1: Simple Approach with Maps

Let us first solve it in the most direct way. We glue the two ids into one key, like `"order_1#pack"`, and keep everything about a step in plain maps.

```java
// workflow id -> its step ids, in the order they were given
Map<String, List<String>> workflowSteps = new HashMap<>();
// "workflowId#stepId" -> current state
Map<String, String> stepState = new HashMap<>();
// "workflowId#stepId" -> last updated timestamp
Map<String, Long> lastUpdated = new HashMap<>();
// "workflowId#stepId" -> history lines like "15,Pending,Running"
Map<String, List<String>> history = new HashMap<>();
```

The rules are plain `if-else` checks:

```java
boolean isAllowed(String fromState, String toState) {
    if (fromState.equals("Pending")) {
        return toState.equals("Running");
    }
    if (fromState.equals("Running")) {
        return toState.equals("Completed") || toState.equals("Failed");
    }
    return false; // Completed and Failed are final
}
```

A successful update must change all three step maps:

```java
String oldState = stepState.get(key);
if (!isAllowed(oldState, newState)) {
    return "INVALID_TRANSITION";
}
// one step lives in three maps, and all three must be updated
stepState.put(key, newState);
lastUpdated.put(key, currentTimestamp);
history.get(key).add(currentTimestamp + "," + oldState + "," + newState);
return "UPDATED";
```

And the snapshot sorts the step ids every time it is called:

```java
List<String> ids = new ArrayList<>(workflowSteps.get(workflowId));
Collections.sort(ids); // sorted again on every call
for (String stepId : ids) {
    String key = workflowId + "#" + stepId;
    result.add(stepId + "," + stepState.get(key) + "," + lastUpdated.get(key));
}
```

### Problems with This Approach

This code works, but it has some weak spots.

- **One step is split across three maps.** Every update must change all three. Forget one, and the data no longer agrees. The glued key also works only because `#` can never appear in an id.
- **The rules are buried in `if-else` code.** You have to read the code to see which moves are allowed. Adding a state like `Cancelled` means editing these conditions.
- **The snapshot sorts on every call.** Step ids never change after the workflow is created, so sorting them again and again is wasted work.


## Solution 2: Clean Design with Classes and a Transition Table

We fix the problems above one at a time.

### Step 1: `TransitionRules`, the Rules as a Table

`TransitionRules` keeps a `Map` from each state to the `Set` of states it may move to.

| From | Allowed to |
| --- | --- |
| `Pending` | `Running` |
| `Running` | `Completed`, `Failed` |
| `Completed` | nothing |
| `Failed` | nothing |

The only question we ever ask is "is this state in the set?", and a `Set` answers it fast. So `canMove(fromState, toState)` is a single lookup.

Moving to the same state, or back to `Pending`, fails on its own, because those states are never in the set. Adding a new state is one more line in the table.

We keep the state names as plain strings, because the input and the output already use these exact names. An enum would also work, but it needs extra code to turn names into enum values and back.

### Step 2: `Step` and `Transition`

All data about one step now lives in one `Step` object: its id, its current state, its last update time and its history.

A `Transition` is one recorded move: the time, the old state and the new state. Its `toString()` gives the exact format the problem asks for.

`moveTo(newState, timestamp)` is the only place where a step changes. It adds the transition and updates the state and the time together, so they can never get out of sync.

`lastUpdatedAt` starts at the workflow creation time. That is exactly what the snapshot needs for a step that was never updated.

**Why is the history never sorted?** Timestamps never go down. So every new transition has a time equal to or bigger than the last one, and we add it at the end of the list. Ties stay in the order they were recorded, which is exactly what the problem wants.

Also, a step can change at most twice: `Pending` to `Running`, then `Running` to `Completed` or `Failed`. So a history never holds more than 2 entries.

### Step 3: `Workflow`

A `Workflow` keeps its steps in two ways:

- `steps`: a `HashMap` from step id to step, for fast lookup in updates and history calls.
- `sortedSteps`: a `List` of the same step objects, sorted by id once, when the workflow is created.

Both hold the same objects, so a change made through the map also shows up in the list.

No step is ever added or removed after creation, so one sort is enough. The snapshot simply walks this list.

Sorting uses plain `compareTo`, which is case-sensitive. Uppercase letters come before lowercase ones, so `"Ship"` comes before `"pack"`.

### Step 4: `WorkflowStateLogger`

The logger keeps a `HashMap` from workflow id to workflow, because every call starts with a workflow id. It also holds one `TransitionRules` object.

Each method checks the errors in the order the problem gives, and only then changes anything.

- **createWorkflow:** a `HashSet` keeps one copy of each id. If the set is smaller than the list, some step id is repeated. The workflow is built only after both checks pass, so a failed call stores nothing.
- **updateStepState:** find the workflow, find the step, ask `canMove`, and only then call `moveTo`.
- **getStepHistory** and **getWorkflowSnapshot:** find, then format. They get `currentTimestamp` only to keep the global call order, so we do not need it.

### Code

```java
import java.util.*;

/**
 * The allowed moves between step states, kept in one table.
 * A new state or a new move is just one more line here.
 */
class TransitionRules {
    // state -> the states it is allowed to move to
    Map<String, Set<String>> allowedMoves = new HashMap<>();

    TransitionRules() {
        allowedMoves.put("Pending", Set.of("Running"));
        allowedMoves.put("Running", Set.of("Completed", "Failed"));
        allowedMoves.put("Completed", Set.of()); // final state
        allowedMoves.put("Failed", Set.of());    // final state
    }

    /** True if a step may move from fromState to toState. */
    boolean canMove(String fromState, String toState) {
        return allowedMoves.get(fromState).contains(toState);
    }
}

/** One recorded state change of a step. */
class Transition {
    long timestamp;
    String fromState;
    String toState;

    Transition(long timestamp, String fromState, String toState) {
        this.timestamp = timestamp;
        this.fromState = fromState;
        this.toState = toState;
    }

    /** Format asked by the problem: "timestamp,oldState,newState". */
    public String toString() {
        return timestamp + "," + fromState + "," + toState;
    }
}

/** One step of a workflow: its current state and every change it went through. */
class Step {
    String id;
    String state = "Pending"; // every step starts in Pending
    long lastUpdatedAt;       // workflow creation time until the first update
    List<Transition> history = new ArrayList<>();

    Step(String id, long createdAt) {
        this.id = id;
        this.lastUpdatedAt = createdAt;
    }

    /** Moves the step to newState and records the change. The move is already checked. */
    void moveTo(String newState, long timestamp) {
        history.add(new Transition(timestamp, state, newState));
        state = newState;
        lastUpdatedAt = timestamp;
    }

    /** Format asked by the problem: "stepId,state,lastUpdatedCurrentTimestamp". */
    String snapshot() {
        return id + "," + state + "," + lastUpdatedAt;
    }
}

/** A workflow and its steps. No step is added or removed after creation. */
class Workflow {
    String id;
    // step id -> step, for fast lookup
    Map<String, Step> steps = new HashMap<>();
    // the same steps, sorted by id only once, for the snapshot
    List<Step> sortedSteps = new ArrayList<>();

    Workflow(String id, List<String> stepIds, long createdAt) {
        this.id = id;
        for (String stepId : stepIds) {
            Step step = new Step(stepId, createdAt);
            steps.put(stepId, step);
            sortedSteps.add(step);
        }
        sortedSteps.sort((a, b) -> a.id.compareTo(b.id));
    }
}

/**
 * Entry point. It finds workflows and steps by id, checks every move
 * against the transition rules, and returns results in the asked format.
 */
public class WorkflowStateLogger {
    // workflow id -> workflow
    Map<String, Workflow> workflows = new HashMap<>();
    TransitionRules rules = new TransitionRules();

    public WorkflowStateLogger() {
    }

    public String createWorkflow(String workflowId, List<String> stepIds, long currentTimestamp) {
        if (workflows.containsKey(workflowId)) {
            return "DUPLICATE_WORKFLOW";
        }
        // a set keeps one copy of each id, so a smaller set means a repeated id
        if (new HashSet<>(stepIds).size() < stepIds.size()) {
            return "DUPLICATE_STEP";
        }
        workflows.put(workflowId, new Workflow(workflowId, stepIds, currentTimestamp));
        return "CREATED";
    }

    public String updateStepState(String workflowId, String stepId, String newState,
                                  long currentTimestamp) {
        Workflow workflow = workflows.get(workflowId);
        if (workflow == null) {
            return "INVALID_WORKFLOW";
        }
        Step step = workflow.steps.get(stepId);
        if (step == null) {
            return "INVALID_STEP";
        }
        if (!rules.canMove(step.state, newState)) {
            return "INVALID_TRANSITION";
        }
        step.moveTo(newState, currentTimestamp);
        return "UPDATED";
    }

    public List<String> getStepHistory(String workflowId, String stepId, long currentTimestamp) {
        Workflow workflow = workflows.get(workflowId);
        if (workflow == null) {
            return List.of("INVALID_WORKFLOW");
        }
        Step step = workflow.steps.get(stepId);
        if (step == null) {
            return List.of("INVALID_STEP");
        }
        // timestamps never go down, so the history is already in the right order
        List<String> result = new ArrayList<>();
        for (Transition transition : step.history) {
            result.add(transition.toString());
        }
        return result;
    }

    public List<String> getWorkflowSnapshot(String workflowId, long currentTimestamp) {
        Workflow workflow = workflows.get(workflowId);
        if (workflow == null) {
            return List.of("INVALID_WORKFLOW");
        }
        List<String> result = new ArrayList<>();
        for (Step step : workflow.sortedSteps) {
            result.add(step.snapshot());
        }
        return result;
    }
}
```


## Complexity

Let `S` be the number of steps in a workflow.

| Method | Time |
| --- | --- |
| `createWorkflow` | O(S log S), build the steps and sort them once |
| `updateStepState` | O(1) |
| `getStepHistory` | O(1), a history has at most 2 entries |
| `getWorkflowSnapshot` | O(S), one walk over the sorted list |

Map lookups are O(1) on average. Space is O(total number of steps), because each step stores at most 2 transitions.


## Edge Cases to Remember

- `DUPLICATE_WORKFLOW` wins over `DUPLICATE_STEP`.
- A failed create stores nothing, so the same workflow id can be created later.
- `Completed` and `Failed` are final. No move ever goes back to `Pending`.
- Ids and state names are case-sensitive. `pack` and `Pack` are two different steps.
- A step with no transitions returns an empty history list, not an error.
- The same step id in two different workflows means two separate steps.