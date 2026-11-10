# Detect First Timed Out Job from Logs in Python

#### Problem Statement
[https://codezym.com/question/138-detect-first-timed-out-job-logs](https://codezym.com/question/138-detect-first-timed-out-job-logs)

---

We read the logs from left to right and keep track of the jobs that are still running. Before handling each log, we check if any running job has now been running for more than `timeoutThreshold`. The first time we find one, that is our answer.


Checking every running job at every log works, but it is slow. The key fact is that every job has the same time limit, so jobs time out in the same order they started. So we keep the started jobs in a queue in start order and only look at the front. A dict tells us which of them are still running. This makes the whole scan `O(n)`.

---

## Understanding the Problem

Each log looks like `"jobId,timestamp,eventType"`, and the logs are already sorted by time. Three details matter.


**1. One check covers both timeout rules.**

A job times out if its END log shows `end - start > timeoutThreshold`, or if it is still running at some log time `t` with `t - start > timeoutThreshold`.


When we reach a job's END log, the job is still running. So if we check all running jobs *before* handling each log, the "still running" rule already catches every END timeout. END logs need no separate check.


**2. Timeouts are only noticed at log times.**

In Example 4, job `1` starts at time `1` with limit `4`. It is already over the limit at time `6`, but there is no log at time `6`. We only notice it at the next log, at time `7`.


So a job with no END log only counts as timed out if a later log shows it is over the limit.


**3. Ties go to the smaller id, not the earlier start.**

Several jobs can be found timed out at the same log. With limit `5`:

```
"9,1,START", "3,2,START", "4,10,START"
```

At time `10`, job `9` has run for `9` and job `3` has run for `8`. Both are found at the same log, so the answer is `3`, the smaller id, even though job `9` started first.

---

## Approach 1: Brute Force

Keep a dict of running jobs, from job id to start time. Before each log, look at every running job and pick the smallest id that has timed out.

```python
# job id -> start time of every running job
runningJobs = {}

for log in logs:
    parts = log.split(",")
    jobId = int(parts[0])
    timestamp = int(parts[1])

    # before handling this log, check every running job
    timedOut = [runningId for runningId, start in runningJobs.items()
                if timestamp - start > timeoutThreshold]
    if timedOut:
        return min(timedOut)

    if parts[2] == "START":
        runningJobs[jobId] = timestamp
    else:
        runningJobs.pop(jobId, None)

return -1
```

**Why is it slow?**

If many jobs keep running, every log looks at all of them. With `200,000` logs, that can be around `20 billion` checks. This is `O(n²)`, far too slow.

---

## Approach 2: Watch Only the Oldest Running Job

### The key observation

A job that started earlier has been running longer. So at any log, the timed out jobs are always the oldest few running jobs.


If the oldest running job is still within the limit, every other running job is too, and we can stop looking.


### A queue keeps jobs in start order

The logs are sorted by time, so START logs arrive in start order. If we add each started job to the back of a queue, the oldest job is always at the front.


### Removing finished jobs lazily

When a job ends, it can be anywhere in the queue, and removing from the middle of a queue is slow.


So we don't touch the queue. We only remove the job from a dict of running jobs. Later, when this finished job reaches the front of the queue, we see it is not running anymore and drop it.


Each job enters the queue once and leaves it at most once, so this cleanup costs `O(n)` in total.


### Checking at each log

Before handling a log at time `t`, keep looking at the front of the queue:

1. **The job already finished:** drop it and look at the next one.
2. **The job has timed out** (`t - start > timeoutThreshold`): remember its id if it is the smallest so far, drop it, and look at the next one. More jobs may have timed out at this same log.
3. **The job is still within the limit:** stop. Every newer job is within the limit too.

If we remembered an id, return it. Otherwise handle the log: START adds the job to the dict and the queue, END only removes it from the dict.


### Why we need each piece

- **`startOrder`, a `deque`:** holds started jobs in start order, so the oldest one is always at the front. Removing from the front of a `deque` is `O(1)`, while `list.pop(0)` is `O(n)`.
- **`runningJobs`, a `dict`:** maps a job id to its current run. It tells us in `O(1)` whether a queue entry is still running.
- **`Job` class:** one run of a job, with its id and start time.


A queue entry is still running only if `runningJobs` holds that exact `Job` object, which we check with `is`. This also handles a job id that starts again after it ended: the dict points to the new run, so the old entry is ignored.


### Dry Run (Example 1, limit `5`)

| Log | Check before handling the log | After handling the log |
|---|---|---|
| `1,1,START` | Queue is empty. | Queue: job 1 |
| `2,2,START` | Job 1: `2 - 1 = 1`, fine. Stop. | Queue: job 1, job 2 |
| `1,4,END` | Job 1: `4 - 1 = 3`, fine. Stop. | Job 1 removed from the dict only |
| `3,8,START` | Job 1 is not running, drop it. Job 2: `8 - 2 = 6 > 5`, timed out. Queue is now empty. | Return `2` |

---

## Code

```python
from collections import deque
from typing import List


class Job:
    """One run of a job: its id and the time its START log appeared."""

    def __init__(self, jobId: int, startTime: int):
        self.id = jobId
        self.startTime = startTime


class DetectFirstTimedOutJobFromLogs:

    def __init__(self):
        # job id -> its current run, only for jobs that are running right now
        self.runningJobs = {}

        # every started job in the order it started, so the oldest start is at the front.
        # finished jobs are not removed right away, they are dropped at the front.
        self.startOrder = deque()

        self.timeoutThreshold = 0

    def firstTimedOutJobId(self, logs: List[str], timeoutThreshold: int) -> int:
        """
        Scans the logs in order and returns the id of the first job
        that is detected to have timed out, or -1 if no job times out.
        """
        # start fresh for every call
        self.timeoutThreshold = timeoutThreshold
        self.runningJobs = {}
        self.startOrder = deque()

        for log in logs:
            parts = log.split(",")
            jobId = int(parts[0])
            timestamp = int(parts[1])
            eventType = parts[2].strip()

            # Step 1: before handling this log, check if any running job has timed out.
            # This also covers a job whose END log is this log, it is still running here.
            timedOutJobId = self.findTimedOutJobId(timestamp)
            if timedOutJobId != -1:
                return timedOutJobId

            # Step 2: handle the log itself
            if eventType == "START":
                job = Job(jobId, timestamp)
                self.runningJobs[jobId] = job
                self.startOrder.append(job)
            else:
                # only remove it from the dict, its queue entry is dropped later
                self.runningJobs.pop(jobId, None)

        return -1

    def findTimedOutJobId(self, currentTime: int) -> int:
        """
        Returns the smallest id among the running jobs that have timed out
        at currentTime. Returns -1 if no running job has timed out yet.
        """
        smallestId = -1

        while self.startOrder:
            oldest = self.startOrder[0]

            if not self.isRunning(oldest):
                # this job already finished, drop it
                self.startOrder.popleft()
            elif currentTime - oldest.startTime > self.timeoutThreshold:
                # timed out, remember the smaller id and check the next oldest job too
                if smallestId == -1 or oldest.id < smallestId:
                    smallestId = oldest.id
                self.startOrder.popleft()
            else:
                # the oldest running job is still within the limit,
                # so every job that started after it is also within the limit
                break

        return smallestId

    def isRunning(self, job: Job) -> bool:
        """
        A queue entry is still running only if the dict still holds this exact entry.
        If the job ended, or the same id started again, the dict holds something else.
        """
        return self.runningJobs.get(job.id) is job
```

---

## Complexity

- **Time:** `O(n)`. Each log is read once, and each job enters and leaves the queue at most once.
- **Space:** `O(n)` for the dict and the queue.

---

## Key Takeaways

- When every item has the same time limit, items expire in the order they arrived. A plain queue is enough, no sorting needed.
- To delete from the middle of a queue, remove the item from a dict instead and drop it later when it reaches the front.
- Read tie rules carefully. Here ties go to the smaller id, not the earlier start.