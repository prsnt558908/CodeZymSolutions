# Find Unoccupied Seat Position With Maximum Distance in Python

#### Problem Statement
[https://codezym.com/question/438-unoccupied-seat-with-max-distance](https://codezym.com/question/438-unoccupied-seat-with-max-distance)

The occupied seats split the row into groups of empty seats. Let us call each group a **gap**.

Inside a gap, the best seat is easy to find. For the gap at the start of the row, it is the first seat. For the gap at the end, it is the last seat. For a gap between two occupied seats, it is the middle seat.

So we walk through the row once, find the best seat of every gap, and keep the best one overall. This takes O(n) time and only O(1) extra space.

We will start with a simple brute force solution and improve it step by step.

## Understanding the Problem

For an empty seat, its **distance** is how far away the closest occupied seat is. We want the empty seat with the largest distance. If there is a tie, we return the smaller position.

Here is Example 1:

| Position | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|---|
| Seat | U | U | O | U | U | U | O | U |
| Distance | 2 | 1 | - | 1 | 2 | 1 | - | 1 |

Seats `0` and `4` both have the largest distance, `2`. We return `0` because it is smaller.

## Approach 1: Brute Force

The simplest idea is to follow the definition directly. For every empty seat, check every occupied seat and take the smallest distance.

We go from left to right and update the answer only when we find a **strictly larger** distance. So on a tie, the seat we found first, which is the smaller one, stays.

```python
n = len(seats)
best_position = -1
best_distance = 0

for i in range(n):
    if seats[i] == "O":
        continue

    # Distance from seat i to its nearest occupied seat
    nearest = n  # bigger than any real distance
    for j in range(n):
        if seats[j] == "O":
            nearest = min(nearest, abs(i - j))

    # Strictly greater keeps the smaller position on a tie
    if nearest > best_distance:
        best_distance = nearest
        best_position = i
```

**Problem:** for every empty seat, we scan the whole row again. That is O(n²) time. With 100,000 seats, it can take up to 10 billion steps, which is far too slow.

## Approach 2: Closest Occupied Seat on Each Side

Here is the key observation. For any empty seat, its nearest occupied seat is either the **closest one on its left** or the **closest one on its right**. Every other occupied seat is farther away.

So we do not need to check every occupied seat. We keep two lists:

- `left[i]`: distance from seat `i` to the closest occupied seat on its left.
- `right[i]`: distance from seat `i` to the closest occupied seat on its right.

Then the distance of seat `i` is just `min(left[i], right[i])`.

We fill `left` in one pass from left to right, remembering the last occupied seat we saw. We fill `right` the same way, from right to left.

```python
left = [0] * n   # distance to the closest occupied seat on the left
right = [0] * n  # distance to the closest occupied seat on the right

# Left to right: remember the last occupied seat we saw
last_occupied = -1
for i in range(n):
    if seats[i] == "O":
        last_occupied = i
    left[i] = n if last_occupied == -1 else i - last_occupied

# Right to left: same idea from the other side
last_occupied = -1
for i in range(n - 1, -1, -1):
    if seats[i] == "O":
        last_occupied = i
    right[i] = n if last_occupied == -1 else last_occupied - i

for i in range(n):
    if seats[i] == "U":
        distance = min(left[i], right[i])
        if distance > best_distance:
            best_distance = distance
            best_position = i
```

If a seat has no occupied seat on one side, we store `n` there. It is bigger than any real distance, so `min` picks the other side.

**Problem:** this is O(n) time, but the two lists need O(n) extra space. The problem asks for O(1) extra space.

## Approach 3: Look at Each Gap (Optimal)

Look at the empty seats between two occupied seats. They all have the **same** closest occupied seat on the left and the **same** closest one on the right.

So we do not need to store anything for each seat. We can look at a whole gap at once and directly pick its best seat.

### The Three Kinds of Gaps

**1. Left edge gap:** empty seats before the first occupied seat. They have an occupied seat only on their right, so the farther left, the better. If the first occupied seat is at position `first`, the best seat is `0` and its distance is `first`.

**2. Middle gap:** empty seats between two occupied seats at positions `a` and `b`. The best seat is the middle one. Using integer division (`//`), its distance is `(b - a) // 2` and its position is `a + (b - a) // 2`.

In Example 1, seats `3` to `5` sit between occupied seats `2` and `6`. The middle seat is `2 + (6 - 2) // 2 = 4`, with distance `2`.

**3. Right edge gap:** empty seats after the last occupied seat. If the last occupied seat is at position `last`, the best seat is `n - 1` and its distance is `n - 1 - last`.

What if two occupied seats sit right next to each other, or the row starts or ends with an occupied seat? Then there is no empty seat there, and the distance works out to `0`. Every empty seat has a distance of at least `1`, so this case never wins.

### Handling Ties

Both kinds of ties are handled automatically.

**Inside a middle gap:** if the gap has an even number of seats, two seats share the middle. For `a = 2` and `b = 5`, seats `3` and `4` both have distance `1`. The formula gives `2 + (5 - 2) // 2 = 3`, which is the smaller one.

**Between gaps:** we visit gaps from left to right, so their best seats come in increasing order. We update the answer only on a strictly larger distance, so the earlier, smaller seat stays.

### How the Code Works

We walk through the row and keep just three numbers:

- `previous_occupied`: the last occupied seat we saw, `-1` if none yet.
- `best_position` and `best_distance`: the best seat found so far.

Every time we reach an occupied seat `i`, the gap before it has just ended. If `previous_occupied` is `-1`, it was the left edge gap. Otherwise, it was a middle gap between `previous_occupied` and `i`.

After the loop, the seats after `previous_occupied` form the right edge gap.

### Dry Run

Let us run Example 1: `U U O U U U O U`.

| Step | Gap that ended | Best seat in gap | Distance | Best so far |
|---|---|---|---|---|
| Reach `O` at 2 | Left edge, seats 0 to 1 | 0 | 2 | seat 0, distance 2 |
| Reach `O` at 6 | Middle, seats 3 to 5 | 4 | 2 | seat 0, distance 2 (tie, smaller stays) |
| End of row | Right edge, seat 7 | 7 | 1 | seat 0, distance 2 |

The answer is `0`.

### Python Code

```python
from typing import List


class SeatDistanceFinder:

    def __init__(self):
        pass

    def findSeatPosition(self, seats: List[str]) -> int:
        """
        Returns the position of the unoccupied seat that is farthest
        from its nearest occupied seat. On a tie, the smallest position wins.
        """
        n = len(seats)

        # Best seat found so far and its distance
        best_position = -1
        best_distance = 0

        # Position of the last occupied seat we have seen. -1 means none yet.
        previous_occupied = -1

        for i in range(n):
            if seats[i] == "U":
                continue

            # Seat i is occupied, so the gap of empty seats before it just ended.
            if previous_occupied == -1:
                # Left edge gap: seats 0 to i - 1. Seat 0 is the farthest.
                gap_seat = 0
                gap_distance = i
            else:
                # Middle gap: the middle seat is the farthest.
                # Integer division picks the left middle seat on a tie.
                gap_distance = (i - previous_occupied) // 2
                gap_seat = previous_occupied + gap_distance

            # Update only on a strictly larger distance,
            # so on a tie the earlier (smaller) seat stays.
            if gap_distance > best_distance:
                best_distance = gap_distance
                best_position = gap_seat

            previous_occupied = i

        # Right edge gap: seats after the last occupied seat.
        # The last seat, n - 1, is the farthest.
        right_edge_distance = n - 1 - previous_occupied
        if right_edge_distance > best_distance:
            best_position = n - 1

        return best_position
```

### Complexity

- **Time:** O(n). We visit each seat once.
- **Extra Space:** O(1). We only keep a few integer variables, and the input list is never changed.