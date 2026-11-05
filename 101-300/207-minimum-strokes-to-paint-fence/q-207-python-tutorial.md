# Minimum Strokes to Paint Fence in Python


#### Problem Statement

[https://codezym.com/question/207-minimum-strokes-to-paint-fence](https://codezym.com/question/207-minimum-strokes-to-paint-fence)


For each continuous section of fence, compare two choices. Paint every plank vertically, or paint horizontal layers up to the shortest plank and solve the remaining parts. We first build this idea with divide and conquer. Then a stack helps us find all the splits in one pass, giving an `O(n)` solution without recursion.


## 1. Start with vertical strokes

One vertical stroke can paint an entire plank, regardless of its height. So `n` planks always need at most `n` strokes.

```python
vertical = len(plankHeights)
```

This is an upper bound, not always the minimum. For `[1, 1, 1, 1]`, one horizontal stroke paints the whole fence.


## 2. Split at the shortest plank

Suppose we are solving indices `left` through `right`, and the bottom `baseHeight` layers are already painted. Initially, `baseHeight` is `0`.


Painting vertically costs `right - left + 1` strokes. For the horizontal choice, find the shortest height `minimumHeight`. We can paint the whole section up to that height using `minimumHeight - baseHeight` strokes.


A shortest plank is now finished. Above it, a horizontal stroke cannot connect the left and right sides because the brush would lose contact with the fence. Solve those sides separately, with `minimumHeight` as their new base.


The main calculation looks like this. Here, `solve` returns `0` for an empty range.

```python
# minimumIndex is the index of a shortest plank in [left, right].
minimumHeight = heights[minimumIndex]
vertical = right - left + 1
horizontal = (
    minimumHeight - baseHeight
    + solve(left, minimumIndex - 1, minimumHeight)
    + solve(minimumIndex + 1, right, minimumHeight)
)
return min(vertical, horizontal)
```

Scanning each section to find its shortest plank gives an `O(n²)` worst case. For increasing heights, the scans can visit `n`, then `n - 1`, then `n - 2` planks. A recursive implementation can also reach depth `n`.


## 3. Find every split with a stack

Represent the splits as a binary tree. Each node is a plank, and it is a shortest plank in its section. Its left child represents the section before it. Its right child represents the section after it.


We build this tree from left to right. The stack holds the current path down the tree's right side, with heights in nondecreasing order.

1. Pop indices whose heights are greater than the current height.
2. The last popped node represents the whole popped section. Make it the current node's left child.
3. If an index remains on the stack, make the current node its right child.
4. Push the current index.

For `[3, 1, 2, 2]`, reading `1` pops `3`, so `3` becomes its left child. Both `2`s stay on the right side. Equal heights are allowed, which is why we pop only strictly taller planks.


Each index enters and leaves the stack at most once. The tree therefore takes `O(n)` time to build. Its root is the first index left on the stack.


## 4. Calculate answers from children to parents

`FencePainter` contains the required method and a `buildTree` helper. We copy the input into `heights` and leave the original list unchanged.


`left`, `right`, and `parent` are lists of plank indices. A value of `-1` means there is no link. Another list works as a stack through `append()` and `pop()`.


For each node, `width[node]` counts the planks in its section. `strokes[node]` stores the minimum additional strokes, assuming painting has already reached its parent's height. The root starts from height `0`.


The vertical cost is `width[node]`. The horizontal cost is the difference between this node's height and its parent's height, plus the answers for its children.


We record parents before children in `order`, then process that list backwards. Every child's answer is ready before its parent needs it. This avoids deep recursive calls, including for 5,000 increasing or equal heights.


## Example

For `[3, 1, 2, 2]`, painting vertically takes `4` strokes.


The horizontal choice paints the bottom layer across all four planks in one stroke. The left section `[3]` then needs one vertical stroke. The right section `[2, 2]` needs one more horizontal stroke.


The answer is `min(4, 1 + 1 + 1) = 3`.


## Why the two choices are enough

If every plank in a section receives a vertical stroke, we need at least its width in strokes. Painting each plank vertically achieves that cost.


Otherwise, some plank receives no vertical stroke. Every layer between the base and the shortest height must then be covered horizontally. We can extend those strokes across the whole section without adding strokes, since every plank reaches that height and repainting is allowed.


After those common layers are painted, the parts on either side of a shortest plank are independent. Taking the smaller cost at every split therefore gives the minimum for the whole fence.


## Complexity

Time is `O(n)`. Stack construction visits each plank a constant number of times, and the remaining passes process each node once.


Extra space is `O(n)` for the tree links, stack, processing order, and answers. Reading all heights already requires `O(n)` time, so this time complexity is optimal.


The answer is at most `n`. Heights can be as large as `1,000,000,000`, but the algorithm never loops once per unit of height.


## Complete Code

```python
from typing import List


class FencePainter:
    def __init__(self):
        pass

    def minimumStrokesToPaintFence(self, plankHeights: List[int]) -> int:
        n = len(plankHeights)
        heights = list(plankHeights)
        left = [-1] * n
        right = [-1] * n
        parent = [-1] * n

        root = self.buildTree(heights, left, right, parent)

        # Record parents before children, then process this order backwards.
        order = [root]
        position = 0
        while position < len(order):
            node = order[position]
            if left[node] != -1:
                order.append(left[node])
            if right[node] != -1:
                order.append(right[node])
            position += 1

        width = [0] * n
        strokes = [0] * n
        for node in reversed(order):
            baseHeight = 0 if parent[node] == -1 else heights[parent[node]]
            horizontal = heights[node] - baseHeight
            width[node] = 1

            if left[node] != -1:
                width[node] += width[left[node]]
                horizontal += strokes[left[node]]
            if right[node] != -1:
                width[node] += width[right[node]]
                horizontal += strokes[right[node]]

            # Either paint every plank vertically or finish the horizontal plan.
            strokes[node] = min(width[node], horizontal)

        return strokes[root]

    def buildTree(
        self,
        heights: List[int],
        left: List[int],
        right: List[int],
        parent: List[int],
    ) -> int:
        stack = []
        for i in range(len(heights)):
            lastPopped = -1

            # Keep heights in nondecreasing order, including equal heights.
            while stack and heights[stack[-1]] > heights[i]:
                lastPopped = stack.pop()

            if stack:
                previous = stack[-1]
                right[previous] = i
                parent[i] = previous
            if lastPopped != -1:
                left[i] = lastPopped
                parent[lastPopped] = i
            stack.append(i)

        return stack[0]
```
