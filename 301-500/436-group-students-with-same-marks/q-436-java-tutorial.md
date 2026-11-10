# Group Students with Same Marks in Java

#### Problem Statement
[https://codezym.com/question/436-group-students-with-same-marks](https://codezym.com/question/436-group-students-with-same-marks)


## Core Idea

The key is to stop thinking about single operations and look at **ranges** instead. Each mark covers the range from its first position to its last position, and every student inside that range must end up with the same mark.

Overlapping ranges join into independent **blocks**. In each block, we keep the most common mark and change every other mark into it.

We first find the blocks with the classic merge intervals idea. Then we improve it to a single left to right scan with two hash maps, which runs in `O(n)` time.


## Key Observations

Remember the main rule: one operation changes **every** student with a given mark, never just some of them.

### 1. A mark locks its whole range

Look at `[80, 60, 80]`.

Both 80s always change together, so they always have the same value. For them to stand together, the student between them must also have that value.

So 60 must end with the same value as 80. And since all 60s change together, every 60 in the row is now tied to 80.

**Rule:** take the range from a mark's first position to its last position. Every mark inside this range must end with the same value as this mark.

### 2. Overlapping ranges form blocks

If two ranges overlap, one range contains a student of the other mark. So by the rule above, both marks must end with the same value.

This works like a chain. If A is tied to B and B is tied to C, then all three are tied.

Look at Example 2:

```
index :    0    1    2    3    4    5    6    7
marks :   50   70   50   90   70   40   90   40
50    :   [----------]
70    :        [---------------]
90    :                  [---------------]
40    :                            [----------]
```

50 overlaps 70, 70 overlaps 90 and 90 overlaps 40. So all four marks are tied, and the whole row is one block.

A mark never appears in two different blocks. So each block can be solved on its own.

### 3. Cost of one block

All students in a block must end with one value. So at most one mark of the block can stay unchanged. If two marks stayed unchanged, they would end with different values.

Every other mark must be changed at least once, and that costs its full count.

So the best plan is to keep the most common mark and change every other mark directly into it.

`cost of a block = block size - count of its most common mark`

In Example 2, the block has 8 students and the most common mark appears 2 times. So the cost is `8 - 2 = 6`.

The final answer is the sum of the costs of all blocks.


## Solution 1: Merge Intervals

Each mark gives us an interval `[first position, last position]`. Finding the blocks is now the classic merge intervals problem.

1. Store the first and last position of every mark in two maps.
2. Put the intervals in a list and sort them by start.
3. Merge overlapping intervals into blocks.
4. For each block, count its marks and add `block size - highest count`.

```java
// Step 1: first and last position of every mark
Map<Integer, Integer> firstIndex = new HashMap<>();
Map<Integer, Integer> lastIndex = new HashMap<>();
for (int i = 0; i < marks.size(); i++) {
    int mark = marks.get(i);
    firstIndex.putIfAbsent(mark, i);
    lastIndex.put(mark, i);
}

// Step 2: one interval per mark, sorted by start
List<int[]> intervals = new ArrayList<>();
for (int mark : firstIndex.keySet()) {
    intervals.add(new int[]{firstIndex.get(mark), lastIndex.get(mark)});
}
intervals.sort((a, b) -> a[0] - b[0]);

// Step 3: merge overlapping intervals into blocks
int totalChanges = 0;
int blockStart = intervals.get(0)[0];
int blockEnd = intervals.get(0)[1];
for (int[] interval : intervals) {
    if (interval[0] > blockEnd) {
        // the current block is complete, a new one begins
        totalChanges += changesInBlock(marks, blockStart, blockEnd);
        blockStart = interval[0];
    }
    blockEnd = Math.max(blockEnd, interval[1]);
}
totalChanges += changesInBlock(marks, blockStart, blockEnd);
```

```java
// Step 4: keep the most common mark of the block, change the rest
int changesInBlock(List<Integer> marks, int start, int end) {
    Map<Integer, Integer> count = new HashMap<>();
    int maxCount = 0;
    for (int i = start; i <= end; i++) {
        int mark = marks.get(i);
        count.put(mark, count.getOrDefault(mark, 0) + 1);
        maxCount = Math.max(maxCount, count.get(mark));
    }
    return (end - start + 1) - maxCount;
}
```

### Problems with this solution

- Sorting the intervals takes `O(n log n)` time. The problem expects `O(n)`.
- We count the marks again inside every block. This is extra work and extra code.


## Solution 2: Single Left to Right Scan

Two small observations fix both problems.

**No sorting needed.** When we walk the row from left to right, we meet each mark for the first time at the start of its interval. So the intervals already come to us sorted by start.

**No re-counting needed.** All students with the same mark are inside the same block. So the total count of a mark in the whole row is also its count inside its block. We can count every mark once, before the scan.

### How the scan works

For the current block we keep:

- `blockStart`: the position where the block began.
- `blockEnd`: the farthest last position of any mark seen so far in this block.
- `maxFrequency`: the highest count of any mark seen so far in this block.

At every position `i`, we extend `blockEnd` to the last position of the current mark, if it reaches farther. We also update `maxFrequency` with the count of the current mark.

When `i == blockEnd`, every mark seen in this block ends at or before `i`. Nothing ties this block to the students after it, so the block is complete.

We add `blockSize - maxFrequency` to the answer and start a new block at `i + 1`.

### Why two hash maps?

- `lastIndex` tells us in `O(1)` how far the current mark reaches. This is how we find where a block ends.
- `frequency` tells us in `O(1)` how many students have the current mark. This lets us find the most common mark of a block without counting again.

### Dry Run

`marks = [60, 40, 60, 90, 75, 90, 30, 30]`

```
lastIndex : {60: 2, 40: 1, 90: 5, 75: 4, 30: 7}
frequency : {60: 2, 40: 1, 90: 2, 75: 1, 30: 2}
```

| i | mark | blockEnd | maxFrequency | What happens |
|---|------|----------|--------------|--------------|
| 0 | 60 | 2 | 2 | continue |
| 1 | 40 | 2 | 2 | continue |
| 2 | 60 | 2 | 2 | block `[0, 2]` ends, add `3 - 2 = 1` |
| 3 | 90 | 5 | 2 | continue |
| 4 | 75 | 5 | 2 | continue |
| 5 | 90 | 5 | 2 | block `[3, 5]` ends, add `3 - 2 = 1` |
| 6 | 30 | 7 | 2 | continue |
| 7 | 30 | 7 | 2 | block `[6, 7]` ends, add `2 - 2 = 0` |

Answer = `1 + 1 + 0 = 2`

### Code

```java
import java.util.*;

public class StudentMarksOrganizer {

    public StudentMarksOrganizer() {
    }

    /**
     * Returns the minimum number of student records that must be changed
     * so that all students with the same marks stand together.
     */
    public int minimumMarkChanges(List<Integer> marks) {
        int n = marks.size();

        // Pass 1: last position and total count of every mark
        Map<Integer, Integer> lastIndex = new HashMap<>();
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int mark = marks.get(i);
            lastIndex.put(mark, i);
            frequency.put(mark, frequency.getOrDefault(mark, 0) + 1);
        }

        // Pass 2: walk left to right and cut the row into blocks
        int totalChanges = 0;
        int blockStart = 0;
        int blockEnd = 0;      // farthest last position of any mark seen in this block
        int maxFrequency = 0;  // count of the most common mark in this block

        for (int i = 0; i < n; i++) {
            int mark = marks.get(i);
            blockEnd = Math.max(blockEnd, lastIndex.get(mark));
            maxFrequency = Math.max(maxFrequency, frequency.get(mark));

            // no mark seen so far reaches beyond i, so the block is complete
            if (i == blockEnd) {
                int blockSize = blockEnd - blockStart + 1;
                // keep the most common mark, change everyone else
                totalChanges += blockSize - maxFrequency;

                // the next block starts right after this one
                blockStart = i + 1;
                maxFrequency = 0;
            }
        }
        return totalChanges;
    }
}
```

### Complexity

- **Time:** `O(n)` expected. We make two passes over the row, and each map operation takes `O(1)` on average.
- **Space:** `O(n)` for the two maps.