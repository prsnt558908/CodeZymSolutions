# Clockwise Border of Binary Tree in Python

#### Problem Statement
[https://codezym.com/question/233-clockwise-border-of-binary-tree](https://codezym.com/question/233-clockwise-border-of-binary-tree)


The clockwise border is just four small pieces joined together: the root, the right border going down, all the leaves from right to left, and the left border going up. So we do not need one clever traversal. We build each piece on its own and add them to the answer in this order.

First we turn the input strings into three simple lists, so we can reach any node by its index. After that, each border is a plain loop that walks down one path. The leaves come from a DFS (depth first search) that always visits the right child before the left child. To stay safe on very deep trees, this DFS uses our own stack instead of recursion.


## Understanding the Border

Here is the tree from Example 1:

```
          10
        /    \
       5      20
      / \       \
     3   7       30
```

Moving around it like the hands of a clock:

1. **Root:** `10`
2. **Right border, top to bottom:** `20`
3. **Leaves, right to left:** `30, 7, 3`
4. **Left border, bottom to top:** `5`

Answer: `[10, 20, 30, 7, 3, 5]`

Walking down the right border we reach `30`, but it is a leaf. This is why both borders skip leaves: every leaf is added exactly once, in step 3.

The right border stays inside the right subtree and the left border stays inside the left subtree. So the two borders can never share a node.


## Storing the Tree

Each node arrives as a string like `"10,1,2"`. We split every string once and fill three lists:

- `value[i]`: value of node `i`
- `left[i]`: index of its left child, or `-1`
- `right[i]`: index of its right child, or `-1`

Why lists? The input already gives every node a number, its index. With lists we can reach any node or child in O(1), without creating node objects and linking them.

A node is a **leaf** when both `left[i]` and `right[i]` are `-1`.

We always track nodes by **index**, not by value. Two different nodes can have the same value, and both may belong on the border.


## A Simple First Try: Recursion

The borders are easy. Start at one child of the root and keep stepping down.

The leaves need a walk over the whole tree. The most natural way is a recursive DFS that goes right before left:

```python
# Right child first, so leaves are added from right to left
def collectLeaves(self, node: int, result: List[int]) -> None:
    if node == -1:
        return
    if self.isLeaf(node):
        result.append(self.value[node])
        return
    self.collectLeaves(self.right[node], result)
    self.collectLeaves(self.left[node], result)
```

This gives the right answer, but it has one problem.

The tree can have up to 100,000 nodes. If they form one long chain, the recursion goes 100,000 calls deep. Python allows only about 1000 nested calls by default, so this raises a `RecursionError`.

We could raise that limit, but it is cleaner to avoid deep recursion completely. So we keep the same idea and only remove the recursion.


## Better Solution: Loops and Our Own Stack

**Step 1: Root.** Add the root first. If it has no children, the tree has only one node, so return right away. This also stops the root from being added again as a leaf.


**Step 2: Right border.** Start at the root's right child. While the current node is not a leaf, add it and move down: to the right child if it exists, otherwise to the left child. The loop stops at the first leaf, because leaves are added in the next step.

If the root has no right child, the loop never runs and the right border stays empty, just like in Example 2.


**Step 3: Leaves.** Put the root on a stack. Then keep popping one node at a time:

- If it is a leaf, add it to the answer.
- Otherwise, push its **left** child first and its **right** child second.

A stack always gives back the last item pushed, so the right child comes out first. The whole right side is finished before the left side starts, and the leaves come out from right to left.

Why a stack? It does exactly what recursion did, but without the recursion limit. Our stack is a plain Python list: `append` pushes an item and `pop` removes the last one. A list holds 100,000 items easily, so a long chain is no longer a problem.

Here is the stack on Example 1 (values shown instead of indexes, top of the stack on the right):

| Stack | Pop | What happens |
|---|---|---|
| `[10]` | `10` | push `5`, then `20` |
| `[5, 20]` | `20` | push `30` |
| `[5, 30]` | `30` | leaf, add `30` |
| `[5]` | `5` | push `3`, then `7` |
| `[3, 7]` | `7` | leaf, add `7` |
| `[3]` | `3` | leaf, add `3` |


**Step 4: Left border.** Walk down from the root's left child, this time preferring the left child. Save the non-leaf nodes in a list.

We walk top to bottom, but the answer needs bottom to top. So we reverse this list and then add it to the answer. If the root has no left child, the list stays empty, just like in Example 3.


## Complexity

- **Time:** O(n). Every string is parsed once, and every node is pushed on the stack at most once.
- **Space:** O(n) for the three lists, the stack and the answer.


## Python Code

```python
from typing import List


class BinaryTreeBorder:

    def __init__(self):
        # Node i is described by value[i], left[i] and right[i].
        # left[i] and right[i] hold child indexes, or -1 when the child is missing.
        self.value = []
        self.left = []
        self.right = []

    def clockwiseBorder(self, tree: List[str]) -> List[int]:
        self.buildTree(tree)
        result = []

        # 1. The root always comes first
        result.append(self.value[0])

        # A root without children means the tree has only one node
        if self.isLeaf(0):
            return result

        # 2. Right border, top to bottom
        self.addRightBorder(result)

        # 3. All leaves, right to left
        self.addLeaves(result)

        # 4. Left border, bottom to top
        self.addLeftBorder(result)

        return result

    def buildTree(self, tree: List[str]) -> None:
        """Turns every "value,leftIndex,rightIndex" string into list entries."""
        self.value = []
        self.left = []
        self.right = []

        for node in tree:
            parts = node.split(",")
            self.value.append(int(parts[0]))
            self.left.append(int(parts[1]))
            self.right.append(int(parts[2]))

    def isLeaf(self, node: int) -> bool:
        return self.left[node] == -1 and self.right[node] == -1

    def addRightBorder(self, result: List[int]) -> None:
        """Walks down from the root's right child, preferring the right child.

        Stops at the first leaf, because leaves are added in the next step.
        """
        node = self.right[0]
        while node != -1 and not self.isLeaf(node):
            result.append(self.value[node])
            node = self.right[node] if self.right[node] != -1 else self.left[node]

    def addLeaves(self, result: List[int]) -> None:
        """DFS with our own stack instead of recursion, so deep trees are safe.

        The left child is pushed before the right child, so the right child
        is popped first and leaves come out from right to left.
        """
        stack = [0]

        while stack:
            node = stack.pop()

            if self.isLeaf(node):
                result.append(self.value[node])
                continue
            if self.left[node] != -1:
                stack.append(self.left[node])
            if self.right[node] != -1:
                stack.append(self.right[node])

    def addLeftBorder(self, result: List[int]) -> None:
        """Walks down from the root's left child, preferring the left child.

        We walk top to bottom, so the path is reversed before adding it.
        """
        path = []
        node = self.left[0]
        while node != -1 and not self.isLeaf(node):
            path.append(self.value[node])
            node = self.left[node] if self.left[node] != -1 else self.right[node]

        path.reverse()
        result.extend(path)
```