# String Burst in Python

#### Problem Statement
[https://codezym.com/question/109-string-burst](https://codezym.com/question/109-string-burst)

---

## Core Idea

Think of the string as a chain of **groups** of equal characters. For example, `aabccc` is `[a,2] [b,1] [c,3]`.


In every step, all big groups (size `>= burstLength`) burst at the same time. Then the pieces on both sides of each gap join together.


The key insight is this: after the first step, a group can become big **only** by joining with its neighbors at a gap. So we never need to scan the whole string again. We only look at the gaps.


We store the groups in a **doubly linked list**, so bursting a group and joining two groups both take O(1) time. This gives us an O(n) solution.


We will start with a simple brute force simulation, see why it is slow, and then improve it step by step.

---

## Understanding the Rules

Each step has two parts:

1. **Burst:** remove every group whose size is `>= burstLength`, all at the same time.
2. **Join:** glue the remaining pieces together. Equal neighbors become one group.


A group created by a join is **not** removed in the same step. It is checked only in the next step.


Example: `sequence = "abcccbba"`, `burstLength = 3`

```
Step 1:  a b [ccc] bb a   ->  burst ccc  ->  a bbb a   (bbb is new, it waits)
Step 2:  a [bbb] a        ->  burst bbb  ->  aa
Step 3:  aa has size 2    ->  nothing bursts

Answer: "aa"
```

---

## Why Not Use a Stack?

If you know the classic "remove adjacent duplicates" stack trick, it is tempting to use it here. But a stack bursts a group as soon as it is complete. It mixes different steps together.


Take `sequence = "aabbbaccca"` with `burstLength = 3`.


**Correct process:** step 1 bursts `bbb` and `ccc` together. All four `a`s join into `aaaa`. Step 2 bursts it. The answer is `""`.


**Stack:** after removing `bbb`, it sees `aa + a = aaa` and bursts it right away, before `ccc` is removed. The last `a` is left alone. The answer is `"a"`, which is wrong.


So we must respect the order of steps.

---

## Solution 1: Brute Force (Simulate Every Step)

### Idea

Just do exactly what the problem says.


In each step, scan the string from left to right and find each group. Keep only the small groups in a list of parts, then join them into a new string.


Joining the survivors one after another automatically glues the left and right parts. And since we decide what bursts by looking at the **old** string, a newly joined group never bursts in the same step.


Stop when a full scan removes nothing.

### Code

```python
class StringBurst:
    def __init__(self):
        pass

    def arrayBurst(self, sequence, burstLength):
        """
        Brute force: simulate every step exactly as the problem describes.
        Each step scans the whole string and drops every group of size >= burstLength.
        """
        current = sequence

        while True:
            parts = []
            removed_any = False

            i = 0
            while i < len(current):
                # find the end of the group that starts at i
                j = i
                while j < len(current) and current[j] == current[i]:
                    j += 1

                if j - i >= burstLength:
                    removed_any = True           # this group bursts
                else:
                    parts.append(current[i:j])   # this group survives
                i = j

            # nothing burst in this step, so the string is final
            if not removed_any:
                return current
            # left and right parts join automatically inside the new string
            current = "".join(parts)
```

### Complexity

One step costs O(n). The problem is the **number of steps**.


Look at `"abaccaba"` with `burstLength = 2`:

```
abaccaba  ->  abaaba  ->  abba  ->  aa  ->  ""
```

Each step bursts only one tiny group in the middle, but we rescan the whole string every time.


For a long string built like this, there are about n/2 steps. So the total work is **O(n²)**. With n = 10^5, that is billions of operations. Too slow.

### What is wasteful here?

1. We rescan groups that did not change at all.
2. We rebuild the whole string in every step.


Only the places right next to a burst can change. Let us use that.

---

## Solution 2: Optimized (Groups in a Doubly Linked List)

### Observation 1: Work with groups, not characters

`"eeeee"` is just one item: `[e,5]`. We compress the string into groups once, at the start.

### Observation 2: Only joins can create new big groups

After step 1, every original group that is still alive has size `< burstLength`.


The only way a group can grow is when the group between two equal groups bursts and they join.


So the candidates for the next step are **only the groups that grew by joining in this step**. Nothing else needs to be checked.

### Observation 3: A join can only happen at a gap

When a group bursts, it leaves a gap. The group on the left of the gap and the group on the right are now neighbors. If they have the same character, they join.


So for each burst group, we remember its **left neighbor**. We call it a **join point**.

### Why a doubly linked list?

We need these operations to be fast:

- remove a group from the middle
- find the left and right neighbors of a group
- join two neighboring groups


With a Python `list`, deleting from the middle (`del` or `pop(i)`) shifts all later elements, which is O(n).


A doubly linked list does all of these in O(1). Python has no built-in linked list where we can hold a node and remove it directly, so we write a tiny `Group` node class.

### The `Group` class

Each `Group` stores:

- `ch` and `count`: which character and how many times it repeats
- `prev` and `next`: links to the neighbor groups
- `removed`: whether this group has burst or was joined into another group


We also add a dummy `head` and a dummy `tail`. Now every real group always has a neighbor on both sides, so we never need `None` checks.

### The Algorithm

1. Compress the string into a linked list of groups. Put every group with `count >= burstLength` into `to_remove`.
2. While `to_remove` is not empty (one loop = one step):
   - **Burst:** unlink every group in `to_remove`. Save its `prev` group as a join point.
   - **Join:** for every join point that is still alive, keep merging its right neighbor into it while the characters match. If its new count is `>= burstLength`, add it to the list for the next step.
3. Walk the list and rebuild the final string.

### Two Small Details

**Neighboring groups can burst together.** When a group is unlinked, its `prev` is always the nearest alive group on its left. If that left group also bursts in the same step, it gets unlinked too and saves its own left neighbor. So we simply skip join points that are already removed, because another join point covers the same gap.


**One join point can join three or more groups.** Take `a X a Y a`, where `X` and `Y` burst in the same step. All three `a` groups become one. That is why the join uses a `while` loop and not a single `if`.

### Dry Run on Example 1

`sequence = "abcdeeeeeddcbfgfhhht"`, `burstLength = 3`

```
Groups:  a1 b1 c1 d1 e5 d2 c1 b1 f1 g1 f1 h3 t1

Step 1:  burst e5 (join point d1) and h3 (join point f1)
         d1 + d2 -> d3      3 >= 3, burst it in the next step
         f1 and t1 differ, no join

Step 2:  burst d3 (join point c1)
         c1 + c1 -> c2      2 < 3, it stays

Step 3:  nothing to burst

Result:  a b cc b f g f t  ->  "abccbfgft"
```

### Code

```python
class Group:
    """
    One group of equal characters, for example "eeeee" is Group('e', 5).
    Groups are linked as a doubly linked list so that removing a group
    or joining two groups takes O(1) time.
    """

    def __init__(self, ch, count):
        self.ch = ch
        self.count = count
        self.prev = None
        self.next = None
        self.removed = False


class StringBurst:
    def __init__(self):
        pass

    def arrayBurst(self, sequence, burstLength):
        # dummy head and tail, so we never have to check for None neighbors
        head = Group("#", 0)
        tail = Group("#", 0)
        head.next = tail
        tail.prev = head

        # groups that will burst in the current step
        to_remove = []

        # Step 0: compress the string into groups, "aabbb" -> [a,2] [b,3]
        n = len(sequence)
        i = 0
        while i < n:
            j = i
            while j < n and sequence[j] == sequence[i]:
                j += 1
            group = Group(sequence[i], j - i)
            self.add_before_tail(group, tail)
            if group.count >= burstLength:
                to_remove.append(group)
            i = j

        # Each loop iteration is exactly one step of the process
        while to_remove:

            # 1. Burst every removable group. Remember the group on its left,
            #    because that is the only place where a new join can happen.
            join_points = []
            for group in to_remove:
                # skip duplicates and groups that were joined into a group on their left
                if group.removed:
                    continue
                self.unlink(group)
                join_points.append(group.prev)

            # 2. Join equal neighbors at every join point.
            #    Joined groups are only checked in the NEXT step.
            next_remove = []
            for left in join_points:
                # a removed left group means another join point covers this gap
                if left is head or left.removed:
                    continue
                while left.next is not tail and left.next.ch == left.ch:
                    right = left.next
                    left.count += right.count
                    self.unlink(right)
                if left.count >= burstLength:
                    next_remove.append(left)

            to_remove = next_remove

        # Rebuild the final string from the groups that are left
        parts = []
        group = head.next
        while group is not tail:
            parts.append(group.ch * group.count)
            group = group.next
        return "".join(parts)

    def add_before_tail(self, group, tail):
        """Adds a group at the end of the list, just before the dummy tail."""
        group.prev = tail.prev
        group.next = tail
        tail.prev.next = group
        tail.prev = group

    def unlink(self, group):
        """
        Removes a group from the list in O(1).
        Its own prev pointer is kept, so we can still find its left neighbor.
        """
        group.removed = True
        group.prev.next = group.next
        group.next.prev = group.prev
```

### Complexity

- **Time: O(n).** Each group is created once and removed at most once. Each burst creates one join point, and each merge inside the `while` loop removes one group. So the total work across all steps is O(n).
- **Space: O(n)** for the groups and the lists.

---

## Summary

| Solution | Time | Space |
|---|---|---|
| Brute force simulation | O(n²) | O(n) |
| Groups in a doubly linked list | O(n) | O(n) |


The big lesson: after a burst, **only the gap can change**. Instead of rescanning everything, we keep a list of gaps and check only the groups that grew there.