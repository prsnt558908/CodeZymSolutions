# Find Missing Numbers In Range in Java

#### Problem Statement
[https://codezym.com/question/189-find-missing-numbers-in-range](https://codezym.com/question/189-find-missing-numbers-in-range)


Missing numbers can only hide in the gaps between the numbers we already have. Since `nums` is sorted, we can walk through it once and compare each number with the one just before it.


If two neighbors are more than 1 apart, every number between them is missing, and that whole stretch is one answer range.


The two ends of `[lower, upper]` look like special cases, but a small trick removes them. We pretend that `lower - 1` sits just before the list and `upper + 1` sits just after it. Now the start and the end are ordinary gaps too.


We will first write a brute force that checks every number one by one, see why it is too slow, and then turn it into a single pass over `nums`.


## Picture the Problem

Example 1: `nums = [0, 1, 4, 8, 10]`, `lower = 0`, `upper = 12`. The missing numbers are in brackets.

```
0  1  [2  3]  4  [5  6  7]  8  [9]  10  [11  12]
```

Each bracket group is one answer: `["2,3", "5,7", "9,9", "11,12"]`. A group with just one number, like `9`, is still written as `"9,9"`.


## Approach 1: Brute Force (Check Every Number)

Walk from `lower` to `upper` and check every number: is it in `nums` or not?


To answer that quickly, we first copy `nums` into a `HashSet`. A set tells us if a number is present in O(1) time, while searching the list every time would cost O(n).


When we reach a missing number, we keep walking while numbers stay missing. The first and the last number of that walk form one range.

```java
public List<String> findMissingRanges(List<Integer> nums, int lower, int upper) {
    // A set answers "is x in nums?" in O(1) time
    Set<Integer> present = new HashSet<>(nums);
    List<String> result = new ArrayList<>();

    int x = lower;
    while (x <= upper) {
        if (present.contains(x)) {
            x++;
            continue;
        }
        // x is missing, so walk forward while numbers stay missing
        int start = x;
        while (x <= upper && !present.contains(x)) {
            x++;
        }
        result.add(start + "," + (x - 1));
    }
    return result;
}
```


### Why This Is Too Slow

The loop runs once for every number in the range, not once for every number in `nums`.


The range can go from -1,000,000,000 to 1,000,000,000. That is about 2 billion numbers to check, while `nums` holds at most 10,000 values. Almost all the time is spent stepping through long missing stretches, one number at a time.


Now look at what the inner loop really does: it walks until it reaches the next present number, or the end of the range. Since `nums` is sorted, we already know that next number. It is simply the next element of the list.


So we can jump over the whole stretch in one step, and we don't need the set anymore.


## Approach 2: Optimal (Jump Over the Gaps)

### Step 1: Only Look at Neighbors

Keep a variable `prev`, the last present number we have seen. When the next number `num` comes, every number strictly between them is missing:

```
missing range = prev + 1  to  num - 1
```

If `prev + 1` is bigger than `num - 1`, the two numbers are side by side (like `0` and `1`) and nothing is missing.


### Step 2: Two Pretend Numbers for the Edges

Start with `prev = lower - 1`. Then a gap before the first number begins at `lower`, which is exactly what we want.


After the loop, pretend one more number `upper + 1` comes. Then a gap after the last number stops at `upper`.


This also handles an empty `nums`. The only gap is then `lower` to `upper`.


Both pretend numbers fit safely in an `int`. All values are between -10^9 and 10^9, and an `int` goes up to about 2.1 billion in both directions, so adding or subtracting 1 never overflows.


### Step 3: Skip Numbers Outside the Range

Because `nums` is sorted:

- A number smaller than `lower` is skipped. It can't create a gap inside the range.
- The first number bigger than `upper` ends the loop, because every number after it is bigger too.


### Dry Run

Example 5: `nums = [-5, -2, 0, 1, 3, 8, 20]`, `lower = 0`, `upper = 5`. We start with `prev = -1`.

| num | What happens | Gap `prev + 1` to `num - 1` | Added |
|---|---|---|---|
| -5 | smaller than `lower`, skip | | |
| -2 | smaller than `lower`, skip | | |
| 0 | check gap, then `prev = 0` | 0 to -1 (empty) | |
| 1 | check gap, then `prev = 1` | 1 to 0 (empty) | |
| 3 | check gap, then `prev = 3` | 2 to 2 | `"2,2"` |
| 8 | bigger than `upper`, stop | | |
| 6 (pretend `upper + 1`) | last check after the loop | 4 to 5 | `"4,5"` |

Final answer: `["2,2", "4,5"]`.


## Complexity

| | Brute Force | Optimal |
|---|---|---|
| Time | O(upper - lower + n) | O(n) |
| Extra Space | O(n) for the set | O(1) |

Here `n` is the size of `nums`. The output list is not counted as extra space.


## Java Code

The gap check runs in two places, inside the loop and once after it. So a small `addRange()` helper keeps the "is it empty?" check and the `"start,end"` format in one place.


Ranges are found from left to right, so `result` is already sorted by start. No sorting is needed.

```java
import java.util.*;

public class FindMissingRanges {

    public FindMissingRanges() {
    }

    /**
     * Returns all missing ranges inside [lower, upper] as "start,end" strings.
     * Ranges are sorted by start. Values outside [lower, upper] are ignored.
     */
    public List<String> findMissingRanges(List<Integer> nums, int lower, int upper) {
        List<String> result = new ArrayList<>();

        // Pretend lower - 1 is present, so a gap at the start begins at lower.
        int prev = lower - 1;

        for (int num : nums) {
            // Too small: it can't create a gap inside the range.
            if (num < lower) {
                continue;
            }
            // Too big: nums is sorted, so every later value is too big.
            if (num > upper) {
                break;
            }
            // Every number strictly between prev and num is missing.
            addRange(result, prev + 1, num - 1);
            prev = num;
        }

        // Pretend upper + 1 is present, so a gap at the end stops at upper.
        addRange(result, prev + 1, upper);

        return result;
    }

    /**
     * Adds "start,end" to result if the range has at least one number.
     */
    void addRange(List<String> result, int start, int end) {
        if (start <= end) {
            result.add(start + "," + end);
        }
    }
}
```