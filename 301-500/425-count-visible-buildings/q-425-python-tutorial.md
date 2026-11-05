# Count Visible Buildings in Python

#### Problem Statement

[https://codezym.com/question/425-count-visible-buildings](https://codezym.com/question/425-count-visible-buildings)

The main idea is to scan buildings from right to left and keep useful heights in a stack. For each building, count the shorter heights removed from the stack, then count the first taller height that remains. That taller building blocks everything beyond it. Each height enters and leaves the stack at most once, giving an `O(n)` solution.


## 1. Start with a simple scan

Fix a building at index `i` and check every building to its right. Keep `tallestBetween`, the greatest height strictly between `i` and the building being checked.

The next building is visible only when `tallestBetween` is smaller than both endpoint heights. Start with `0` because every height is positive.

This snippet counts visible buildings for one index. Here, `heights` is the input list.

```python
visibleCount = 0
tallestBetween = 0
currentHeight = heights[i]

for j in range(i + 1, len(heights)):
    nextHeight = heights[j]

    if tallestBetween < min(currentHeight, nextHeight):
        visibleCount += 1

    # Include this height only after checking the current pair.
    tallestBetween = max(tallestBetween, nextHeight)
```

Repeat this scan for every `i`. It takes `O(n^2)` time because we revisit the same buildings many times. With up to `100,000` buildings, we need to avoid those repeated scans.


## 2. Keep useful heights in a stack

Process the input from right to left. Use a list as a stack, with its last element as the top. The stored heights decrease from bottom to top. This is called a monotonic stack.

For a current height `h`:

1. Remove every top height smaller than `h`. Each removed building is visible, so add one to the count.
2. If the stack still contains a height, count its top once. It is the first taller building and blocks the view beyond it.
3. Store the count at the current index.
4. Push `h` onto the stack.

All heights are unique, so the height left at the top after step 1 must be taller.


## 3. Walk through an example

For `[12, 5, 10, 3, 8, 14, 7]`, consider the building of height `10`.

After processing everything to its right, the stack is `[14, 8, 3]`, with `3` at the top. Remove and count `3`, then `8`. The remaining `14` is also visible. The count is `3`, and pushing `10` leaves `[14, 10]`.

After processing height `5`, the stack becomes `[14, 10, 5]`. Height `12` removes and counts `5` and `10`, then counts `14`. Its answer is also `3`.

The complete result is `[3, 1, 3, 1, 1, 1, 0]`. The last building gets `0` because its stack is initially empty.


## 4. Why removing heights is safe

A building kept in the stack has no taller building in front of it within the processed part of the row. Therefore, when the current building is taller than the stack top, all buildings between them are shorter than that top. They can see each other.

Once a shorter height is removed, the current building stands in front of it. For every future building further left, this taller building blocks the removed one. We never need that removed height again.

After all shorter heights are removed, the remaining top is visible, but it blocks every building beyond it. These are exactly the buildings the algorithm counts.


## 5. Complexity

Each height is pushed once and removed at most once. The total time is `O(n)`, including result creation. The extra space is `O(n)` for the stack. The returned list also uses `O(n)` space.


## Python Code

`BuildingVisibilityAnalyzer` keeps all working lists inside the method, so repeated calls are independent. `answer` stores counts in the original order. `append()` adds a height to the stack and `pop()` removes its top. The input is not changed.

```python
from dataclasses import dataclass
from typing import List


@dataclass
class BuildingVisibilityAnalyzer:

    def countVisibleBuildings(self, buildingHeights: List[int]) -> List[int]:
        heights = buildingHeights
        n = len(heights)
        answer = [0] * n
        stack = []

        for i in range(n - 1, -1, -1):
            currentHeight = heights[i]
            visibleCount = 0

            # Each shorter height removed from the top is visible.
            while stack and stack[-1] < currentHeight:
                stack.pop()
                visibleCount += 1

            # The first taller building is visible and blocks the rest.
            if stack:
                visibleCount += 1

            answer[i] = visibleCount
            stack.append(currentHeight)

        return answer
```
