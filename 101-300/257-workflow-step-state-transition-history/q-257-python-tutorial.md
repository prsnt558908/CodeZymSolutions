# Workflow Step State Transitions History in Python

#### Problem Statement
[https://codezym.com/question/257-workflow-step-state-transition-history](https://codezym.com/question/257-workflow-step-state-transition-history)


This is a low level design problem. Every step is a tiny **state machine**. It starts in `Pending`, can move to `Running`, and from there to `Completed` or `Failed`. Our job is to allow only these moves and to write down every move that succeeds.

The cleanest tool for this is not a big design pattern. It is a small **transition table**: for each state, the set of states it may move to. One lookup in this table answers "is this move allowed?".

The **State** and **Observer** patterns sound like a good fit for "state changes" and "recording changes". But here they would only add extra classes, so we do not use them. We will see why below.

Everything else is simple classes and dicts. `WorkflowStateLogger` finds a `Workflow` by id, a `Workflow` finds a `Step` by id, and each `Step` keeps its current state and its list of `Transition`s.


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

But there is only one listener, and the history belongs to the step itself. A listener class adds moving parts, and changes would be lost if someone forgot to register the listener.

One line inside the method that changes the state does the same job, and it can never be skipped. Observer becomes useful when many parts must react to a change, like alerts or dashboards.


## Solution 1: Simple Approach with Dicts

Let us first solve it in the most direct way. We use the pair `(workflowId, stepId)` as the key, and keep everything about a step in plain dicts.

```python
# workflow id -> its step ids, in the order they were given
self.workflow_steps = {}
# (workflowId, stepId) -> current state
self.step_state = {}
# (workflowId, stepId) -> last updated timestamp
self.last_updated = {}
# (workflowId, stepId) -> history lines like "15,Pending,Running"
self.history = {}
```

The rules are plain `if-else` checks:

```python
def is_allowed(self, from_state, to_state):
    """The rules are written as if-else checks."""
    if from_state == "Pending":
        return to_state == "Running"
    if from_state == "Running":
        return to_state in ("Completed", "Failed")
    return False  # Completed and Failed are final
```

A successful update must change all three step dicts:

```python
old_state = self.step_state[key]
if not self.is_allowed(old_state, newState):
    return "INVALID_TRANSITION"
# one step lives in three dicts, and all three must be updated
self.step_state[key] = newState
self.last_updated[key] = currentTimestamp
self.history[key].append(f"{currentTimestamp},{old_state},{newState}")
return "UPDATED"
```

And the snapshot sorts the step ids every time it is called:

```python
result = []
for step_id in sorted(self.workflow_steps[workflowId]):  # sorted again on every call
    key = (workflowId, step_id)
    result.append(f"{step_id},{self.step_state[key]},{self.last_updated[key]}")
return result
```

### Problems with This Approach

This code works, but it has some weak spots.

- **One step is split across three dicts.** Every update must change all three. Forget one, and the data no longer agrees.
- **The rules are buried in `if-else` code.** You have to read the code to see which moves are allowed. Adding a state like `Cancelled` means editing these conditions.
- **The snapshot sorts on every call.** Step ids never change after the workflow is created, so sorting them again and again is wasted work.


## Solution 2: Clean Design with Classes and a Transition Table

We fix the problems above one at a time.

### Step 1: `TransitionRules`, the Rules as a Table

`TransitionRules` keeps a dict from each state to the `set` of states it may move to.

| From | Allowed to |
| --- | --- |
| `Pending` | `Running` |
| `Running` | `Completed`, `Failed` |
| `Completed` | nothing |
| `Failed` | nothing |

The only question we ever ask is "is this state in the set?", and a `set` answers it fast. So `can_move(from_state, to_state)` is a single lookup.

Moving to the same state, or back to `Pending`, fails on its own, because those states are never in the set. Adding a new state is one more line in the table.

We keep the state names as plain strings, because the input and the output already use these exact names. An `Enum` would also work, but it needs extra code to turn names into enum members and back.

### Step 2: `Step` and `Transition`

All data about one step now lives in one `Step` object: its id, its current state, its last update time and its history.

A `Transition` is one recorded move: the time, the old state and the new state. Its `__str__` gives the exact format the problem asks for.

`move_to(new_state, timestamp)` is the only place where a step changes. It adds the transition and updates the state and the time together, so they can never get out of sync.

`last_updated_at` starts at the workflow creation time. That is exactly what the snapshot needs for a step that was never updated.

**Why is the history never sorted?** Timestamps never go down. So every new transition has a time equal to or bigger than the last one, and we add it at the end of the list. Ties stay in the order they were recorded, which is exactly what the problem wants.

Also, a step can change at most twice: `Pending` to `Running`, then `Running` to `Completed` or `Failed`. So a history never holds more than 2 entries.

### Step 3: `Workflow`

A `Workflow` keeps its steps in two ways:

- `steps`: a dict from step id to step, for fast lookup in updates and history calls.
- `sorted_steps`: a list of the same step objects, sorted by id once, when the workflow is created.

Both hold the same objects, so a change made through the dict also shows up in the list.

No step is ever added or removed after creation, so one sort is enough. The snapshot simply walks this list.

Python compares strings character by character, and it is case-sensitive. Uppercase letters come before lowercase ones, so `"Ship"` comes before `"pack"`.

### Step 4: `WorkflowStateLogger`

The logger keeps a dict from workflow id to workflow, because every call starts with a workflow id. It also holds one `TransitionRules` object.

Each method checks the errors in the order the problem gives, and only then changes anything.

- **createWorkflow:** a `set` keeps one copy of each id. If the set is smaller than the list, some step id is repeated. The workflow is built only after both checks pass, so a failed call stores nothing.
- **updateStepState:** find the workflow, find the step, ask `can_move`, and only then call `move_to`.
- **getStepHistory** and **getWorkflowSnapshot:** find, then format. They get `currentTimestamp` only to keep the global call order, so we do not need it.

The public methods keep the exact names the problem gives. Our own helpers use normal Python names, like `move_to`.

### Code

```python
class TransitionRules:
    """The allowed moves between step states, kept in one table.
    A new state or a new move is just one more line here."""

    def __init__(self):
        # state -> the states it is allowed to move to
        self.allowed_moves = {
            "Pending": {"Running"},
            "Running": {"Completed", "Failed"},
            "Completed": set(),  # final state
            "Failed": set(),     # final state
        }

    def can_move(self, from_state, to_state):
        """True if a step may move from from_state to to_state."""
        return to_state in self.allowed_moves[from_state]


class Transition:
    """One recorded state change of a step."""

    def __init__(self, timestamp, from_state, to_state):
        self.timestamp = timestamp
        self.from_state = from_state
        self.to_state = to_state

    def __str__(self):
        """Format asked by the problem: "timestamp,oldState,newState"."""
        return f"{self.timestamp},{self.from_state},{self.to_state}"


class Step:
    """One step of a workflow: its current state and every change it went through."""

    def __init__(self, step_id, created_at):
        self.id = step_id
        self.state = "Pending"             # every step starts in Pending
        self.last_updated_at = created_at  # workflow creation time until the first update
        self.history = []                  # Transition objects, oldest first

    def move_to(self, new_state, timestamp):
        """Moves the step to new_state and records the change. The move is already checked."""
        self.history.append(Transition(timestamp, self.state, new_state))
        self.state = new_state
        self.last_updated_at = timestamp

    def snapshot(self):
        """Format asked by the problem: "stepId,state,lastUpdatedCurrentTimestamp"."""
        return f"{self.id},{self.state},{self.last_updated_at}"


class Workflow:
    """A workflow and its steps. No step is added or removed after creation."""

    def __init__(self, workflow_id, step_ids, created_at):
        self.id = workflow_id
        # step id -> Step, for fast lookup
        self.steps = {}
        for step_id in step_ids:
            self.steps[step_id] = Step(step_id, created_at)
        # the same steps, sorted by id only once, for the snapshot
        self.sorted_steps = sorted(self.steps.values(), key=lambda step: step.id)


class WorkflowStateLogger:
    """Entry point. It finds workflows and steps by id, checks every move
    against the transition rules, and returns results in the asked format."""

    def __init__(self):
        # workflow id -> Workflow
        self.workflows = {}
        self.rules = TransitionRules()

    def createWorkflow(self, workflowId, stepIds, currentTimestamp):
        if workflowId in self.workflows:
            return "DUPLICATE_WORKFLOW"
        # a set keeps one copy of each id, so a smaller set means a repeated id
        if len(set(stepIds)) < len(stepIds):
            return "DUPLICATE_STEP"
        self.workflows[workflowId] = Workflow(workflowId, stepIds, currentTimestamp)
        return "CREATED"

    def updateStepState(self, workflowId, stepId, newState, currentTimestamp):
        workflow = self.workflows.get(workflowId)
        if workflow is None:
            return "INVALID_WORKFLOW"
        step = workflow.steps.get(stepId)
        if step is None:
            return "INVALID_STEP"
        if not self.rules.can_move(step.state, newState):
            return "INVALID_TRANSITION"
        step.move_to(newState, currentTimestamp)
        return "UPDATED"

    def getStepHistory(self, workflowId, stepId, currentTimestamp):
        workflow = self.workflows.get(workflowId)
        if workflow is None:
            return ["INVALID_WORKFLOW"]
        step = workflow.steps.get(stepId)
        if step is None:
            return ["INVALID_STEP"]
        # timestamps never go down, so the history is already in the right order
        return [str(transition) for transition in step.history]

    def getWorkflowSnapshot(self, workflowId, currentTimestamp):
        workflow = self.workflows.get(workflowId)
        if workflow is None:
            return ["INVALID_WORKFLOW"]
        return [step.snapshot() for step in workflow.sorted_steps]
```


## Complexity

Let `S` be the number of steps in a workflow.

| Method | Time |
| --- | --- |
| `createWorkflow` | O(S log S), build the steps and sort them once |
| `updateStepState` | O(1) |
| `getStepHistory` | O(1), a history has at most 2 entries |
| `getWorkflowSnapshot` | O(S), one walk over the sorted list |

Dict lookups are O(1) on average. Space is O(total number of steps), because each step stores at most 2 transitions.


## Edge Cases to Remember

- `DUPLICATE_WORKFLOW` wins over `DUPLICATE_STEP`.
- A failed create stores nothing, so the same workflow id can be created later.
- `Completed` and `Failed` are final. No move ever goes back to `Pending`.
- Ids and state names are case-sensitive. `pack` and `Pack` are two different steps.
- A step with no transitions returns an empty history list, not an error.
- The same step id in two different workflows means two separate steps.