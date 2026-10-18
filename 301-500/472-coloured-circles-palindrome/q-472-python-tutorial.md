# Maximum Coloured Circles in a Palindrome Pattern in Python

#### Problem Statement
[https://codezym.com/question/472-coloured-circles-palindrome](https://codezym.com/question/472-coloured-circles-palindrome)


The whole solution comes from one simple question: what do we do with the two circles at the ends of the row?

If they have the same colour, we keep both and solve the smaller row between them. If their colours are different, one of them must be erased. So we try both options and keep the better one.

This rule gives a correct but slow recursive solution, because the same parts of the row get solved again and again. We fix that by saving the answer for every part of the row in a table. This trick is called **dynamic programming**. At the end, we shrink the table down to a single row to save memory.


## Understanding the Problem

We can erase any circles, but we can never move them. So the kept circles stay in their original order.

The kept circles must read the same from left to right and from right to left.

For example, in `"rgybgr"` we can erase the blue circle `b`. The kept circles `"rgygr"` read the same both ways, so the answer is 5.

A single circle always reads the same both ways. So the answer is never less than 1.


## The Key Idea: Look at the Two Ends

Think of any part of the row, from circle `left` to circle `right`. Only two things can happen at its ends.

**Case 1: the two end circles have the same colour.**

We keep both. They wrap around the best pattern of the middle part, from `left + 1` to `right - 1`. This adds 2 circles to the answer of the middle part.

Why is keeping both always right? Look at a best pattern for this part:

- If it skips both end circles, we could add them around it and keep 2 more. So a best pattern never skips both.

- If it keeps the left end but skips the right end, its last circle has the same colour as the left end. That is also the colour of the right end. So we can keep the right end circle in its place instead. The same works the other way around.

So there is always a best pattern that keeps both end circles.

**Case 2: the two end circles have different colours.**

If we kept both, they would be the first and last circles of the pattern. A palindrome must start and end with the same colour, so that is impossible. At least one of them must be erased.

So we try both choices:

- erase the left circle and solve the part from `left + 1` to `right`
- erase the right circle and solve the part from `left` to `right - 1`

The answer is the bigger of the two.

**The smallest parts** are easy. One circle gives 1. An empty part gives 0.

Here is the rule in action for `"rgybgr"`:

| Part | End circles | What we do |
|---|---|---|
| `rgybgr` | `r` and `r` | Same colour. Keep both: 2 + answer for `gybg` |
| `gybg` | `g` and `g` | Same colour. Keep both: 2 + answer for `yb` |
| `yb` | `y` and `b` | Different colours. Best of `b` and `y`, which is 1 |

So the answer is 2 + 2 + 1 = **5**.


## Solution 1: Brute Force (Plain Recursion)

The rule above turns straight into a recursive method. `best(left, right)` returns the most circles we can keep from circle `left` to circle `right`.

```python
class ColouredCirclesPalindrome:
    def __init__(self):
        self.colours = ""

    def maxCirclesKept(self, circleColours: str) -> int:
        self.colours = circleColours
        return self.best(0, len(circleColours) - 1)

    def best(self, left: int, right: int) -> int:
        """Most circles we can keep from circle left to circle right."""
        if left > right:
            return 0  # empty part
        if left == right:
            return 1  # one circle alone is a palindrome
        if self.colours[left] == self.colours[right]:
            return self.best(left + 1, right - 1) + 2  # keep both end circles
        # erase one end circle, keep the better result
        return max(self.best(left + 1, right), self.best(left, right - 1))
```


### Why is it slow?

When the end circles differ, the method calls itself twice. Each of those calls can split into two again. In the worst case, the number of calls grows like 2^n, where n is the number of circles. For 1,000 circles, this would never finish.

The waste is easy to spot. In `"abcd"`, erasing `a` and then `d` leaves `"bc"`. Erasing `d` and then `a` also leaves `"bc"`. So `"bc"` is solved twice, and its own smaller parts even more often.

But a part is fixed by just two numbers: `left` and `right`. With 1,000 circles, there are only about 500,000 different parts. If we solve each part once and remember its answer, the work becomes small.


## Solution 2: Save Every Answer in a Table

We store the answer for every part in a 2D table, built as a list of lists:

`best[left][right]` = the most circles we can keep from circle `left` to circle `right`.

**Why a list of lists?** Every part of the row is named by two numbers, `left` and `right`. A list of lists works like a 2D table. It gives each part its own cell, and reading a saved answer takes constant time.

**In what order do we fill it?** To fill `best[left][right]`, we may need three answers:

- `best[left + 1][right - 1]`, the middle part
- `best[left + 1][right]`, the part with the left circle erased
- `best[left][right - 1]`, the part with the right circle erased

The first two are in row `left + 1`. The third is in the same row, one cell to the left. So we fill the rows from the last circle back to the first, and each row from left to right. This way, every answer we need is ready before we use it.

One small detail: when two neighbouring circles match, the middle part is empty. Its cell, `best[left + 1][left]`, is never filled, so it keeps the 0 we filled the table with. That is exactly the right answer for an empty part.

```python
class ColouredCirclesPalindrome:
    def __init__(self):
        pass

    def maxCirclesKept(self, circleColours: str) -> int:
        colours = circleColours
        n = len(colours)

        # best[left][right] = most circles we can keep from circle left to circle right
        best = [[0] * n for _ in range(n)]

        # fill rows from the last circle back to the first
        for left in range(n - 1, -1, -1):
            best[left][left] = 1  # one circle alone is a palindrome

            for right in range(left + 1, n):
                if colours[left] == colours[right]:
                    # keep both end circles around the best middle part
                    best[left][right] = best[left + 1][right - 1] + 2
                else:
                    # erase one end circle, keep the better result
                    erase_left = best[left + 1][right]
                    erase_right = best[left][right - 1]
                    best[left][right] = max(erase_left, erase_right)

        return best[0][n - 1]
```

**Time:** O(n^2). We fill about n^2 / 2 cells, and each cell takes constant time.

**Memory:** O(n^2) for the table.


## Solution 3: Keep Only One Row (Final Solution)

Look at the fill order again. Row `left` only needs row `left + 1` and itself. Older rows are never used again.

So we do not need the whole table. One list of size n is enough, and we overwrite it as `left` moves down.

`best[right]` = the most circles we can keep from circle `left` to circle `right`, for the current `left`.

When we are about to update `best[right]`:

- `best[right]` still holds the old row's value. That is the answer for `left + 1` to `right`, the part with the left circle erased.
- `best[right - 1]` already holds the new row's value. That is the answer for `left` to `right - 1`, the part with the right circle erased.

We also need the middle part, from `left + 1` to `right - 1`. That was the old value of `best[right - 1]`, but we have just overwritten it. So before each overwrite, we save the old value and use it as `inner` in the next step.

```python
class ColouredCirclesPalindrome:
    def __init__(self):
        pass

    def maxCirclesKept(self, circleColours: str) -> int:
        colours = circleColours
        n = len(colours)

        # best[right] = most circles we can keep from circle left to circle right,
        # for the current value of left
        best = [0] * n

        for left in range(n - 1, -1, -1):
            best[left] = 1  # one circle alone is a palindrome
            inner = 0  # answer for left + 1 .. right - 1, empty at first

            for right in range(left + 1, n):
                erase_left = best[right]  # old value: answer for left + 1 .. right
                erase_right = best[right - 1]  # new value: answer for left .. right - 1

                if colours[left] == colours[right]:
                    best[right] = inner + 2  # keep both end circles
                else:
                    best[right] = max(erase_left, erase_right)

                inner = erase_left  # the middle part for the next right

        return best[n - 1]
```

**Time:** O(n^2), the same work as the table.

**Memory:** O(n), just one list of size n.


## Complexity Summary

| Solution | Time | Memory |
|---|---|---|
| Brute Force (Plain Recursion) | O(2^n) | O(n) |
| Table | O(n^2) | O(n^2) |
| One Row | O(n^2) | O(n) |

n is the number of circles.