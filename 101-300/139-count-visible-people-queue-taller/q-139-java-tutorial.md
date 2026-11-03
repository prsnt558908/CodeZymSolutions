# Count Visible People in Queue with Taller Observer Rule in Java

#### Problem Statement
[https://codezym.com/question/139-count-visible-people-queue-taller](https://codezym.com/question/139-count-visible-people-queue-taller)


Two people can see each other if at least one of them is taller than everyone standing between them.

The trick is to look in one direction at a time. When a person looks right, they see everyone up to the **first taller person**. After that, they only see people who are taller than everyone before them, like the steps of a **staircase**.

A **monotonic stack** (a stack where heights only grow from top to bottom) gives us both of these in a single right to left scan. For the left side, we reverse the queue and run the same scan again. The answer is the sum of both sides.

This takes the solution from O(n²) down to O(n).


## Solution 1: Brute Force

Just follow the rule for every pair of people.

For person `i`, walk to the right one person at a time. Keep `tallestBetween`, the tallest height seen so far between `i` and the current person `j`. Person `i` sees `j` if `max(heights[i], heights[j])` is bigger than `tallestBetween`.

Then do the same walk to the left.

```java
for (int i = 0; i < n; i++) {
    int count = 0;

    // look right
    int tallestBetween = 0;
    for (int j = i + 1; j < n; j++) {
        if (Math.max(heights.get(i), heights.get(j)) > tallestBetween) {
            count++;
        }
        // person j now stands between i and the next person
        tallestBetween = Math.max(tallestBetween, heights.get(j));
    }

    // look left: same loop, with j going from i - 1 down to 0

    answer.add(count);
}
```

This is correct but slow. It checks every pair, so it takes O(n²) time. With `n` up to 100,000, that is around 10 billion checks.


## Solution 2: Monotonic Stack (Optimal)

### Key Observation

Pick one direction and walk away from person `i`. Call the first person who is taller than `i` the **first taller person**.

**Group 1: everyone up to the first taller person.** Everyone between `i` and the first taller person is shorter than `i`, so nobody blocks the view. Person `i` sees all of them, and the first taller person too.

**Group 2: people after the first taller person.** Now someone taller than `i` stands in between. So `i` sees a person only if that person is taller than everyone in between. These people form a **staircase**: each one is taller than the one before.

Let's check this on Example 1: `heights = [1, 10, 6, 7, 9, 2, 4, 5]`. Person `7` (height 5) looks left.

- 4 and 2 are shorter than 5. Visible.
- 9 is the first taller person. Visible.
- 7 and 6 are hidden behind 9.
- 10 is taller than everyone in between. Visible, it is a staircase step.
- 1 is hidden behind 10.

Person `7` sees 3 + 1 = 4 people, which matches `answer[7] = 4`.

So for each person and each direction, we only need two things:

1. Where the first taller person stands.
2. How many staircase steps come after them.


### Using a Stack

Let's count the right side first. Scan the queue from **right to left** and keep a stack of positions.

Why a stack? The person we need to compare with first is always the closest one on the right. That is exactly the one we pushed last.

For each person `i`:

**1. Pop shorter people.** Pop everyone shorter than person `i`. None of them can be the first taller person or a staircase step for `i`. They can't be one for anyone further left either, since person `i` stands in front of them and is taller.

**2. Read the count from the stack.** After popping, the stack holds the first taller person on top, and the staircase steps below it.

Why? A person stays in the stack only if nobody from `i` up to them is taller than them. The first taller person and the staircase steps are exactly those people.

- If the stack is empty, nobody on the right is taller. Person `i` sees all `n - 1 - i` people on the right.
- Otherwise, the count is `(firstTaller - i) + (stack.size() - 1)`.
  - `firstTaller - i` is Group 1: everyone from `i + 1` up to the first taller person.
  - `stack.size() - 1` is Group 2: the staircase steps below the top.

**3. Push person `i`.** Person `i` may be the first taller person or a staircase step for someone on the left.


### Looking Left

Looking left in the queue is the same as looking right in the reversed queue. So we reverse the list and run the same scan. Person `i` stands at position `n - 1 - i` in the reversed queue.

The final answer for person `i` is its right count plus its left count.


### Dry Run

Example 2: `heights = [10, 6, 8, 5, 11, 9]`, right side only. The stack is shown as heights, top first.

| i | Height | Popped | Stack after popping | Right count |
|---|---|---|---|---|
| 5 | 9 | none | empty | 6 - 1 - 5 = 0 |
| 4 | 11 | 9 | empty | 6 - 1 - 4 = 1 |
| 3 | 5 | none | 11 | (4 - 3) + 0 = 1 |
| 2 | 8 | 5 | 11 | (4 - 2) + 0 = 2 |
| 1 | 6 | none | 8, 11 | (2 - 1) + 1 = 2 |
| 0 | 10 | 6, 8 | 11 | (4 - 0) + 0 = 4 |

Look at person `1` (height 6). The stack holds 8 and 11. So person `1` sees 8 (the first taller person) and 11 (a staircase step). Heights 5 and 9 stay hidden.

The right counts are `[4, 2, 2, 1, 1, 0]`. The left scan gives `[0, 1, 2, 2, 4, 1]`. Adding them gives `[4, 3, 4, 3, 5, 1]`, which is the expected answer.


### Code

```java
import java.util.*;

public class CountVisiblePeople {

    public CountVisiblePeople() {
    }

    public List<Integer> countVisiblePeople(List<Integer> heights) {
        int n = heights.size();

        // how many people each person sees on the right
        int[] rightCounts = countVisibleOnRight(heights);

        // looking left is the same as looking right in the reversed queue
        List<Integer> reversedHeights = new ArrayList<>(heights);
        Collections.reverse(reversedHeights);
        int[] leftCounts = countVisibleOnRight(reversedHeights);

        List<Integer> answer = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // person i stands at position (n - 1 - i) in the reversed queue
            answer.add(rightCounts[i] + leftCounts[n - 1 - i]);
        }
        return answer;
    }

    /**
     * Returns counts where counts[i] is the number of people
     * that person i can see on the right side.
     * Scans from right to left using a monotonic stack.
     */
    int[] countVisibleOnRight(List<Integer> heights) {
        int n = heights.size();
        int[] counts = new int[n];

        // positions of people, heights increase from top to bottom
        Deque<Integer> stack = new ArrayDeque<>();

        for (int i = n - 1; i >= 0; i--) {
            int height = heights.get(i);

            // shorter people can never be the first taller person
            // or a staircase step for person i or anyone further left
            while (!stack.isEmpty() && heights.get(stack.peek()) < height) {
                stack.pop();
            }

            if (stack.isEmpty()) {
                // nobody on the right is taller, so person i sees all of them
                counts[i] = n - 1 - i;
            } else {
                // the stack now holds the staircase, first taller person on top
                int firstTaller = stack.peek();
                int groupOne = firstTaller - i;     // everyone up to the first taller person
                int groupTwo = stack.size() - 1;    // staircase steps after the first taller person
                counts[i] = groupOne + groupTwo;
            }

            stack.push(i);
        }
        return counts;
    }
}
```


### Complexity

- **Time:** O(n). In each scan, every person is pushed once and popped at most once.
- **Space:** O(n) for the stack, the reversed list and the count arrays.