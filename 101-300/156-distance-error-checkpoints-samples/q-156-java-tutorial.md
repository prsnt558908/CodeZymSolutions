# Overall Distance Error Between Checkpoints and Samples in Java

#### Problem Statement
[https://codezym.com/question/156-distance-error-checkpoints-samples](https://codezym.com/question/156-distance-error-checkpoints-samples)


Think of a car moving on a map. At a few moments we know exactly where it was: these are the **checkpoints**. A GPS also sends many noisy readings: these are the **samples**.


For each sample, we find the two checkpoints around it in time. We assume the car moved at a steady speed between them, so we can work out where it should have been. The error is the straight line distance between that spot and the sample.


Both lists are already sorted by time. So we walk through them together with two pointers, just like merging two sorted lists. The checkpoint pointer never moves back, so the whole job is done in a single pass.

---

## Error of One Sample

Let the sample be at time `t`. Let `before` and `after` be two neighbouring checkpoints with `before.time <= t <= after.time`.


**Expected position.** If the car went from `x = 0` to `x = 10` in 10 seconds, then after 5 seconds it was halfway, at `x = 5`. This is called linear interpolation:

```
expectedX = before.x + (after.x - before.x) * (t - before.time) / (after.time - before.time)
expectedY = before.y + (after.y - before.y) * (t - before.time) / (after.time - before.time)
```

The part `(t - before.time) / (after.time - before.time)` is how much of the trip is done. It is `0` at `before` and `1` at `after`.


If `t` exactly matches a checkpoint time, this part is `0` or `1`, so we get that checkpoint's own position. No special case is needed.


**Distance.** The error is the usual straight line distance:

```
dx = expectedX - sample.x
dy = expectedY - sample.y
error = sqrt(dx * dx + dy * dy)
```

We put these two steps in a helper method `distanceError(before, after, sample)`. Both solutions below use it.


### The `Point` Class

Every row is a string like `"15,6,0"`. We split and parse it once into a small `Point` object with `time`, `x` and `y`. After that, the math works on plain numbers and reads cleanly.


We keep the parsed checkpoints in a `List<Point> points`, in the same sorted order as the input. A plain list is all we need, because we only ever look at two neighbours: `points.get(i)` and `points.get(i + 1)`.

---

## Solution 1: Brute Force

For every sample, start from the first checkpoint and check each neighbouring pair until one surrounds the sample.

```java
double totalError = 0;

for (String row : samples) {
    Point sample = new Point(row);

    // check every pair of neighbouring checkpoints, starting from the first one
    for (int i = 0; i + 1 < points.size(); i++) {
        Point before = points.get(i);
        Point after = points.get(i + 1);

        if (before.time <= sample.time && sample.time <= after.time) {
            totalError += distanceError(before, after, sample);
            break; // found the pair, stop scanning
        }
    }
    // no pair found means the sample is outside the range, so it adds 0
}
```

Samples outside the checkpoint range never find a pair, so they are skipped on their own.


**The problem:** every sample scans the checkpoints again from the very start. With `N` checkpoints and `M` samples, that is up to `N * M` checks. For `10^5` of each, that is up to `10^10` steps, which is far too slow.

---

## Solution 2: Two Pointers

### Idea

Samples come in time order. So the pair that fits the next sample is never to the left of the pair that fit the current one.


Restarting the scan from `0` for every sample is wasted work. We just remember where we stopped, in a variable `i`, and continue from there.


Now `i` only moves forward. Over the whole run it moves at most `N` times, no matter how many samples there are.


Binary search for each sample would also work, in `O(M log N)`. But two pointers is simpler and faster here, because both lists are already sorted.


### Steps

For each sample at time `t`:

1. If `t` is before the first checkpoint or after the last one, skip it.
2. While checkpoint `i + 1` is earlier than `t`, move `i` forward.
3. Now `points.get(i).time <= t <= points.get(i + 1).time`. Add `distanceError(points.get(i), points.get(i + 1), sample)` to the total.


Two small facts keep this safe:

- The range check in step 1 makes sure the last checkpoint is at or after `t`. So the while loop always stops before the end of the list.
- `i` only moves past checkpoints that are earlier than the current sample. Later samples are never earlier, so checkpoint `i` is always at or before `t`.


### Dry Run

`checkpoints = ["0,0,0", "10,10,0", "20,10,10"]`

`samples = ["5,5,1", "10,10,0", "15,10,4", "25,0,0"]`

| Sample | `i` | Pair used (times) | Expected | Error |
|---|---|---|---|---|
| `5,5,1` | `0` | `0` and `10` | `(5, 0)` | `1.0` |
| `10,10,0` | `0` | `0` and `10` | `(10, 0)` | `0.0` |
| `15,10,4` | `1` | `10` and `20` | `(10, 5)` | `1.0` |
| `25,0,0` | - | after the last checkpoint, skipped | - | `0` |

Total error = `2.0`.


### Truncating the Answer

The answer keeps at most 2 decimal places, and the rest is cut off, not rounded. So `1.239` becomes `1.23`.


We multiply by `100`, drop the fraction with `Math.floor`, then divide by `100` again.


One catch: a `double` cannot store most decimals exactly. A total that should be `0.8` can end up as `0.7999999999999999`, and flooring that gives a wrong `0.79`. Adding a tiny `1e-6` before flooring fixes it.

```java
Math.floor(value * 100 + 1e-6) / 100
```


### Code

```java
import java.util.*;

/**
 * One parsed "timestamp,x,y" string.
 * We parse each string only once and then work with plain numbers.
 */
class Point {
    double time;
    double x;
    double y;

    Point(String row) {
        String[] parts = row.split(",");
        time = Double.parseDouble(parts[0]);
        x = Double.parseDouble(parts[1]);
        y = Double.parseDouble(parts[2]);
    }
}

public class OverallDistanceError {

    public OverallDistanceError() {
    }

    public double computeOverallErrorMetric(List<String> checkpoints, List<String> samples) {
        // parse every checkpoint only once
        List<Point> points = new ArrayList<>();
        for (String row : checkpoints) {
            points.add(new Point(row));
        }

        double firstTime = points.get(0).time;
        double lastTime = points.get(points.size() - 1).time;

        double totalError = 0;

        // points.get(i) and points.get(i + 1) are the checkpoints around the current sample.
        // i only moves forward, because samples come in time order.
        int i = 0;

        for (String row : samples) {
            Point sample = new Point(row);

            // outside the checkpoint range: ignore it, it adds 0
            if (sample.time < firstTime || sample.time > lastTime) {
                continue;
            }

            // move forward until the right checkpoint is at or after the sample time
            while (points.get(i + 1).time < sample.time) {
                i++;
            }

            Point before = points.get(i);
            Point after = points.get(i + 1);
            totalError += distanceError(before, after, sample);
        }

        return truncate(totalError);
    }

    /**
     * Distance between the sample and the position expected at sample.time.
     * Needs before.time <= sample.time <= after.time.
     */
    double distanceError(Point before, Point after, Point sample) {
        double gap = after.time - before.time;
        double expectedX = before.x + (after.x - before.x) * (sample.time - before.time) / gap;
        double expectedY = before.y + (after.y - before.y) * (sample.time - before.time) / gap;

        double dx = expectedX - sample.x;
        double dy = expectedY - sample.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Keeps only 2 decimal places (no rounding).
     * The tiny 1e-6 fixes values like 0.7999999999 that should be 0.8.
     */
    double truncate(double value) {
        return Math.floor(value * 100 + 1e-6) / 100;
    }
}
```


### Complexity

- **Time:** `O(N + M)`. Every row is parsed once, and `i` moves forward at most `N` times in total.
- **Space:** `O(N)` for the parsed checkpoints. Samples are parsed one at a time.

---

## Key Takeaways

- Linear interpolation is just "how much of the trip is done", applied to the gap between two checkpoints.
- When two lists are sorted by the same key, a pointer that only moves forward replaces repeated scans.
- Truncate only once at the end, and add a tiny `1e-6` to guard against `double` precision errors.