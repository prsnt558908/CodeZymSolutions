# Insert Into Circular Linked List While Keeping It Sorted in Python

#### Problem Statement
[https://codezym.com/question/193-insert-into-circular-list-sorted](https://codezym.com/question/193-insert-into-circular-list-sorted)


## Core Idea

Think of a sorted circular list as numbers written around a clock. As you walk around it, the numbers keep going up (or stay the same). They go down at only one spot, where the largest number is followed by the smallest number. Let's call such a spot a **drop**.

To insert a new value, we walk around the ring once and check every gap between two neighbors. The new value goes into the first gap where it fits: either between two numbers that go up, or at the drop if it is a new largest or new smallest number.

If no gap fits after a full circle, all numbers are equal, so we just insert after the first element. This is a single pass, so it runs in O(n) time.


## How the Input Works

The list `[4, 1, 3]` stands for this ring:

```
4 -> 1 -> 3 -> back to 4
```

The first element is the node we were given. It can be any node in the ring, so the smallest value may sit in the middle of the list.

A ring with `n` values has `n` gaps. The gap after index `i` is between `circularList[i]` and `circularList[(i + 1) % n]`. The `% n` makes the last element wrap around to the first.

The answer must start from the same first element.


## Basic Approach: Try Every Gap

The most direct idea is to try every gap in order:

1. Put `insertVal` into the gap after index `0`, then after index `1`, and so on.
2. After each try, check if the ring is still sorted.
3. Return the first try that works.

A sorted ring has at most one drop. If we see two or more drops, the ring is not sorted. So the check just counts the drops.

```python
# Try every gap in order and return the first one that keeps the ring sorted
for i in range(len(circularList)):
    candidate = list(circularList)
    candidate.insert(i + 1, insertVal)  # put insertVal right after index i

    if self.isSortedRing(candidate):
        return candidate
```

```python
# A drop is a value followed by a smaller value. A sorted ring has at most one.
def isSortedRing(self, ring: List[int]) -> bool:
    n = len(ring)
    drops = 0

    for i in range(n):
        current = ring[i]
        nextVal = ring[(i + 1) % n]
        if current > nextVal:
            drops += 1

    return drops <= 1
```

### Why Is This Slow?

Each try copies the list and checks every pair again. That is O(n) work per try. In the worst case we try all `n` gaps, so the total is O(n²).

With 50,000 values, that is around 2.5 billion steps. Way too slow.


## Better Approach: Only Look at the Gap

The ring is already sorted before we insert. Putting `insertVal` into a gap only changes that one gap. Every other pair of neighbors stays the same.

So we don't need to recheck the whole ring. We only look at the two neighbors around the gap, `current` and `nextVal`. This gives us three simple rules.

**Rule 1: Normal gap (`current <= nextVal`)**

The values go up here, so `insertVal` fits if it sits between them:

`current <= insertVal <= nextVal`

Example: in `[4, 1, 3]`, the value `2` fits between `1` and `3`.

**Rule 2: Drop gap (`current > nextVal`)**

Here `current` is the largest value and `nextVal` is the smallest. A new largest or new smallest value belongs right here:

`insertVal >= current` or `insertVal <= nextVal`

Example: in `[3, 5, 1]`, the value `6` fits between `5` and `1`.

**Rule 3: No gap fits**

This only happens when all values are equal, like `[3, 3, 3]` with `5`. There is no drop, and `5` is not between `3` and `3`. A single element list like `[1]` with `0` also lands here.

In such a ring any spot works, and the problem asks us to insert after the first element. So we use index `0`.


## Solution 1: One Pass Over the List

Rules 1 and 2 go into a small helper, `canInsertBetween`. Then the solution is just two steps:

1. `findInsertIndex` checks the gaps from index `0` and returns the first index `i` where `insertVal` fits after `circularList[i]`. If nothing fits, it returns `0` (rule 3).
2. We copy the list and insert `insertVal` at position `i + 1`. A Python list lets us insert a value at any position, and copying keeps the input list unchanged.

We always insert after some element, so the first element never moves. If the chosen gap is the last one, the new value goes at the end. For example, `[1, 3, 5]` with `0` gives `[1, 3, 5, 0]`.

An empty list is a special case. We just return `[insertVal]`.

```python
from typing import List


class InsertIntoSortedCircularList:

    def __init__(self):
        pass

    def insertIntoSortedCircularList(self, circularList: List[int], insertVal: int) -> List[int]:
        """
        Inserts insertVal into the sorted circular list.
        Returns the values starting from the originally given node.
        """
        # Copy the list, so we can insert a value at any position
        result = list(circularList)

        # Empty ring: the new value becomes a ring with one node
        if not result:
            result.append(insertVal)
            return result

        # Put insertVal right after the chosen index
        insertIndex = self.findInsertIndex(circularList, insertVal)
        result.insert(insertIndex + 1, insertVal)
        return result

    def findInsertIndex(self, circularList: List[int], insertVal: int) -> int:
        """
        Returns the first index i where insertVal fits between
        circularList[i] and the element after it.
        The last element wraps around to the first one.
        """
        n = len(circularList)

        for i in range(n):
            current = circularList[i]
            nextVal = circularList[(i + 1) % n]

            if self.canInsertBetween(current, nextVal, insertVal):
                return i

        # No gap fits, so all values are equal. Insert after the first element.
        return 0

    def canInsertBetween(self, current: int, nextVal: int, insertVal: int) -> bool:
        """Checks if insertVal can sit between two neighbors and keep the ring sorted."""

        # Rule 1: normal gap, insertVal lies between the two values
        if current <= insertVal <= nextVal:
            return True

        # Rule 2: drop gap, from the largest value to the smallest value
        if current > nextVal:
            # insertVal is a new largest or a new smallest value
            return insertVal >= current or insertVal <= nextVal

        return False
```

### Dry Run

Input: `circularList = [4, 1, 3]`, `insertVal = 2`

| i | current | nextVal | Gap type | Fits? |
|---|---------|---------|----------|-------|
| 0 | 4 | 1 | drop | No, 2 is not >= 4 and not <= 1 |
| 1 | 1 | 3 | normal | Yes, 1 <= 2 <= 3 |

`findInsertIndex` returns `1`, so `2` is inserted at position `2`. The answer is `[4, 1, 2, 3]`.

### Complexity

- **Time:** O(n). One pass to find the gap, and one copy to build the answer.
- **Space:** O(n) for the answer list.


## Solution 2: Using Real Linked List Nodes

In an interview you usually get a real node instead of a list. The rules stay exactly the same, only the walking changes.

We need a small `Node` class. Each node holds a value and a link to the next node, which is all a linked list is made of.

- Start with `node = head`. Its neighbor is `node.next`.
- Move `node` forward until `insertVal` fits between `node.val` and `node.next.val`.
- If `node` comes back to `head`, we went a full circle without a fit (rule 3), so we insert after `head`.

Linking the new node after `node` takes two pointer changes:

```python
newNode.next = node.next
node.next = newNode
```

If the ring is empty, the new node simply points to itself.

`buildRing` turns the input list into a ring, and `toList` reads it back starting from the original head.

```python
from typing import List, Optional


class Node:
    """One node of the circular linked list."""

    def __init__(self, val: int):
        self.val = val
        self.next = None


class InsertIntoSortedCircularList:

    def __init__(self):
        pass

    def insertIntoSortedCircularList(self, circularList: List[int], insertVal: int) -> List[int]:
        head = self.buildRing(circularList)
        head = self.insert(head, insertVal)
        return self.toList(head)

    def insert(self, head: Optional[Node], insertVal: int) -> Node:
        """
        Inserts insertVal into the sorted ring and returns the head.
        The head stays the same unless the ring was empty.
        """
        newNode = Node(insertVal)

        # Empty ring: the new node points to itself
        if head is None:
            newNode.next = newNode
            return newNode

        node = head
        while True:
            if self.canInsertBetween(node.val, node.next.val, insertVal):
                break
            node = node.next

            # Back at head: no gap fits, so all values are equal.
            # Stop here, so the new node goes right after head.
            if node is head:
                break

        # Link the new node right after node
        newNode.next = node.next
        node.next = newNode
        return head

    def canInsertBetween(self, current: int, nextVal: int, insertVal: int) -> bool:
        """Checks if insertVal can sit between two neighbors and keep the ring sorted."""

        # Rule 1: normal gap, insertVal lies between the two values
        if current <= insertVal <= nextVal:
            return True

        # Rule 2: drop gap, from the largest value to the smallest value
        if current > nextVal:
            # insertVal is a new largest or a new smallest value
            return insertVal >= current or insertVal <= nextVal

        return False

    def buildRing(self, values: List[int]) -> Optional[Node]:
        """Builds a circular linked list from the values and returns its first node."""
        if not values:
            return None

        head = Node(values[0])
        tail = head
        for value in values[1:]:
            tail.next = Node(value)
            tail = tail.next

        tail.next = head  # close the ring
        return head

    def toList(self, head: Node) -> List[int]:
        """Reads the ring once, starting from head."""
        result = [head.val]

        node = head.next
        while node is not head:
            result.append(node.val)
            node = node.next
        return result
```

### Complexity

- **Time:** O(n).
- **Space:** O(n) to build the nodes from the input list. The insert step itself uses only O(1) extra space.