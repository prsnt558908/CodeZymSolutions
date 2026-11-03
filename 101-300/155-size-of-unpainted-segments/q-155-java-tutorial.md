# Size of Unpainted Segments in Java

#### Problem Statement
[https://codezym.com/question/155-size-of-unpainted-segments](https://codezym.com/question/155-size-of-unpainted-segments)


The main observation is that only the start and end values matter. If we cut the number line at every start and end value, we get a small number of **pieces**. Every interval covers whole pieces, so a piece is always either fully painted or fully unpainted.


So instead of painting single numbers, we paint whole pieces. To avoid walking over painted pieces again and again, every piece keeps a pointer that lets us jump over the painted ones.


Each piece gets painted only once, and the whole solution runs in `O(n log n)` time. We will reach it in three small steps, starting from brute force.


---


## Approach 1: Brute Force (Paint Every Cell)

Think of the number line as unit cells. Cell `x` is the segment `[x, x + 1)`.


An interval `[start, end)` is made of the cells `start, start + 1, ..., end - 1`. This is also why `[1, 5)` and `[5, 8)` do not overlap: they share no cell.


For each interval, we walk over all of its cells. If a cell is not painted yet, we count it and paint it.

```java
// painted[x] is true if cell [x, x + 1) is already painted
// maxEnd is the largest end value among all intervals
boolean[] painted = new boolean[maxEnd];

for (int i = 0; i < n; i++) {
    int newlyPainted = 0;
    for (int x = starts[i]; x < ends[i]; x++) {
        if (!painted[x]) {
            painted[x] = true;
            newlyPainted++;
        }
    }
    result.add(newlyPainted);
}
```

This is easy to follow, but it is too slow for the given limits:

- One interval can be `10^8` cells long. With `10^5` intervals, that is up to `10^13` steps.
- The `painted` array needs one slot for every number up to `10^8`.


---


## Approach 2: Cut the Number Line into Pieces

We do not need every single cell.


Sort all start and end values and pick two neighbours `a` and `b` from that sorted list. No interval starts or ends strictly between them. So every interval either covers all of `[a, b)` or none of it.


This means `[a, b)` behaves like one single block. We call it a **piece**. (This trick is often called coordinate compression.)


1. Collect every start and end value, remove duplicates and sort them. Call this list `points`.
2. Piece `k` is `[points[k], points[k + 1])`. Its length is `points[k + 1] - points[k]`.
3. If `indexOf(v)` is the position of `v` in `points`, then an interval `[start, end)` covers exactly the pieces from `indexOf(start)` to `indexOf(end) - 1`.


For `["3,7", "1,4", "6,9"]` we get `points = [1, 3, 4, 6, 7, 9]` and these pieces:

| Piece | Segment | Length |
|:-----:|:-------:|:------:|
| 0 | `[1, 3)` | 2 |
| 1 | `[3, 4)` | 1 |
| 2 | `[4, 6)` | 2 |
| 3 | `[6, 7)` | 1 |
| 4 | `[7, 9)` | 2 |


Now we paint pieces instead of cells:

```java
// painted[k] is true if piece k is already painted
boolean[] painted = new boolean[points.size()];

int startIndex = indexOf.get(start);
int endIndex = indexOf.get(end);

int newlyPainted = 0;
for (int k = startIndex; k < endIndex; k++) {
    if (!painted[k]) {
        painted[k] = true;
        newlyPainted += points.get(k + 1) - points.get(k);
    }
}
```

There are at most `2n` points, so memory is no longer a problem.


But time still is. One interval can cover up to `2n - 1` pieces, and we keep walking over pieces that are already painted. If many big intervals cover the same area, each of them scans the same painted pieces again. That is `O(n^2)` in the worst case.


---


## Approach 3: Jump Over Painted Pieces (Final Solution)

We want to skip painted pieces without visiting them one by one.


So every piece `k` keeps one pointer, `nextUnpainted[k]`:

- If piece `k` is unpainted, it points to itself.
- If piece `k` is painted, it points to some piece on its right.


To find the first unpainted piece starting from `k`, we keep jumping until we reach a piece that points to itself. Every jump skips only painted pieces, so the piece we land on is the first unpainted one.

- At the start, all pieces are unpainted, so `nextUnpainted[k] = k`.
- When we paint piece `k`, we set `nextUnpainted[k] = k + 1`.


Painting an interval is now simple: find the first unpainted piece inside it, add its length, mark it painted and jump to the next unpainted piece. Stop as soon as the jump lands outside the interval.


**The shortcut:** following a long chain of painted pieces again and again would still be slow. So after we find the unpainted piece, we make every piece on the way point directly to it. Next time, it is a single jump. (This trick is called path compression.)


The last index of `points` is not a real piece, because no piece starts there. It is never painted, so it always points to itself and every search stops there at the latest.


### Dry Run

Using the pieces from the table above:

1. `[3, 7)` covers pieces `1` to `3`. All of them are unpainted, so the answer is `1 + 2 + 1 = 4`. Their pointers are now `1 → 2`, `2 → 3` and `3 → 4`.
2. `[1, 4)` covers pieces `0` to `1`. Piece `0` is unpainted, so we add `2`. From piece `1` we jump `1 → 2 → 3 → 4`. Piece `4` is outside this interval, so we stop. The answer is `2`, and the shortcut now points pieces `1`, `2` and `3` directly to `4`.
3. `[6, 9)` covers pieces `3` to `4`. Piece `3` jumps straight to `4`, which is unpainted, so the answer is `2`.


Final result: `[4, 2, 2]`.


### Why These Data Structures

- **`points` (sorted list):** turns a piece index into its real length. We build it with a sorted set, which removes duplicates and keeps the values in order.
- **`indexOf` (map):** gives the index of any start or end value in `O(1)`.
- **`nextUnpainted` (int array):** lets us jump over painted pieces instead of visiting them one by one.


### Complexity

- **Time:** `O(n log n)`. Sorting the points takes `O(n log n)`. Each piece is painted only once, and the shortcut keeps every search fast.
- **Space:** `O(n)` for `points`, `indexOf` and `nextUnpainted`.


### Java Code

```java
import java.util.*;

public class IntervalPainting {

    // Sorted unique start and end values. Piece k is [points[k], points[k + 1])
    List<Integer> points;

    // If piece k is unpainted, nextUnpainted[k] == k.
    // If it is painted, it points to a piece on its right.
    int[] nextUnpainted;

    public IntervalPainting() {
    }

    public List<Integer> amountPainted(List<String> intervals) {
        int n = intervals.size();
        int[] starts = new int[n];
        int[] ends = new int[n];

        // 1. Parse "start,end" and collect every start and end value.
        // A sorted set removes duplicates and keeps the values in order.
        Set<Integer> sortedPoints = new TreeSet<>();
        for (int i = 0; i < n; i++) {
            String[] parts = intervals.get(i).split(",");
            starts[i] = Integer.parseInt(parts[0].trim());
            ends[i] = Integer.parseInt(parts[1].trim());
            sortedPoints.add(starts[i]);
            sortedPoints.add(ends[i]);
        }
        points = new ArrayList<>(sortedPoints);

        // 2. value -> its index in points
        Map<Integer, Integer> indexOf = new HashMap<>();
        for (int k = 0; k < points.size(); k++) {
            indexOf.put(points.get(k), k);
        }

        // 3. Every piece starts unpainted, so it points to itself.
        // The last index is not a real piece. It always points to itself,
        // so every search stops there at the latest.
        nextUnpainted = new int[points.size()];
        for (int k = 0; k < points.size(); k++) {
            nextUnpainted[k] = k;
        }

        // 4. Paint the intervals in the given order
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(paint(indexOf.get(starts[i]), indexOf.get(ends[i])));
        }
        return result;
    }

    // Paints every unpainted piece in [startIndex, endIndex)
    // and returns their total length.
    int paint(int startIndex, int endIndex) {
        int newlyPainted = 0;
        int piece = findUnpainted(startIndex);

        while (piece < endIndex) {
            newlyPainted += points.get(piece + 1) - points.get(piece);
            nextUnpainted[piece] = piece + 1; // painted now, so point to the next piece
            piece = findUnpainted(piece + 1);
        }
        return newlyPainted;
    }

    // Returns the first unpainted piece at index or after it.
    int findUnpainted(int index) {
        // follow the pointers until we reach a piece that points to itself
        int target = index;
        while (nextUnpainted[target] != target) {
            target = nextUnpainted[target];
        }

        // shortcut: point every piece on the way directly to target
        while (nextUnpainted[index] != target) {
            int next = nextUnpainted[index];
            nextUnpainted[index] = target;
            index = next;
        }
        return target;
    }
}
```