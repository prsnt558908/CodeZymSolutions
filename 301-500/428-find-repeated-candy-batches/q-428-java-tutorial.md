# Find Repeated Candy Batches in Java

#### Problem Statement
[https://codezym.com/question/428-find-repeated-candy-batches](https://codezym.com/question/428-find-repeated-candy-batches)


## Core Idea

Candies of the same flavor that sit next to each other form one batch. Seen this way, the whole list is just a chain of batches placed one after another.

So we only need to walk through the list from left to right, find where each batch starts and where it ends, and keep the batches that have at least 3 candies.

We will first try a simple brute force idea, see where it wastes work, and then fix it with one small change to get a single pass O(n) solution.


## Approach 1: Brute Force

The most direct idea is to stand on every candy and walk right while the flavor stays the same. The place where we stop is the end of that run.

Here `batches` is the answer list and `n` is the number of candies.

```java
for (int i = 0; i < n; i++) {
    // walk right while the flavor stays the same
    int j = i;
    while (j + 1 < n && candyFlavors.get(j + 1).equals(candyFlavors.get(i))) {
        j++;
    }

    // keep the run only if i is the first candy of its batch
    boolean isFirstCandy = (i == 0) || !candyFlavors.get(i - 1).equals(candyFlavors.get(i));
    if (isFirstCandy && j - i + 1 >= 3) {
        batches.add(i + "," + j);
    }
}
```

**Why do we need `isFirstCandy`?**

Take Example 4 with 5 mangoes. Starting from index 1 we get `"1,4"`, and starting from index 2 we get `"2,4"`.

These are only pieces of the real batch `"0,4"`. The problem wants complete batches, so we keep a run only when it starts at index 0 or the candy on its left has a different flavor.


### Problem with this approach

It gives the right answer, but it repeats a lot of work.

For the 5 mangoes, we walk from 0 to 4, then again from 1 to 4, then from 2 to 4, and so on. Most of these walks are thrown away by the `isFirstCandy` check.

If all 1,000 candies have the same flavor, this makes about 500,000 comparisons. The time complexity is O(n²).


## Approach 2: Jump to the Next Batch (Optimal)

**Key observation:** if a batch ends at index `end`, the next batch must start at index `end + 1`.

So we do not need to start a walk from every candy. Once a batch is finished, we jump straight to the first candy of the next batch.

This one change fixes both problems:

- Every walk now starts at the first candy of a batch, so the `isFirstCandy` check is not needed anymore.
- The scan moves over every candy only once.


### How it works

We keep two markers:

- `start`: index of the first candy of the current batch.
- `end`: moves right as long as the next candy has the same flavor as the candy at `start`.

Steps:

1. Begin with `start = 0`.
2. Move `end` right while the next candy has the same flavor.
3. Candies from `start` to `end` form one complete batch. Its size is `end - start + 1`.
4. If the size is 3 or more, add `"start,end"` to the answer.
5. Set `start = end + 1` and repeat until we pass the last candy.

We scan from left to right, so batches are added in increasing order of their start index. No sorting is needed.


### Dry Run on Example 3

| start | end | Flavor | Size | Added to answer |
|-------|-----|--------|------|-----------------|
| 0 | 2 | orange | 3 | `"0,2"` |
| 3 | 3 | lime | 1 | No |
| 4 | 6 | berry | 3 | `"4,6"` |
| 7 | 10 | vanilla | 4 | `"7,10"` |
| 11 | 11 | mint | 1 | No |

Final answer: `["0,2", "4,6", "7,10"]`


### Two loops, but still O(n)

The code has a loop inside a loop, so it may look like O(n²).

But `end` never moves back. After each batch, `start` jumps to `end + 1`, so the next batch continues from where the last one stopped.

Together they move across the list just once, so the total work is O(n).


### Why `ArrayList` and `equals()`

- **`ArrayList` for the answer:** we only add batches at the end, in the same order we find them. Adding at the end of an `ArrayList` is fast (O(1) on average) and it keeps that order, which is exactly the order the problem asks for.
- **`equals()` instead of `==`:** in Java, `==` on strings checks if both are the same object in memory, not if they have the same text. Two `"mint"` strings can be two different objects, so we compare flavors with `equals()`.


## Complexity

- **Time:** O(n), because the scan moves over every candy once.
- **Space:** O(1) extra space, not counting the answer list.


## Final Code

```java
import java.util.*;

public class CandyBatchAnalyzer {

    public CandyBatchAnalyzer() {
    }

    /**
     * Finds every batch of 3 or more consecutive candies with the same flavor.
     * Each batch is returned as "startIndex,endIndex" (both inclusive),
     * in increasing order of startIndex.
     */
    public List<String> findRepeatedBatches(List<String> candyFlavors) {
        List<String> batches = new ArrayList<>();
        int n = candyFlavors.size();

        // start is the index of the first candy of the current batch
        int start = 0;
        while (start < n) {
            String flavor = candyFlavors.get(start);

            // move end forward while the next candy has the same flavor
            int end = start;
            while (end + 1 < n && candyFlavors.get(end + 1).equals(flavor)) {
                end++;
            }

            // candies from start to end form one complete batch
            int batchSize = end - start + 1;
            if (batchSize >= 3) {
                batches.add(start + "," + end);
            }

            // the next batch begins right after this one
            start = end + 1;
        }

        return batches;
    }
}
```