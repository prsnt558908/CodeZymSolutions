# Valid Course Order with Prerequisites in Python

#### Problem Statement
[https://codezym.com/question/336-valid-course-order](https://codezym.com/question/336-valid-course-order)


Think of each course as a node, and each prerequisite as an arrow from the prerequisite to the course. A course is **ready** when every course pointing to it is finished.


The plan is simple: keep taking a ready course. To follow the ordering rule, all ready courses wait in a **min-heap** (`heapq`), so the smallest one always comes out first.


If we get stuck before taking every course, the prerequisites form a cycle, so we return an empty list.


This is the classic **topological sort** (Kahn's algorithm), with a min-heap in place of a normal queue. We start with a simple brute force and then fix its slow parts one by one.


## Solution 1: Brute Force


Follow the rule word by word. In every round, scan courses `0, 1, 2, ...` and take the first one that is not taken yet and has all its prerequisites taken.


Since we scan from small to large, the first match is always the smallest ready course. If a round finds nothing, we are stuck, and that only happens when there is a cycle.


Assume the strings are already parsed, so `prereqsOf[c]` holds the prerequisites of course `c`. For `"2,0"`, course `0` goes into `prereqsOf[2]`.


```python
taken = [False] * numCourses
order = []

for _ in range(numCourses):
    pick = -1
    # smallest course that is not taken and has all prerequisites taken
    for c in range(numCourses):
        if not taken[c] and all(taken[p] for p in prereqsOf[c]):
            pick = c
            break
    if pick == -1:
        return []  # stuck, so there is a cycle
    taken[pick] = True
    order.append(pick)

return order
```


### Why is it slow?


There are `n = numCourses` rounds. In each round we may scan every course and read every prerequisite again. That is `O(n × (n + m))` time, where `m` is the number of prerequisites.


With `n = 2,000` and up to about 4 million prerequisites, this can reach billions of steps. Two things are wasted:


1. We re-check the same prerequisites in every round.
2. We scan all courses in every round just to find the smallest ready one.


## Solution 2: Pending Counts + Min-Heap


### Fix 1: Count the pending prerequisites


For every course, keep `pendingCount[c]`: how many of its prerequisites are not finished yet. A course is ready when its count is `0`.


When we finish course `p`, only the courses that need `p` are affected. So we also keep `nextCourses[p]`: the list of courses that have `p` as a prerequisite. We reduce each of their counts by one.


For `"2,0"`, we add `2` to `nextCourses[0]` and increase `pendingCount[2]`. Now each prerequisite is checked only once, instead of once per round.


### Fix 2: Keep ready courses in a min-heap


Every ready course goes into a min-heap (`heapq`). `heappop()` always gives back the smallest course in `O(log n)` time, so we never scan all courses again.


Why not a normal queue, like in regular Kahn's algorithm? A queue returns courses in the order they became ready, not smallest first. The dry run below shows where that breaks the rule.


### Detecting a cycle


Courses in a cycle wait for each other, so their counts never reach `0` and they never enter the heap.


So if the final order has fewer than `numCourses` courses, there is a cycle and we return an empty list.


In Example 2, courses `0`, `1` and `2` wait for each other. Only course `3` is taken, so we return `[]`.


### Dry Run (Example 4)


`numCourses = 5`, prerequisites `"3,0"`, `"3,2"`, `"4,1"`. So the arrows are `0 → 3`, `2 → 3` and `1 → 4`.


| Step | Heap | Take | Newly ready |
|---|---|---|---|
| 1 | {0, 1, 2} | 0 | none (3 still needs 2) |
| 2 | {1, 2} | 1 | 4 |
| 3 | {2, 4} | 2 | 3 |
| 4 | {3, 4} | 3 | none |
| 5 | {4} | 4 | none |


Final order: `[0, 1, 2, 3, 4]`.


Look at step 4. Course `4` became ready before course `3`, but the heap still gives `3` first because it is smaller. A normal queue would return `4` first and break the ordering rule.


### Python Code


```python
import heapq
from typing import List


class CourseScheduler:

    def __init__(self):
        pass

    def findOrder(self, numCourses: int, prerequisites: List[str]) -> List[int]:
        """
        Returns an order in which all courses can be finished.
        When many courses are available, the smallest one is taken first.
        Returns an empty list if the prerequisites contain a cycle.
        """
        # nextCourses[p] = courses that have p as a prerequisite
        nextCourses = [[] for _ in range(numCourses)]

        # pendingCount[c] = number of prerequisites of c not finished yet
        pendingCount = [0] * numCourses

        # Step 1: read every "course,prerequisite" string
        for relation in prerequisites:
            parts = relation.split(",")
            course = int(parts[0])
            prerequisite = int(parts[1])

            nextCourses[prerequisite].append(course)
            pendingCount[course] += 1

        # Step 2: courses with no prerequisites are ready right away
        available = []
        for c in range(numCourses):
            if pendingCount[c] == 0:
                heapq.heappush(available, c)

        # Step 3: keep taking the smallest ready course
        order = []
        while available:
            course = heapq.heappop(available)
            order.append(course)

            # this course is done, so its dependents wait for one less course
            for nextCourse in nextCourses[course]:
                pendingCount[nextCourse] -= 1
                if pendingCount[nextCourse] == 0:
                    heapq.heappush(available, nextCourse)

        # Step 4: courses that were never taken are stuck in a cycle
        if len(order) < numCourses:
            return []
        return order
```


### Complexity


| | Brute Force | Pending Counts + Min-Heap |
|---|---|---|
| Time | O(n × (n + m)) | O(m + n log n) |
| Space | O(n + m) | O(n + m) |


Here `n` is the number of courses and `m` is the number of prerequisites. In Solution 2, every prerequisite is handled once, and every course enters and leaves the heap once.