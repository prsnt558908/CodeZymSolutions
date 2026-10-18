# Video Game Turn Slots in Python

#### Problem Statement
[https://codezym.com/question/450-video-game-turn-slots](https://codezym.com/question/450-video-game-turn-slots)

The core idea is simple: **the child with the most turns decides the answer**.


That child must wait after every turn, so their turns get spread out across time. All the other children just fill the gaps in between. If there are not enough of them to fill the gaps, the machine stays unused for those slots.


We will first solve it with a **greedy simulation**: in every slot, let the free child with the most turns left play. Then we will notice that the answer only depends on a few counts, and replace the whole simulation with a **short formula**.

---

## Understanding the Problem

- Each name in `turnRequests` is one turn for that child.
- Names are case-insensitive. `"gaurav"` and `"GauRav"` are the same child.
- After a child plays, at least `waitingSlots` full slots must pass before that child plays again.
- Other children can play during that wait. If nobody is allowed to play, the slot is unused.
- We want the smallest total number of slots, unused ones included.


One important thing to notice: **the names themselves do not matter**. Only the number of turns per child matters. So the first step in every approach is to count turns per child.


Let us look at Example 1.

```
Aarav = 4 turns, Diya = 3 turns, Kabir = 1 turn, waitingSlots = 2

Aarav, Diya, Kabir, Aarav, Diya, unused, Aarav, Diya, unused, Aarav   ->  10 slots
```

Aarav has the most turns. Between two Aarav turns there must be 2 other slots. Diya and Kabir fill as many of them as they can. The rest stay unused.

---

## Why Not Try Every Order?

The most basic idea is to try every possible order of turns and pick the shortest valid one.


With up to 10,000 turns, the number of orders is astronomically large. So we need a smarter way to decide who plays in each slot.

---

## Approach 1: Greedy Simulation

### The Idea

We walk through the slots one by one. In every slot, we ask:

> Among the children who are allowed to play right now, who has the most turns left?

That child plays.


**Why the child with the most turns left?**


The child with the biggest pile of turns is the hardest to finish, because of the waiting time after each turn. If we keep delaying them, at the end only they will be left, and the machine will sit unused between their turns.


Giving them a turn whenever possible keeps their waiting time overlapping with other children's turns. That is exactly what avoids unused slots.

### Data Structures We Need

**1. `dict` for counting turns**


We convert every name to lowercase and count how many times it appears. This takes care of the case-insensitive rule. After counting, we only need the counts, not the names.


**2. `heapq` (max heap) for children who can play now**


It holds the "turns left" of every child who is free right now. Python's `heapq` is a min heap, so we store counts as negative numbers. That way the biggest count is always on top, and picking the right child for a slot is just one `heappop()`.


**3. `deque` for children who are waiting**


When a child plays and still has turns left, they go into the waiting line as a tuple `(turns_left, free_at_slot)`.


Why is a plain queue enough here? Every child waits the same number of slots. So a child who played earlier also becomes free earlier. The front of the queue is always the next child to become free, no sorting needed.


### Steps

1. Count turns per child (lowercase names).
2. Put all counts into the max heap.
3. Repeat while some child still has turns:
   - Move every waiting child whose wait is over back into the heap.
   - If the heap is empty, nobody can play. Jump straight to the slot when the next child is free.
   - Otherwise, the top child of the heap plays. If they still have turns left, send them to the waiting line, free at `slot + waitingSlots + 1`.
   - Move to the next slot.
4. The final value of `slot` is the answer.


The jump in step 3 is a small but useful trick. Without it, a case like one child with 10,000 turns and `waitingSlots = 100` would make us walk through about a million empty slots one by one.

### Dry Run on Example 3

`Ishaan = 4 turns, Zoya = 2 turns, waitingSlots = 3`

| Slot | Who plays | Note |
|------|-----------|------|
| 0 | Ishaan | 3 left, free again at slot 4 |
| 1 | Zoya | 1 left, free again at slot 5 |
| 2, 3 | unused | nobody free, jump to slot 4 |
| 4 | Ishaan | 2 left, free again at slot 8 |
| 5 | Zoya | done |
| 6, 7 | unused | jump to slot 8 |
| 8 | Ishaan | 1 left, free again at slot 12 |
| 9, 10, 11 | unused | jump to slot 12 |
| 12 | Ishaan | done |

Total slots used = **13**.

### Code

```python
import heapq
from collections import deque


class GameMachinePlanner:
    def __init__(self):
        pass

    def minimumSlots(self, turnRequests, waitingSlots):
        """
        Simulates the machine slot by slot.
        In every slot, the free child with the most turns left gets to play.
        """
        # Count turns of each child. Names are case-insensitive.
        turns_per_child = {}
        for name in turnRequests:
            child = name.lower()
            turns_per_child[child] = turns_per_child.get(child, 0) + 1

        # Children who can play right now. heapq is a min heap,
        # so we store negative counts to keep the biggest count on top.
        ready_children = [-turns for turns in turns_per_child.values()]
        heapq.heapify(ready_children)

        # Children who are waiting, stored as (turns_left, free_at_slot).
        waiting = deque()

        slot = 0
        while ready_children or waiting:
            # Children whose waiting is over can play again.
            while waiting and waiting[0][1] <= slot:
                turns_left, _ = waiting.popleft()
                heapq.heappush(ready_children, -turns_left)

            if not ready_children:
                # Nobody can play. Skip the unused slots in one jump.
                slot = waiting[0][1]
                continue

            # Child with the most turns left plays in this slot.
            turns_left = -heapq.heappop(ready_children) - 1
            if turns_left > 0:
                waiting.append((turns_left, slot + waitingSlots + 1))
            slot += 1

        return slot
```

### Complexity

Let `N` be the total number of turns and `K` the number of different children.

- **Time:** `O(N log K)`. Every turn is one heap operation. Each jump over unused slots costs only `O(1)`.
- **Space:** `O(K)` for the dict, the heap and the queue.

### What Can Be Better?

This solution is correct and fast enough. But it has a lot of moving parts: a heap, a queue and a loop that tracks time.


If you look at the dry run closely, the answer came from just one thing: **how many turns the busiest child has**, and how many gaps the others could fill. We never really needed to know the exact order. That gives us a much simpler way.

---

## Approach 2: Direct Counting (Optimal)

### The Idea

Let `max_turns` be the highest number of turns any child has.


Think of the busiest child's turns as the start of rows. Each row is `waitingSlots + 1` slots wide: one slot for the busiest child, and `waitingSlots` slots of waiting after it.


Here is Example 1 drawn as rows. `max_turns = 4` (Aarav) and each row is `2 + 1 = 3` slots wide.

```
Row 1:  Aarav   Diya    Kabir
Row 2:  Aarav   Diya    unused
Row 3:  Aarav   Diya    unused
Row 4:  Aarav
```

- There are `max_turns - 1 = 3` full rows of 3 slots each.
- The last row only needs Aarav's final turn. Nobody has to wait after the very last turn.
- Total = `3 * 3 + 1 = 10`.


Children with fewer turns fill the empty spots in the full rows. Place the children with more turns first, going down one column at a time. Each of them has at most `max_turns - 1` turns, which is exactly the number of full rows. So each child takes at most one spot per row, and the waiting rule still holds.

### What if Several Children Tie for the Most Turns?

Each of them also needs one spot in the last row.


For example, if Diya also had 4 turns, the last row would be `Aarav, Diya` and the answer would be `3 * 3 + 2 = 11`.


So the formula becomes:

```
slots = (max_turns - 1) * (waitingSlots + 1) + children_with_max_turns
```

### What if There Are More Turns Than Empty Spots?

Look at Example 2. `Mira = 3, Rohan = 2, Tara = 1, Neel = 1, waitingSlots = 1`.


The formula gives `(3 - 1) * 2 + 1 = 5`. But there are 7 turns in total, and every turn needs its own slot. So the answer can never be less than 7.


When there are too many turns, we simply make the rows wider:

```
Row 1:  Mira   Rohan   Tara
Row 2:  Mira   Rohan   Neel
Row 3:  Mira
```

Wider rows only increase the gap between repeated turns, so the waiting rule is never broken. And no slot is unused, so the answer is just the total number of turns.

### Final Answer

```
answer = max( (max_turns - 1) * (waitingSlots + 1) + children_with_max_turns ,  total turns )
```

Quick check with Example 3: `Ishaan = 4, Zoya = 2, waitingSlots = 3`.


`(4 - 1) * 4 + 1 = 13` and total turns = 6. The answer is `max(13, 6) = 13`.

```
Row 1:  Ishaan   Zoya     unused   unused
Row 2:  Ishaan   Zoya     unused   unused
Row 3:  Ishaan   unused   unused   unused
Row 4:  Ishaan
```

### Steps

1. Count turns per child using a `dict` with lowercase names.
2. Find `max_turns` and how many children have exactly `max_turns`.
3. Apply the formula and return the bigger of the two values.


We only read `turnRequests`, we never change it, so the rule about not modifying the list is respected.

### Code

```python
class GameMachinePlanner:
    def __init__(self):
        pass

    def minimumSlots(self, turnRequests, waitingSlots):
        """
        Finds the answer directly from the turn counts, without any simulation.
        """
        # Count turns of each child. Names are case-insensitive.
        turns_per_child = {}
        for name in turnRequests:
            child = name.lower()
            turns_per_child[child] = turns_per_child.get(child, 0) + 1

        # Highest turn count, and how many children have that count.
        max_turns = 0
        children_with_max_turns = 0
        for turns in turns_per_child.values():
            if turns > max_turns:
                max_turns = turns
                children_with_max_turns = 1
            elif turns == max_turns:
                children_with_max_turns += 1

        # (max_turns - 1) full rounds of size (waitingSlots + 1),
        # then one last slot for every child who has max_turns.
        slots_from_busiest_child = (max_turns - 1) * (waitingSlots + 1) + children_with_max_turns

        # Every turn needs its own slot, so we can never go below the total turns.
        return max(slots_from_busiest_child, len(turnRequests))
```

### Complexity

- **Time:** `O(N)`. One pass to count, one pass over the counts.
- **Space:** `O(K)` for the dict.


---

## Summary

| Approach | Time | Space | Idea |
|----------|------|-------|------|
| Greedy Simulation | `O(N log K)` | `O(K)` | Each slot, the free child with most turns left plays |
| Direct Counting | `O(N)` | `O(K)` | Busiest child sets the rows, others fill the gaps |


The key takeaway: when a problem asks for the **minimum time** and one item is the clear bottleneck, try to build the answer around that bottleneck. Often you do not need to simulate anything at all.