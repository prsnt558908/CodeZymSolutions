# Design a Thread Pool Executor in Python

#### Problem Statement
[https://codezym.com/question/294-design-thread-pool-executor](https://codezym.com/question/294-design-thread-pool-executor)

## Core Idea

This problem is a **step by step simulation**. No real threads run. Time moves only when `runOneStep()` is called, and each call just runs the phases given in the problem, in the same order.

The whole pool needs only three things: a **dict of threads** by name, a **sorted list of idle thread names**, and a **FIFO queue** of waiting tasks.

The only rule that changes from one pool to another is what to do when the queue is full. So we use the **Strategy** pattern for the four rejection policies, with a small **Simple Factory** that creates the right policy from its name. This combination is the best fit for this problem.

The executor states and the thread states are plain enums. A full State pattern would add many classes without making the code any clearer.

---

## Thread and Executor Life Cycle

This is how a thread moves between its states:

```
             gets a task                shutdownThread()
    IDLE  ---------------->  RUNNING  -------------------->  STOPPING
     ^                          |                              |
     |       task finishes      |                              |  task finishes
     +--------------------------+                              v
                                                            removed
```

An IDLE thread can also be removed by `shutdownThread()`, by keep-alive or by the shutdown cleanup.

And this is the executor:

```
    RUNNING  --- shutdown() --->  SHUTTING_DOWN  --- nothing left --->  TERMINATED
```

If the pool is already empty, `shutdown()` goes straight to `TERMINATED`.

---

## A Simple First Attempt

The first idea is to keep all threads in one list and write every rule inside the executor class.

- To find the smallest idle thread, loop over all threads and compare names.
- To give queued tasks to idle threads in name order, sort the whole thread list on every step.
- To handle a full queue, write an if-elif chain inside `submitTask()`.

```python
# inside submitTask(), when the queue is full
if self.policy == "ABORT":
    return "REJECTED"
elif self.policy == "DISCARD":
    return "DISCARDED"
elif self.policy == "CALLER_RUNS":
    return "CALLER_RAN"
else:   # DISCARD_OLDEST
    dropped = self.queue.popleft()
    self.queue.append(task)
    return f"QUEUED:DROPPED:{dropped.id}"
```

This works, but it has two problems.

**1. One method does too many jobs.** `submitTask()` now decides how to queue a task and also how to reject one. Every new policy means editing this method again, and a small mistake can break normal queuing.

**2. Repeated work.** Every `submitTask()` loops over all threads just to find one idle thread. Every step sorts all threads again.

Let's fix both, one at a time.

---

## Improvement 1: Strategy Pattern for Rejection Policies

The four policies are four different answers to one question: "the queue is full, what do we do with this task?"

That is exactly what the Strategy pattern is for. Python has no interfaces, so we write a small base class with one method:

```python
class RejectionPolicy:
    def reject(self, task, queue):
        raise NotImplementedError
```

Each policy is now its own tiny class: `AbortPolicy`, `DiscardPolicy`, `CallerRunsPolicy` and `DiscardOldestPolicy`.

The executor keeps one policy object and calls `reject()` when the queue is full. It never needs to know which policy it holds.

We pass the queue to `reject()` because `DISCARD_OLDEST` must remove the oldest waiting task. The other policies simply ignore it.

The constructor gets the policy as a plain string, like `"DISCARD_OLDEST"`. A **Simple Factory** (`RejectionPolicyFactory`) turns that string into the right object using a small dict, so this mapping lives in one place.

Adding a fifth policy now means one new class and one new line in the factory. `submitTask()` does not change at all.

This is also how big thread pool libraries handle rejection: the policy is a small object plugged into the pool.

---

## Improvement 2: A Sorted List of Idle Threads

Look at where name order matters:

- `submitTask()` needs the **smallest** idle thread.
- The assign phase gives tasks to idle threads **in name order**.
- Keep-alive removes the **largest** eligible idle thread first.

All three care only about **idle** threads. So instead of scanning and sorting the whole pool, we keep only the idle names, in sorted order.

Python has no built-in sorted set, so we use a normal list and keep it sorted with `bisect.insort()`:

```python
from bisect import insort

self.idleThreads = []                  # always kept sorted
insort(self.idleThreads, threadName)   # adds the name at its sorted position
smallest = self.idleThreads.pop(0)     # removes and returns the smallest name
```

The smallest idle thread is always at index 0. For the largest one, we walk the list from the end with `reversed()`.

With at most 1000 threads both versions are fast, but the sorted list makes the code shorter and clearer. "Smallest idle thread" becomes a single call.

We still keep a dict `workers` to find any thread by name in O(1).

The one rule to remember: a name is in `idleThreads` **only while** that thread is IDLE. Every place that starts a task, finishes a task or removes a thread keeps the dict and the list in sync.

---

## Design Patterns We Did Not Use

**State pattern.** It looks like a perfect match, because the problem talks about "executor states" and "thread states".

But every method asks only one simple question, like "has shutdown started?" or "is this thread idle?". With the State pattern we would write 3 state classes with 6 methods each, and most of those methods would just return `False` or `"REJECTED"`. An enum plus one `if` check says the same thing in one line.

**Command pattern.** Real thread pools treat each task as a command object with a `run()` method. Here a task has no behavior at all. It is only an id and a step counter, so a Command wrapper would add nothing.

---

## Important Classes and Data Structures

| Class / Field | Why we need it |
| --- | --- |
| `Task` | Holds the task id and how many steps it still needs. |
| `Worker` | One thread: its name, state, current task and idle time. Starting and finishing a task happen here, in one place. |
| `RejectionPolicy` and its 4 classes | The Strategy for a full queue. |
| `RejectionPolicyFactory` | Creates the right policy from its name. |
| `workers` (dict) | Finds a thread by name in O(1). Its size is the number of active threads. |
| `idleThreads` (sorted list) | Idle thread names in sorted order. The smallest is at the front and the largest at the end. |
| `queue` (deque) | The FIFO queue. New tasks go to the back, threads take tasks from the front. |

---

## How Each Method Works

### addThread()

Return `False` if shutdown has started, the name is taken, or the pool already has `maximumPoolSize` threads. Otherwise add a new `Worker` and insert its name into `idleThreads`.

We do **not** hand it a queued task here. The next `runOneStep()` does that in its assign phase.

### shutdownThread()

An IDLE thread is removed at once, with no event.

A RUNNING thread becomes STOPPING. It finishes its task and then `runOneStep()` removes it.

### submitTask()

We check the four rules in order:

1. Shutdown has started: return `"REJECTED"`.
2. The queue is empty and a thread is idle: start the task on `idleThreads.pop(0)`.
3. The queue has space: add the task to the back.
4. Otherwise: return `rejectionPolicy.reject(task, queue)`.

Rule 2 checks that the queue is empty. This keeps things fair: a new task never jumps ahead of tasks that are already waiting.

### runOneStep()

We follow the phases from the problem statement:

- **Phases 1 and 2, work and finish.** Every thread with a task does one step of work. When a task reaches 0, add `COMPLETED`. A STOPPING thread is then removed with a `REMOVED` event. Any other thread becomes IDLE.
- **Phase 3, assign.** While there are queued tasks and idle threads, give the oldest task to the smallest idle thread and add `STARTED`.
- **Phase 4, count idle time.** Only threads that were idle for the **whole** step get +1.
- **Phase 5, keep-alive.** Walk the idle threads from the largest name down. Remove each one that has been idle for at least `keepAliveSteps`, as long as the pool has more than `corePoolSize` threads.
- **Phase 6, shutdown cleanup.** If shutdown has started and the queue is empty, remove all idle threads. If nothing is left, the executor becomes `TERMINATED`.
- **Phase 7, return** the events sorted by thread name.

Three small tricks keep this easy.

**A snapshot of idle threads.** At the start of the step we copy `idleThreads` into `idleAtStart`. In phase 4 a thread gets +1 only if it is in this snapshot and is still IDLE. A thread that just finished its task is not in the snapshot, so its idle time correctly stays at 0.

**Work and finish in one loop.** No thread gets a new task during these two phases, so one pass does both. We loop over a copy of the workers, because a STOPPING thread may be removed inside the loop.

**A stable sort for events.** For any one thread, its `COMPLETED` event is always added before its `STARTED` or `REMOVED` event. Python's `sort()` is stable, so sorting by thread name alone keeps that order.

### shutdown()

Return `False` if shutdown has already started.

Also return `False` if tasks are waiting but there are no threads or every thread is STOPPING, because nobody could ever run those tasks.

Otherwise move to `TERMINATED` if the pool is completely empty, else to `SHUTTING_DOWN`. Idle threads stay until the cleanup in the next `runOneStep()`.

### getState()

Add the executor state, then every thread in name order (we sort the dict keys), then the queued task ids from front to back.

---

## Complexity

Let `T` be the number of active threads (at most 1000) and `Q` the number of queued tasks.

- `addThread()`, `shutdownThread()`, `submitTask()`: O(T) in the worst case, because adding or removing a name shifts the rest of the list. With at most 1000 names this is a tiny, fast memory move.
- `runOneStep()`: O(T log T). We visit each thread once and sort the events.
- `getState()`: O(T log T + Q).
- `shutdown()`: O(T).

---

## Python Code

```python
from bisect import insort
from collections import deque
from enum import Enum


class ExecutorState(Enum):
    """The three states of the executor."""
    RUNNING = "RUNNING"
    SHUTTING_DOWN = "SHUTTING_DOWN"
    TERMINATED = "TERMINATED"


class ThreadState(Enum):
    """The three states of a single thread."""
    IDLE = "IDLE"
    RUNNING = "RUNNING"
    STOPPING = "STOPPING"


class Task:
    """A task: its id and how many steps of work it still needs."""

    def __init__(self, taskId, remainingSteps):
        self.id = taskId
        self.remainingSteps = remainingSteps


class Worker:
    """One named thread of the pool."""

    def __init__(self, name):
        self.name = name
        self.state = ThreadState.IDLE
        self.task = None      # current task, None while IDLE
        self.idleSteps = 0    # whole steps spent IDLE

    def startTask(self, task):
        self.task = task
        self.state = ThreadState.RUNNING
        self.idleSteps = 0

    def becomeIdle(self):
        self.task = None
        self.state = ThreadState.IDLE
        self.idleSteps = 0

    def __str__(self):
        """The text used by getState()."""
        if self.state == ThreadState.IDLE:
            return f"THREAD:{self.name}:IDLE:{self.idleSteps}"
        return f"THREAD:{self.name}:{self.state.value}:{self.task.id}:{self.task.remainingSteps}"


class RejectionPolicy:
    """Strategy: decides what happens to a new task when the queue is full.
    It returns the text that submitTask() should return."""

    def reject(self, task, queue):
        raise NotImplementedError


class AbortPolicy(RejectionPolicy):
    """Rejects the new task."""

    def reject(self, task, queue):
        return "REJECTED"


class DiscardPolicy(RejectionPolicy):
    """Silently drops the new task."""

    def reject(self, task, queue):
        return "DISCARDED"


class CallerRunsPolicy(RejectionPolicy):
    """The caller runs the task itself, so it never enters the pool."""

    def reject(self, task, queue):
        return "CALLER_RAN"


class DiscardOldestPolicy(RejectionPolicy):
    """Drops the oldest waiting task to make room for the new one."""

    def reject(self, task, queue):
        dropped = queue.popleft()
        queue.append(task)
        return f"QUEUED:DROPPED:{dropped.id}"


class RejectionPolicyFactory:
    """Simple Factory: creates the right policy object from its name."""

    def create(self, name):
        policies = {
            "ABORT": AbortPolicy,
            "DISCARD": DiscardPolicy,
            "CALLER_RUNS": CallerRunsPolicy,
            "DISCARD_OLDEST": DiscardOldestPolicy,
        }
        return policies[name]()


class ThreadPoolExecutor:
    def __init__(self, corePoolSize, maximumPoolSize, keepAliveSteps, queueCapacity, rejectionPolicy):
        self.corePoolSize = corePoolSize
        self.maximumPoolSize = maximumPoolSize
        self.keepAliveSteps = keepAliveSteps
        self.queueCapacity = queueCapacity
        self.rejectionPolicy = RejectionPolicyFactory().create(rejectionPolicy)
        self.state = ExecutorState.RUNNING

        self.workers = {}        # every active thread, by name
        self.idleThreads = []    # names of IDLE threads, always kept sorted
        self.queue = deque()     # waiting tasks, oldest at the front

    def addThread(self, threadName):
        if self.state != ExecutorState.RUNNING:
            return False
        if threadName in self.workers:
            return False
        if len(self.workers) >= self.maximumPoolSize:
            return False

        self.workers[threadName] = Worker(threadName)
        insort(self.idleThreads, threadName)
        return True

    def shutdownThread(self, threadName):
        if self.state != ExecutorState.RUNNING:
            return False
        worker = self.workers.get(threadName)
        if worker is None or worker.state == ThreadState.STOPPING:
            return False

        if worker.state == ThreadState.IDLE:
            self.removeIdleThread(threadName)      # gone at once, no event
        else:
            worker.state = ThreadState.STOPPING    # finishes its task, then leaves
        return True

    def submitTask(self, taskId, durationSteps):
        if self.state != ExecutorState.RUNNING:
            return "REJECTED"
        task = Task(taskId, durationSteps)

        # nobody is waiting and a thread is free: start the task right away
        if not self.queue and self.idleThreads:
            threadName = self.idleThreads.pop(0)   # smallest idle name
            self.workers[threadName].startTask(task)
            return f"STARTED:{threadName}"
        if len(self.queue) < self.queueCapacity:
            self.queue.append(task)
            return "QUEUED"
        return self.rejectionPolicy.reject(task, self.queue)   # the queue is full

    def runOneStep(self):
        events = []
        if self.state == ExecutorState.TERMINATED:
            return events

        idleAtStart = list(self.idleThreads)

        # Phases 1 and 2, work and finish: every busy thread does one step of work.
        for worker in list(self.workers.values()):
            if worker.task is None:
                continue
            worker.task.remainingSteps -= 1
            if worker.task.remainingSteps > 0:
                continue

            events.append(f"COMPLETED:{worker.name}:{worker.task.id}")
            if worker.state == ThreadState.STOPPING:
                del self.workers[worker.name]
                events.append(f"REMOVED:{worker.name}")
            else:
                worker.becomeIdle()
                insort(self.idleThreads, worker.name)

        # Phase 3, assign: the oldest task goes to the smallest idle thread.
        while self.queue and self.idleThreads:
            threadName = self.idleThreads.pop(0)
            task = self.queue.popleft()
            self.workers[threadName].startTask(task)
            events.append(f"STARTED:{threadName}:{task.id}")

        # Phase 4, count idle time: only threads idle for this whole step get +1.
        for threadName in idleAtStart:
            worker = self.workers[threadName]
            if worker.state == ThreadState.IDLE:
                worker.idleSteps += 1

        # Phase 5, keep-alive: remove threads idle for too long, largest name first.
        for threadName in reversed(list(self.idleThreads)):
            if len(self.workers) <= self.corePoolSize:
                break
            if self.workers[threadName].idleSteps >= self.keepAliveSteps:
                self.removeIdleThread(threadName)
                events.append(f"REMOVED:{threadName}")

        # Phase 6, shutdown cleanup.
        if self.state == ExecutorState.SHUTTING_DOWN:
            if not self.queue:
                for threadName in self.idleThreads:
                    del self.workers[threadName]
                    events.append(f"REMOVED:{threadName}")
                self.idleThreads.clear()
            if not self.workers and not self.queue:
                self.state = ExecutorState.TERMINATED

        # Phase 7, sort by thread name. Python's sort is stable, so the events of
        # one thread keep the order they happened in (COMPLETED comes first).
        events.sort(key=self.threadNameOf)
        return events

    def shutdown(self):
        if self.state != ExecutorState.RUNNING:
            return False
        # waiting tasks need at least one thread that is not STOPPING
        if self.queue and not self.hasUsableThread():
            return False

        if not self.workers and not self.queue:
            self.state = ExecutorState.TERMINATED
        else:
            self.state = ExecutorState.SHUTTING_DOWN
        return True

    def getState(self):
        result = [f"EXECUTOR:{self.state.value}"]
        for name in sorted(self.workers):
            result.append(str(self.workers[name]))
        for task in self.queue:
            result.append(f"QUEUED:{task.id}")
        return result

    def removeIdleThread(self, threadName):
        """Removes an IDLE thread from both the dict and the idle list."""
        del self.workers[threadName]
        self.idleThreads.remove(threadName)

    def hasUsableThread(self):
        """True if some thread is IDLE or RUNNING, so it can still take queued tasks."""
        for worker in self.workers.values():
            if worker.state != ThreadState.STOPPING:
                return True
        return False

    def threadNameOf(self, event):
        """Events look like "TYPE:threadName..." and names never contain ':'."""
        return event.split(":")[1]
```