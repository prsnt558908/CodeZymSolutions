# Hidden Spaces Between Warehouse Stacks in Java

#### Problem Statement
[https://codezym.com/question/473-hidden-spaces-between-stacks](https://codezym.com/question/473-hidden-spaces-between-stacks)

The key is to find how high each position is blocked on both sides. The shorter of the tallest stacks on its left and right limits the hidden spaces above it. Two pointers let us count these spaces in `O(n)` time with `O(1)` extra space.


## Count the spaces above one position

Each stack starts at the floor without gaps. An empty space counts only when there are boxes on both sides at the same height.

Let `leftMax` be the tallest stack from the left end through the current position. Let `rightMax` be the tallest stack from the current position through the right end.

The number of hidden spaces above this position is:

```text
min(leftMax, rightMax) - currentHeight
```

Both ranges include the current stack, so this value cannot be negative. At either end of the row, it is zero.

![Stacks with heights 5, 1, 3, 1, and 4, with seven hidden empty spaces highlighted](https://d3jug84e56mcss.cloudfront.net/question_images/0/q-472-img-1-sm-50-80.jpg)


## Start with a simple solution

For every position, scan left and right to find the two tallest stacks. Apply the formula and add the result.

This takes `O(n²)` time because we scan the same positions repeatedly. With up to 20,000 stacks, we should avoid that repeated work.

We could store all left and right maximums in two lists. That reduces the time to `O(n)`, but uses `O(n)` extra space. Two pointers avoid those extra lists.


## Improve with two pointers

Move inward from both ends. Keep `leftMax` and `rightMax` as the tallest stacks already seen from each end. Both start at zero.

At each step:

1. If `leftMax <= rightMax`, read the next stack from the left. Update `leftMax`, then add `leftMax - height`.
2. Otherwise, read the next stack from the right. Update `rightMax`, then add `rightMax - height`.
3. Continue until every position has been counted once.

The input is a `List<Integer>`. Two `ListIterator<Integer>` objects act as pointers, one moving forward and the other backward. They do not copy the input, and keep traversal linear for both `ArrayList` and `LinkedList`.

The `remaining` counter prevents the two iterators from counting the same position twice. All counting variables stay inside the method, so repeated calls are independent.


## Why choosing the smaller maximum works

Suppose `leftMax <= rightMax`. If the next left stack is shorter than `leftMax`, we already have a right boundary tall enough to close every empty space up to `leftMax`. Its contribution is exactly `leftMax - height`.

If that stack is taller than `leftMax`, it becomes the new maximum and adds zero. We update the maximum before subtracting, so the contribution never becomes negative.

The same reasoning applies from the right when `rightMax < leftMax`. Every step counts one position exactly, so the final sum is correct.


## Example

For `stackSizes = [5, 1, 3, 1, 4]`, the first stack sets `leftMax` to `5`. The last stack then sets `rightMax` to `4`.

Since `rightMax` is smaller, we continue from the right. The next three stack heights are `1`, `3`, and `1`. They add `4 - 1 = 3`, `4 - 3 = 1`, and `4 - 1 = 3` spaces.

The result is `3 + 1 + 3 = 7`.

Single stacks, equal heights, and steadily increasing or decreasing heights all give zero. Empty positions between tall stacks are counted normally.


## Complexity

Let `n` be the number of positions in `stackSizes`.

- **Time:** `O(n)`. Each position is read once.
- **Extra space:** `O(1)`. Only two iterators and a few counters are needed.

The largest possible answer is `(20,000 - 2) * 100,000 = 1,999,800,000`, which fits in a Java `int`.


## Java solution

```java
import java.util.List;
import java.util.ListIterator;

public class HiddenSpacesBetweenStacks {

    public HiddenSpacesBetweenStacks() {
    }

    public int countHiddenSpaces(List<Integer> stackSizes) {
        ListIterator<Integer> leftIterator = stackSizes.listIterator();
        ListIterator<Integer> rightIterator =
                stackSizes.listIterator(stackSizes.size());

        int leftMax = 0;
        int rightMax = 0;
        int hiddenSpaces = 0;
        int remaining = stackSizes.size();

        // Each step finishes one position from the side with the smaller maximum.
        while (remaining > 0) {
            if (leftMax <= rightMax) {
                int height = leftIterator.next();
                leftMax = Math.max(leftMax, height);
                hiddenSpaces += leftMax - height;
            } else {
                int height = rightIterator.previous();
                rightMax = Math.max(rightMax, height);
                hiddenSpaces += rightMax - height;
            }

            // Stop before the two iterators count any position twice.
            remaining--;
        }

        return hiddenSpaces;
    }
}
```
