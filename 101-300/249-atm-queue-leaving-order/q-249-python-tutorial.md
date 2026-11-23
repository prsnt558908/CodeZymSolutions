# ATM Queue Leaving Order in Python

#### Problem Statement
[https://codezym.com/question/249-atm-queue-leaving-order](https://codezym.com/question/249-atm-queue-leaving-order)

---

## Core Idea

The obvious approach is to play the queue turn by turn. But one person alone can need a billion turns, so this is far too slow.

The key insight is that the queue moves in **rounds** and never changes its order. Because of this, the round in which a person leaves depends only on how many turns they need.

So the solution is just two steps. Count the turns each person needs, then sort people by turns, and by person number when turns are equal.

---

## Solution 1: Simulate the Queue (Brute Force)

Let's first do exactly what the problem says.

We keep a queue. Each entry holds a person's number and the money they still need.

We take the person at the front and let them withdraw. If they still need money, they go to the back. If not, they leave and we add them to the answer.

A **queue** is the natural choice here. The ATM line is first come, first served, and people re-join at the back.

We use `deque` because `popleft()` removes the front item in `O(1)`. On a normal list, `pop(0)` takes `O(n)`.

```python
from collections import deque

# each entry is [personNumber, amountStillNeeded]
queue = deque()
for i in range(len(amounts)):
    queue.append([i + 1, amounts[i]])

exitOrder = []
while queue:
    person = queue.popleft()
    person[1] -= maxWithdraw            # one turn at the ATM

    if person[1] > 0:
        queue.append(person)            # still needs money, go to the back
    else:
        exitOrder.append(person[0])     # done, leave the queue
```

### Why this is too slow

Each loop step is one turn. So the total work equals the total number of turns of all people.

One person can need `1,000,000,000` turns, for example when the amount is `1,000,000,000` and `maxWithdraw` is `1`. With `100,000` such people, that is up to `10^14` turns.

That will never finish in time. We need the exit order without playing every single turn.

**Complexity:** Time `O(total turns)`, Space `O(n)`.

---

## Solution 2: Count Turns and Sort (Efficient)

### Observation 1: The queue moves in rounds

Let's trace Example 1: `amounts = [5, 1, 9, 4]` and `maxWithdraw = 4`.

The number in brackets is the money a person still needs at the start of the round.

| Round | Queue at the start of the round | Who leaves |
|---|---|---|
| 1 | P1 (5), P2 (1), P3 (9), P4 (4) | P2, P4 |
| 2 | P1 (1), P3 (5) | P1 |
| 3 | P3 (1) | P3 |

Exit order: `2, 4, 1, 3`.

Notice two things:

- In every round, each person still in line gets exactly **one** turn.
- People who go to the back keep their old order. So every round goes in increasing person number.

### Observation 2: Exit round = turns needed

A person who needs `t` turns gets their last turn in round `t`, and leaves in that round.

So all we need is the number of turns for each person:

```
turns = ceil(amount / maxWithdraw)
```

For P3 above, `ceil(9 / 4) = 3`, and P3 does leave in round 3.

Now the exit order follows two simple rules:

1. Fewer turns means an earlier round, so that person leaves first.
2. Same turns means the same round, so the smaller person number leaves first.

Rule 2 is exactly the tie-break rule given in the problem.

### Counting turns with whole numbers

We don't need `math.ceil` or floats for this.

`amount // maxWithdraw` gives the number of full turns. If `amount % maxWithdraw` is not `0`, some money is left over, and that needs one more turn.

For example, `9 // 4 = 2` full turns and `9 % 4 = 1` is left over. So P3 needs `3` turns.

### Algorithm

1. For each person `p`, compute `turns[p]`.
2. Make a list of people `1` to `n`.
3. Sort the list by `turns`. If turns are equal, sort by person number.

For step 3 we sort with the key `(turns[p], p)`. Python compares these pairs item by item, so it looks at turns first and at the person number only when turns are equal.

### Why these data structures

- `turns` list: stores each person's turns once, so the sort can compare them quickly. It has size `n + 1`, so `turns[p]` directly means person `p`.
- `people` list: this is the answer itself. We fill it with `1` to `n`, sort it and return it.

### Code

```python
from typing import List


class ATMQueue:

    def __init__(self):
        pass

    def getExitOrder(self, amounts: List[int], maxWithdraw: int) -> List[int]:
        """
        Returns people numbers in the order they leave the ATM queue.
        People who need fewer turns leave first.
        If two people need the same number of turns, the smaller number leaves first.
        """
        n = len(amounts)

        # turns[p] = number of turns person p needs (people are numbered 1 to n)
        turns = [0] * (n + 1)
        for p in range(1, n + 1):
            turns[p] = self.countTurns(amounts[p - 1], maxWithdraw)

        # start with people 1 to n
        people = list(range(1, n + 1))

        # fewer turns first, same turns -> smaller person number first
        people.sort(key=lambda p: (turns[p], p))

        return people

    def countTurns(self, amount: int, maxWithdraw: int) -> int:
        """
        Number of turns needed to withdraw `amount`
        when one turn gives at most `maxWithdraw`.
        Same as ceil(amount / maxWithdraw), using only integer math.
        """
        turns = amount // maxWithdraw
        if amount % maxWithdraw != 0:
            turns += 1  # leftover money needs one more turn
        return turns
```

### Complexity

- **Time:** `O(n log n)`. Counting turns takes `O(n)` and sorting takes `O(n log n)`.
- **Space:** `O(n)` for the `turns` list and the `people` list.