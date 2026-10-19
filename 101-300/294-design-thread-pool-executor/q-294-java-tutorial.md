# Design a Thread Pool Executor in Java

#### Problem Statement
[https://codezym.com/question/294-design-thread-pool-executor](https://codezym.com/question/294-design-thread-pool-executor)

## Core Idea

This problem is a **step by step simulation**. No real threads run. Time moves only when `runOneStep()` is called, and each call just runs the phases given in the problem, in the same order.

The whole pool needs only three things: a **map of threads** by name, a **sorted set of idle thread names**, and a **FIFO queue** of waiting tasks.

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
- To handle a full queue, write an if-else chain inside `submitTask()`.

```java
// inside submitTask(), when the queue is full
if (policy.equals("ABORT")) {
    return "REJECTED";
} else if (policy.equals("DISCARD")) {
    return "DISCARDED";
} else if (policy.equals("CALLER_RUNS")) {
    return "CALLER_RAN";
} else {   // DISCARD_OLDEST
    Task dropped = queue.pollFirst();
    queue.addLast(task);
    return "QUEUED:DROPPED:" + dropped.id;
}
```

This works, but it has two problems.

**1. One method does too many jobs.** `submitTask()` now decides how to queue a task and also how to reject one. Every new policy means editing this method again, and a small mistake can break normal queuing.

**2. Repeated work.** Every `submitTask()` scans all threads just to find one idle thread. Every step sorts all threads again.

Let's fix both, one at a time.

---

## Improvement 1: Strategy Pattern for Rejection Policies

The four policies are four different answers to one question: "the queue is full, what do we do with this task?"

That is exactly what the Strategy pattern is for. We write one small interface:

```java
interface RejectionPolicy {
    String reject(Task task, Deque<Task> queue);
}
```

Each policy is now its own tiny class: `AbortPolicy`, `DiscardPolicy`, `CallerRunsPolicy` and `DiscardOldestPolicy`.

The executor keeps one policy object and calls `reject()` when the queue is full. It never needs to know which policy it holds.

We pass the queue to `reject()` because `DISCARD_OLDEST` must remove the oldest waiting task. The other policies simply ignore it.

The constructor gets the policy as a plain string, like `"DISCARD_OLDEST"`. A **Simple Factory** (`RejectionPolicyFactory`) turns that string into the right object, so this mapping lives in one place.

Adding a fifth policy now means one new class and one new line in the factory. `submitTask()` does not change at all.

Real Java works the same way: `java.util.concurrent.ThreadPoolExecutor` plugs in a `RejectedExecutionHandler` with these same four policies.

---

## Improvement 2: A Sorted Set of Idle Threads

Look at where name order matters:

- `submitTask()` needs the **smallest** idle thread.
- The assign phase gives tasks to idle threads **in name order**.
- Keep-alive removes the **largest** eligible idle thread first.

All three care only about **idle** threads. So instead of scanning and sorting the whole pool, we keep the idle names in a sorted set:

```java
TreeSet<String> idleThreads = new TreeSet<>();
```

Now the smallest idle thread is just `idleThreads.pollFirst()`, which also removes it from the set. For the largest one, we walk the set from the end.

With at most 1000 threads both versions are fast, but the sorted set makes the code shorter and clearer. "Smallest idle thread" becomes a single call.

We still keep a `HashMap<String, Worker>` to find any thread by name in O(1).

The one rule to remember: a name is in `idleThreads` **only while** that thread is IDLE. Every place that starts a task, finishes a task or removes a thread keeps the map and the set in sync.

---

## Design Patterns We Did Not Use

**State pattern.** It looks like a perfect match, because the problem talks about "executor states" and "thread states".

But every method asks only one simple question, like "has shutdown started?" or "is this thread idle?". With the State pattern we would write 3 state classes with 6 methods each, and most of those methods would just return `false` or `"REJECTED"`. An enum plus one `if` check says the same thing in one line.

**Command pattern.** Real thread pools treat each task as a command object, like Java's `Runnable`. Here a task has no behavior at all. It is only an id and a step counter, so a Command wrapper would add nothing.

---

## Important Classes and Data Structures

| Class / Field | Why we need it |
| --- | --- |
| `Task` | Holds the task id and how many steps it still needs. |
| `Worker` | One thread: its name, state, current task and idle time. Starting and finishing a task happen here, in one place. |
| `RejectionPolicy` and its 4 classes | The Strategy for a full queue. |
| `RejectionPolicyFactory` | Creates the right policy from its name. |
| `Map<String, Worker> workers` | Finds a thread by name in O(1). Its size is the number of active threads. |
| `TreeSet<String> idleThreads` | Idle thread names in sorted order. Gives the smallest and the largest idle thread quickly. |
| `Deque<Task> queue` | The FIFO queue. New tasks go to the back, threads take tasks from the front. |

---

## How Each Method Works

### addThread()

Return `false` if shutdown has started, the name is taken, or the pool already has `maximumPoolSize` threads. Otherwise add a new `Worker` and put its name in `idleThreads`.

We do **not** hand it a queued task here. The next `runOneStep()` does that in its assign phase.

### shutdownThread()

An IDLE thread is removed at once, with no event.

A RUNNING thread becomes STOPPING. It finishes its task and then `runOneStep()` removes it.

### submitTask()

We check the four rules in order:

1. Shutdown has started: return `"REJECTED"`.
2. The queue is empty and a thread is idle: start the task on `idleThreads.pollFirst()`.
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

**A stable sort for events.** For any one thread, its `COMPLETED` event is always added before its `STARTED` or `REMOVED` event. Java's `List.sort()` is stable, so sorting by thread name alone keeps that order.

### shutdown()

Return `false` if shutdown has already started.

Also return `false` if tasks are waiting but there are no threads or every thread is STOPPING, because nobody could ever run those tasks.

Otherwise move to `TERMINATED` if the pool is completely empty, else to `SHUTTING_DOWN`. Idle threads stay until the cleanup in the next `runOneStep()`.

### getState()

Add the executor state, then every thread in name order (we sort the map keys), then the queued task ids from front to back.

---

## Complexity

Let `T` be the number of active threads (at most 1000) and `Q` the number of queued tasks.

- `addThread()`, `shutdownThread()`, `submitTask()`: O(log T).
- `runOneStep()`: O(T log T). We visit each thread once and sort the events.
- `getState()`: O(T log T + Q).
- `shutdown()`: O(T).

---

## Java Code

```java
import java.util.*;

/** The three states of the executor. */
enum ExecutorState {
    RUNNING, SHUTTING_DOWN, TERMINATED
}

/** The three states of a single thread. */
enum ThreadState {
    IDLE, RUNNING, STOPPING
}

/** A task: its id and how many steps of work it still needs. */
class Task {
    String id;
    int remainingSteps;

    Task(String id, int remainingSteps) {
        this.id = id;
        this.remainingSteps = remainingSteps;
    }
}

/** One named thread of the pool. */
class Worker {
    String name;
    ThreadState state = ThreadState.IDLE;
    Task task = null;    // current task, null while IDLE
    int idleSteps = 0;   // whole steps spent IDLE

    Worker(String name) {
        this.name = name;
    }

    void startTask(Task task) {
        this.task = task;
        state = ThreadState.RUNNING;
        idleSteps = 0;
    }

    void becomeIdle() {
        task = null;
        state = ThreadState.IDLE;
        idleSteps = 0;
    }

    /** The text used by getState(). */
    public String toString() {
        if (state == ThreadState.IDLE) {
            return "THREAD:" + name + ":IDLE:" + idleSteps;
        }
        return "THREAD:" + name + ":" + state + ":" + task.id + ":" + task.remainingSteps;
    }
}

/**
 * Strategy: decides what happens to a new task when the queue is full.
 * It returns the text that submitTask() should return.
 */
interface RejectionPolicy {
    String reject(Task task, Deque<Task> queue);
}

/** Rejects the new task. */
class AbortPolicy implements RejectionPolicy {
    public String reject(Task task, Deque<Task> queue) {
        return "REJECTED";
    }
}

/** Silently drops the new task. */
class DiscardPolicy implements RejectionPolicy {
    public String reject(Task task, Deque<Task> queue) {
        return "DISCARDED";
    }
}

/** The caller runs the task itself, so it never enters the pool. */
class CallerRunsPolicy implements RejectionPolicy {
    public String reject(Task task, Deque<Task> queue) {
        return "CALLER_RAN";
    }
}

/** Drops the oldest waiting task to make room for the new one. */
class DiscardOldestPolicy implements RejectionPolicy {
    public String reject(Task task, Deque<Task> queue) {
        Task dropped = queue.pollFirst();
        queue.addLast(task);
        return "QUEUED:DROPPED:" + dropped.id;
    }
}

/** Simple Factory: creates the right policy object from its name. */
class RejectionPolicyFactory {
    RejectionPolicy create(String name) {
        switch (name) {
            case "DISCARD":
                return new DiscardPolicy();
            case "CALLER_RUNS":
                return new CallerRunsPolicy();
            case "DISCARD_OLDEST":
                return new DiscardOldestPolicy();
            default:
                return new AbortPolicy();   // "ABORT"
        }
    }
}

public class ThreadPoolExecutor {
    int corePoolSize;
    int maximumPoolSize;
    int keepAliveSteps;
    int queueCapacity;
    RejectionPolicy rejectionPolicy;
    ExecutorState state = ExecutorState.RUNNING;

    Map<String, Worker> workers = new HashMap<>();   // every active thread, by name
    TreeSet<String> idleThreads = new TreeSet<>();   // names of IDLE threads, in name order
    Deque<Task> queue = new ArrayDeque<>();          // waiting tasks, oldest at the front

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, int keepAliveSteps,
                              int queueCapacity, String rejectionPolicy) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveSteps = keepAliveSteps;
        this.queueCapacity = queueCapacity;
        this.rejectionPolicy = new RejectionPolicyFactory().create(rejectionPolicy);
    }

    public boolean addThread(String threadName) {
        if (state != ExecutorState.RUNNING) return false;
        if (workers.containsKey(threadName)) return false;
        if (workers.size() >= maximumPoolSize) return false;

        workers.put(threadName, new Worker(threadName));
        idleThreads.add(threadName);
        return true;
    }

    public boolean shutdownThread(String threadName) {
        if (state != ExecutorState.RUNNING) return false;
        Worker worker = workers.get(threadName);
        if (worker == null || worker.state == ThreadState.STOPPING) return false;

        if (worker.state == ThreadState.IDLE) {
            removeIdleThread(threadName);           // gone at once, no event
        } else {
            worker.state = ThreadState.STOPPING;   // finishes its task, then leaves
        }
        return true;
    }

    public String submitTask(String taskId, int durationSteps) {
        if (state != ExecutorState.RUNNING) return "REJECTED";
        Task task = new Task(taskId, durationSteps);

        // nobody is waiting and a thread is free: start the task right away
        if (queue.isEmpty() && !idleThreads.isEmpty()) {
            String threadName = idleThreads.pollFirst();   // smallest idle name
            workers.get(threadName).startTask(task);
            return "STARTED:" + threadName;
        }
        if (queue.size() < queueCapacity) {
            queue.addLast(task);
            return "QUEUED";
        }
        return rejectionPolicy.reject(task, queue);   // the queue is full
    }

    public List<String> runOneStep() {
        List<String> events = new ArrayList<>();
        if (state == ExecutorState.TERMINATED) return events;

        List<String> idleAtStart = new ArrayList<>(idleThreads);

        // Phases 1 and 2, work and finish: every busy thread does one step of work.
        for (Worker worker : new ArrayList<>(workers.values())) {
            if (worker.task == null) continue;
            worker.task.remainingSteps--;
            if (worker.task.remainingSteps > 0) continue;

            events.add("COMPLETED:" + worker.name + ":" + worker.task.id);
            if (worker.state == ThreadState.STOPPING) {
                workers.remove(worker.name);
                events.add("REMOVED:" + worker.name);
            } else {
                worker.becomeIdle();
                idleThreads.add(worker.name);
            }
        }

        // Phase 3, assign: the oldest task goes to the smallest idle thread.
        while (!queue.isEmpty() && !idleThreads.isEmpty()) {
            String threadName = idleThreads.pollFirst();
            Task task = queue.pollFirst();
            workers.get(threadName).startTask(task);
            events.add("STARTED:" + threadName + ":" + task.id);
        }

        // Phase 4, count idle time: only threads idle for this whole step get +1.
        for (String threadName : idleAtStart) {
            Worker worker = workers.get(threadName);
            if (worker.state == ThreadState.IDLE) worker.idleSteps++;
        }

        // Phase 5, keep-alive: remove threads idle for too long, largest name first.
        List<String> idleNames = new ArrayList<>(idleThreads);
        for (int i = idleNames.size() - 1; i >= 0 && workers.size() > corePoolSize; i--) {
            String threadName = idleNames.get(i);
            if (workers.get(threadName).idleSteps >= keepAliveSteps) {
                removeIdleThread(threadName);
                events.add("REMOVED:" + threadName);
            }
        }

        // Phase 6, shutdown cleanup.
        if (state == ExecutorState.SHUTTING_DOWN) {
            if (queue.isEmpty()) {
                for (String threadName : idleThreads) {
                    workers.remove(threadName);
                    events.add("REMOVED:" + threadName);
                }
                idleThreads.clear();
            }
            if (workers.isEmpty() && queue.isEmpty()) state = ExecutorState.TERMINATED;
        }

        // Phase 7, sort by thread name. The sort is stable, so the events of
        // one thread keep the order they happened in (COMPLETED comes first).
        events.sort((a, b) -> threadNameOf(a).compareTo(threadNameOf(b)));
        return events;
    }

    public boolean shutdown() {
        if (state != ExecutorState.RUNNING) return false;
        // waiting tasks need at least one thread that is not STOPPING
        if (!queue.isEmpty() && !hasUsableThread()) return false;

        if (workers.isEmpty() && queue.isEmpty()) {
            state = ExecutorState.TERMINATED;
        } else {
            state = ExecutorState.SHUTTING_DOWN;
        }
        return true;
    }

    public List<String> getState() {
        List<String> result = new ArrayList<>();
        result.add("EXECUTOR:" + state);

        List<String> names = new ArrayList<>(workers.keySet());
        Collections.sort(names);
        for (String name : names) result.add(workers.get(name).toString());

        for (Task task : queue) result.add("QUEUED:" + task.id);
        return result;
    }

    /** Removes an IDLE thread from both the map and the idle set. */
    void removeIdleThread(String threadName) {
        workers.remove(threadName);
        idleThreads.remove(threadName);
    }

    /** True if some thread is IDLE or RUNNING, so it can still take queued tasks. */
    boolean hasUsableThread() {
        for (Worker worker : workers.values()) {
            if (worker.state != ThreadState.STOPPING) return true;
        }
        return false;
    }

    /** Events look like "TYPE:threadName..." and names never contain ':'. */
    String threadNameOf(String event) {
        return event.split(":")[1];
    }
}
```