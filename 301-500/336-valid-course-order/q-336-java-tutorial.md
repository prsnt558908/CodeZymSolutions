# Valid Course Order with Prerequisites in Java

#### Problem Statement
[https://codezym.com/question/336-valid-course-order](https://codezym.com/question/336-valid-course-order)


Think of each course as a node, and each prerequisite as an arrow from the prerequisite to the course. A course is **ready** when every course pointing to it is finished.


The plan is simple: keep taking a ready course. To follow the ordering rule, all ready courses wait in a **min-heap** (`PriorityQueue`), so the smallest one always comes out first.


If we get stuck before taking every course, the prerequisites form a cycle, so we return an empty list.


This is the classic **topological sort** (Kahn's algorithm), with a min-heap in place of a normal queue. We start with a simple brute force and then fix its slow parts one by one.


## Solution 1: Brute Force


Follow the rule word by word. In every round, scan courses `0, 1, 2, ...` and take the first one that is not taken yet and has all its prerequisites taken.


Since we scan from small to large, the first match is always the smallest ready course. If a round finds nothing, we are stuck, and that only happens when there is a cycle.


Assume the strings are already parsed, so `prereqsOf.get(c)` holds the prerequisites of course `c`. For `"2,0"`, course `0` goes into `prereqsOf.get(2)`.


```java
boolean[] taken = new boolean[numCourses];
List<Integer> order = new ArrayList<>();

for (int round = 0; round < numCourses; round++) {
    int pick = -1;
    // smallest course that is not taken and has all prerequisites taken
    for (int c = 0; c < numCourses; c++) {
        if (!taken[c] && allTaken(prereqsOf.get(c), taken)) {
            pick = c;
            break;
        }
    }
    if (pick == -1) {
        return new ArrayList<>(); // stuck, so there is a cycle
    }
    taken[pick] = true;
    order.add(pick);
}
return order;
```


`allTaken()` simply loops over the list and returns `false` if any prerequisite is not taken yet.


### Why is it slow?


There are `n = numCourses` rounds. In each round we may scan every course and read every prerequisite again. That is `O(n × (n + m))` time, where `m` is the number of prerequisites.


With `n = 2,000` and up to about 4 million prerequisites, this can reach billions of steps. Two things are wasted:


1. We re-check the same prerequisites in every round.
2. We scan all courses in every round just to find the smallest ready one.


## Solution 2: Pending Counts + Min-Heap


### Fix 1: Count the pending prerequisites


For every course, keep `pendingCount[c]`: how many of its prerequisites are not finished yet. A course is ready when its count is `0`.


When we finish course `p`, only the courses that need `p` are affected. So we also keep `nextCourses.get(p)`: the list of courses that have `p` as a prerequisite. We reduce each of their counts by one.


For `"2,0"`, we add `2` to `nextCourses.get(0)` and increase `pendingCount[2]`. Now each prerequisite is checked only once, instead of once per round.


### Fix 2: Keep ready courses in a min-heap


Every ready course goes into a min-heap (`PriorityQueue`). `poll()` always gives back the smallest course in `O(log n)` time, so we never scan all courses again.


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


### Java Code


```java
import java.util.*;

public class CourseScheduler {

    public CourseScheduler() {
    }

    /**
     * Returns an order in which all courses can be finished.
     * When many courses are available, the smallest one is taken first.
     * Returns an empty list if the prerequisites contain a cycle.
     */
    public List<Integer> findOrder(int numCourses, List<String> prerequisites) {
        // nextCourses.get(p) = courses that have p as a prerequisite
        List<List<Integer>> nextCourses = new ArrayList<>();
        for (int i = 0; i < numCourses; i++) {
            nextCourses.add(new ArrayList<>());
        }

        // pendingCount[c] = number of prerequisites of c not finished yet
        int[] pendingCount = new int[numCourses];

        // Step 1: read every "course,prerequisite" string
        for (String relation : prerequisites) {
            String[] parts = relation.split(",");
            int course = Integer.parseInt(parts[0].trim());
            int prerequisite = Integer.parseInt(parts[1].trim());

            nextCourses.get(prerequisite).add(course);
            pendingCount[course]++;
        }

        // Step 2: courses with no prerequisites are ready right away
        PriorityQueue<Integer> available = new PriorityQueue<>();
        for (int c = 0; c < numCourses; c++) {
            if (pendingCount[c] == 0) {
                available.add(c);
            }
        }

        // Step 3: keep taking the smallest ready course
        List<Integer> order = new ArrayList<>();
        while (!available.isEmpty()) {
            int course = available.poll();
            order.add(course);

            // this course is done, so its dependents wait for one less course
            for (int nextCourse : nextCourses.get(course)) {
                pendingCount[nextCourse]--;
                if (pendingCount[nextCourse] == 0) {
                    available.add(nextCourse);
                }
            }
        }

        // Step 4: courses that were never taken are stuck in a cycle
        if (order.size() < numCourses) {
            return new ArrayList<>();
        }
        return order;
    }
}
```


### Complexity


| | Brute Force | Pending Counts + Min-Heap |
|---|---|---|
| Time | O(n × (n + m)) | O(m + n log n) |
| Space | O(n + m) | O(n + m) |


Here `n` is the number of courses and `m` is the number of prerequisites. In Solution 2, every prerequisite is handled once, and every course enters and leaves the heap once.