# Select Exam Marks Closest to a Target in Java

#### Problem Statement
[https://codezym.com/question/430-exam-marks-closest-to-target](https://codezym.com/question/430-exam-marks-closest-to-target)


The marks are already sorted, and that is the key to this problem. The marks closest to the target always sit next to each other in the list. They form one continuous block of `markCount` marks, which we will call a **window**.


So the real question is simple: **where does the window start?**


We will begin with a brute force that sorts the marks by distance. Then we will use the sorted order to shrink the window with **two pointers**. Finally, we will use **binary search** to jump straight to the correct start.

---

## Understanding the Problem

Each mark is compared with `targetMark` by its distance, `|mark - targetMark|`.

- A smaller distance means a closer mark.
- If two marks have the same distance, the lower mark wins.
- Equal marks from different students are separate entries, and each one counts on its own.


The chosen marks must be returned in non-decreasing order. We will call the number of marks `n`.

---

## Solution 1: Sort by Distance (Brute Force)

### Idea

Forget for a moment that the list is sorted.


Sort all marks by their distance from the target, with the lower mark first on a tie. Take the first `markCount` marks. Then sort them again, because the output must be in non-decreasing order.

```java
List<Integer> marks = new ArrayList<>(sortedMarks);

// closest mark first, and on a tie the lower mark comes first
marks.sort((a, b) -> {
    int distanceA = Math.abs(a - targetMark);
    int distanceB = Math.abs(b - targetMark);
    if (distanceA != distanceB) {
        return distanceA - distanceB;
    }
    return a - b;
});

List<Integer> result = new ArrayList<>(marks.subList(0, markCount));
Collections.sort(result); // the output must be in non-decreasing order
return result;
```

### Why It Is Wasteful

- Sorting takes `O(n log n)` time, and the copied list needs `O(n)` extra space.
- It ignores the most useful fact we are given: the list is **already sorted**.

---

## Key Observation: The Answer Is One Continuous Block

In a sorted list, marks get closer as you walk toward the target and farther as you walk away from it.


So if a mark is picked, every mark between it and the target is at least as close, and gets picked too. The answer never has gaps.


In Example 1, `targetMark = 58` and the answer is the block inside the brackets:

```
14   26   [38   51   64   79]   93
```


Now we only need to find where this window starts.

---

## Solution 2: Two Pointers (Shrink the Window)

### Idea

Start with the whole list as the window. `left` points to the first mark and `right` points to the last mark.


The farthest mark in the window is always at one of its two ends. So compare the two ends and drop the one that is farther from the target. On a tie, drop the right end, because the lower mark wins.


Repeat until the window holds exactly `markCount` marks.


We need no extra data structure here. The two indices `left` and `right` fully describe the window. Since the list is sorted, the window is already in the right order when we return it.

### Walkthrough

Example 2: `sortedMarks = [15, 35, 55, 75, 95]`, `markCount = 3`, `targetMark = 65`.

| Window | Left end (distance) | Right end (distance) | Action |
|---|---|---|---|
| `15 ... 95` | 15 (50) | 95 (30) | drop 15 |
| `35 ... 95` | 35 (30) | 95 (30) | tie, drop 95 |
| `35 ... 75` | | | size is 3, stop |


The answer is `[35, 55, 75]`.

### Code

```java
import java.util.*;

public class ExamMarkSelector {

    public ExamMarkSelector() {
    }

    /**
     * Returns the markCount marks closest to targetMark, in non-decreasing order.
     * Starts with the whole list as the window and drops the farther end
     * until only markCount marks are left.
     */
    public List<Integer> chooseClosestMarks(List<Integer> sortedMarks, int markCount, int targetMark) {
        int left = 0;
        int right = sortedMarks.size() - 1;

        while (right - left + 1 > markCount) {
            int leftDistance = Math.abs(sortedMarks.get(left) - targetMark);
            int rightDistance = Math.abs(sortedMarks.get(right) - targetMark);

            if (leftDistance > rightDistance) {
                left++;  // left end is farther, drop it
            } else {
                right--; // right end is farther, or a tie where the lower mark wins
            }
        }

        // the window is already in non-decreasing order, so just copy it
        return new ArrayList<>(sortedMarks.subList(left, right + 1));
    }
}
```

### Complexity

- Time: `O(n)`. Each step drops one mark, and copying the answer takes `O(markCount)`.
- Space: `O(1)` extra, apart from the returned list.


This is already fast. But when `n` is large and `markCount` is small, we still drop marks one at a time. Binary search skips most of that work.

---

## Solution 3: Binary Search on the Window Start (Optimal)

### Idea

The window can start at any index from `0` to `n - markCount`. Instead of shrinking the window step by step, we binary search for its start.


Pick a start `mid` and compare the window starting at `mid` with the window starting at `mid + 1`. In Example 1, with `mid = 1`:

```
sortedMarks  :  14   26   38   51   64   79   93
window at 1  :      [26   38   51   64]
window at 2  :           [38   51   64   79]
```


Both windows share `38, 51, 64`. The only difference is that `26` leaves and `79` joins.


`26` is `32` away from `58`, but `79` is only `21` away. So the window at `2` is better, and the answer must start after `mid`.

### The Check

We call the mark at `mid + markCount` the **next mark**, because it sits just after the window.

```java
int leftGap = targetMark - sortedMarks.get(mid);              // how far the mark at mid is below the target
int rightGap = sortedMarks.get(mid + markCount) - targetMark; // how far the next mark is above the target
```

- If `leftGap > rightGap`, the mark at `mid` is farther, so the answer starts after `mid`: `low = mid + 1`.
- Otherwise, the mark at `mid` is closer, or equally close and lower. So the answer starts at `mid` or before it: `high = mid`.

### Why Binary Search Works

As `mid` moves right, the mark at `mid` and the mark at `mid + markCount` never get smaller. So `leftGap` never goes up, and `rightGap` never goes down.


This means once `leftGap > rightGap` becomes false, it stays false for every later start. Before that point, moving right is the better choice. After it, staying is the better choice.


Binary search finds this point, and that is where the best window starts.

### Why Not Use `Math.abs`?

Keep the signs. A negative `rightGap` tells us that even the next mark is below the target, so the window must move right. Absolute distances lose this, and they can fail when the same mark repeats.


Take `sortedMarks = [50, 50, 50, 60]`, `markCount = 1`, `targetMark = 60`. The answer is `[60]`.


At `mid = 1`, both marks being compared are `50`. With `Math.abs`, both distances are `10`, so it looks like a tie and the search wrongly moves left. With signs, `rightGap` is `-10`, so the search correctly moves right.

### Walkthrough

Example 1: `sortedMarks = [14, 26, 38, 51, 64, 79, 93]`, `markCount = 4`, `targetMark = 58`. The window can start anywhere from `0` to `3`.

| low | high | mid | mark at mid | mark at mid + 4 | leftGap | rightGap | Action |
|---|---|---|---|---|---|---|---|
| 0 | 3 | 1 | 26 | 79 | 32 | 21 | low = 2 |
| 2 | 3 | 2 | 38 | 93 | 20 | 35 | high = 2 |


The search stops at start `2`, so the answer is `[38, 51, 64, 79]`.

### Code

```java
import java.util.*;

public class ExamMarkSelector {

    public ExamMarkSelector() {
    }

    /**
     * Returns the markCount marks closest to targetMark, in non-decreasing order.
     * The answer is one continuous window of sortedMarks,
     * so we binary search for the index where that window starts.
     */
    public List<Integer> chooseClosestMarks(List<Integer> sortedMarks, int markCount, int targetMark) {
        int low = 0;
        int high = sortedMarks.size() - markCount; // last possible start of the window

        while (low < high) {
            int mid = (low + high) / 2;

            // the windows starting at mid and mid + 1 differ in only two marks:
            // the mark at mid and the mark at mid + markCount.
            // keep the signs, they tell us which side of the target a mark is on
            int leftGap = targetMark - sortedMarks.get(mid);
            int rightGap = sortedMarks.get(mid + markCount) - targetMark;

            if (leftGap > rightGap) {
                low = mid + 1; // the mark at mid is farther, so the window must start later
            } else {
                high = mid;    // the mark at mid is closer, or a tie where the lower mark wins
            }
        }

        // the best window starts at low
        return new ArrayList<>(sortedMarks.subList(low, low + markCount));
    }
}
```

### Complexity

- Time: `O(log(n - markCount) + markCount)`. The first part is the search, and the second part is copying the answer.
- Space: `O(1)` extra, apart from the returned list.

---

## Key Takeaways

- In a sorted list, the closest marks always form one continuous window.
- Two pointers find it by dropping the farther end until `markCount` marks are left.
- Binary search finds its start directly. Compare signed gaps, not absolute distances.