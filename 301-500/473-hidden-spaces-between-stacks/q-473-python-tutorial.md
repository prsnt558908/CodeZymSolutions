# Hidden Spaces Between Warehouse Stacks in Python

#### Problem Statement
[https://codezym.com/question/473-hidden-spaces-between-stacks](https://codezym.com/question/473-hidden-spaces-between-stacks)

The key is to find how high each position is blocked on both sides. The shorter of the tallest stacks on its left and right limits the hidden spaces above it. Two pointers let us count these spaces in `O(n)` time with `O(1)` extra space.


## Count the spaces above one position

Each stack starts at the floor without gaps. An empty space counts only when there are boxes on both sides at the same height.

Let `left_max` be the tallest stack from the left end through the current position. Let `right_max` be the tallest stack from the current position through the right end.

The number of hidden spaces above this position is:

```text
min(left_max, right_max) - current_height
```

Both ranges include the current stack, so this value cannot be negative. At either end of the row, it is zero.

![Stacks with heights 5, 1, 3, 1, and 4, with seven hidden empty spaces highlighted](https://d3jug84e56mcss.cloudfront.net/question_images/0/q-472-img-1-sm-50-80.jpg)


## Start with a simple solution

For every position, scan left and right to find the two tallest stacks. Apply the formula and add the result.

This takes `O(n²)` time because we scan the same positions repeatedly. With up to 20,000 stacks, we should avoid that repeated work.

We could store all left and right maximums in two lists. That reduces the time to `O(n)`, but uses `O(n)` extra space. Two pointers avoid those extra lists.


## Improve with two pointers

Move inward from both ends. Keep `left_max` and `right_max` as the tallest stacks already seen from each end. Both start at zero.

At each step:

1. If `left_max <= right_max`, read the next stack from the left. Update `left_max`, then add `left_max - height`.
2. Otherwise, read the next stack from the right. Update `right_max`, then add `right_max - height`.
3. Continue until every position has been counted once.

The input list stores the stack heights. The integer variables `left` and `right` point to the first and last unprocessed positions. Moving only one pointer per step ensures that no position is counted twice.

We only read the input list. All counting variables stay inside the method, so repeated calls are independent.


## Why choosing the smaller maximum works

Suppose `left_max <= right_max`. If the next left stack is shorter than `left_max`, we already have a right boundary tall enough to close every empty space up to `left_max`. Its contribution is exactly `left_max - height`.

If that stack is taller than `left_max`, it becomes the new maximum and adds zero. We update the maximum before subtracting, so the contribution never becomes negative.

The same reasoning applies from the right when `right_max < left_max`. Every step counts one position exactly, so the final sum is correct.


## Example

For `stackSizes = [5, 1, 3, 1, 4]`, the first stack sets `left_max` to `5`. The last stack then sets `right_max` to `4`.

Since `right_max` is smaller, we continue from the right. The next three stack heights are `1`, `3`, and `1`. They add `4 - 1 = 3`, `4 - 3 = 1`, and `4 - 1 = 3` spaces.

The result is `3 + 1 + 3 = 7`.

Single stacks, equal heights, and steadily increasing or decreasing heights all give zero. Empty positions between tall stacks are counted normally.


## Complexity

Let `n` be the number of positions in `stackSizes`.

- **Time:** `O(n)`. Each position is read once.
- **Extra space:** `O(1)`. Only two indices and a few counters are needed.


## Python solution

```python
class HiddenSpacesBetweenStacks:
    def __init__(self):
        pass

    def countHiddenSpaces(self, stackSizes):
        left = 0
        right = len(stackSizes) - 1
        left_max = 0
        right_max = 0
        hidden_spaces = 0

        # Each step finishes one position from the side with the smaller maximum.
        while left <= right:
            if left_max <= right_max:
                height = stackSizes[left]
                left_max = max(left_max, height)
                hidden_spaces += left_max - height
                left += 1
            else:
                height = stackSizes[right]
                right_max = max(right_max, height)
                hidden_spaces += right_max - height
                right -= 1

        return hidden_spaces
```
