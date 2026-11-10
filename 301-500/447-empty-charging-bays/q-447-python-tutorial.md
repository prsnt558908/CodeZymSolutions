# Maximum Empty Charging Bays Between Vehicles in Python

#### Problem Statement
[https://codezym.com/question/447-empty-charging-bays](https://codezym.com/question/447-empty-charging-bays)

The gap between two neighboring vehicles is biggest when the left vehicle sits as far left as it can and the right vehicle sits as far right as it can.

So we only need two scans over the bays. A scan from the left finds the leftmost bay each vehicle can use. A scan from the right finds the rightmost bay each vehicle can use.

For every pair of neighbors, we join these two results and keep the biggest gap. This is a simple greedy idea that runs in linear time.

---

## The Problem in Simple Words

Every bay and every vehicle has a letter. We pick one bay for each vehicle so that:

- the bay letter matches the vehicle letter
- the picked bays go from left to right, in the same order as the vehicles

In other words, we pick positions in `chargerTypes` that spell out `vehicleNeeds`.

The gap between vehicle `i` and vehicle `i + 1` is the number of bays strictly between them. We want the biggest gap that any valid pick can create.

---

## Approach 1: Brute Force (Try Every Assignment)

The simplest idea is to try every valid assignment.

Park vehicle 0 in any matching bay, then vehicle 1 in any matching bay to its right, and so on. Once all vehicles are parked, record the biggest gap of that assignment.

```python
# vehicle: next vehicle to park
# last_bay: bay used by the previous vehicle
# best_so_far: largest gap in the current assignment
def tryAll(self, vehicleNeeds, chargerTypes, vehicle, last_bay, best_so_far):
    if vehicle == len(vehicleNeeds):
        self.max_gap = max(self.max_gap, best_so_far)
        return
    for bay in range(last_bay + 1, len(chargerTypes)):
        if chargerTypes[bay] == vehicleNeeds[vehicle]:
            # bays before the first vehicle are not counted as a gap
            gap = 0 if vehicle == 0 else bay - last_bay - 1
            self.tryAll(vehicleNeeds, chargerTypes, vehicle + 1, bay, max(best_so_far, gap))
```

Start the search with `self.max_gap = 0` and then `self.tryAll(vehicleNeeds, chargerTypes, 0, -1, 0)`.

**Why it is slow:** the number of assignments explodes. With 10 vehicles that all need `a` and 40 bays of type `a`, there are already about 847 million assignments. With 200,000 bays, it will never finish.

---

## Approach 2: Look at One Pair at a Time

Every gap belongs to one pair of neighbors. So instead of building whole assignments, fix one pair: vehicle `i` and vehicle `i + 1`.

To make their gap as big as possible:

- push vehicle `i` as far **left** as possible
- push vehicle `i + 1` as far **right** as possible

**Pushing left:** walk over the bays from left to right and give vehicles `0, 1, ..., i` the first matching bay, one after another. Taking the first match never hurts, because it leaves the most room for the vehicles that come after. The bay that vehicle `i` gets here is its **leftmost bay**.

**Pushing right:** do the same from the other end. Walk from the last bay to the first and give vehicles `last, ..., i + 1` the first matching bay. The bay that vehicle `i + 1` gets here is its **rightmost bay**.

**Can the two halves clash?** No. In any valid assignment, vehicle `i` can't be left of its leftmost bay, and vehicle `i + 1` can't be right of its rightmost bay. Vehicle `i` also comes before vehicle `i + 1`.

So the leftmost bay of `i` is always before the rightmost bay of `i + 1`, and the two halves join into one valid assignment.

Since nothing can push vehicle `i` further left or vehicle `i + 1` further right, this is the biggest gap this pair can ever have.

```python
vehicle_count = len(vehicleNeeds)
bay_count = len(chargerTypes)

max_gap = 0
for i in range(vehicle_count - 1):
    # Park vehicles 0 to i as far left as possible
    vehicle = 0
    left_bay = -1
    bay = 0
    while vehicle <= i:
        if chargerTypes[bay] == vehicleNeeds[vehicle]:
            left_bay = bay
            vehicle += 1
        bay += 1

    # Park vehicles from the last one down to i + 1 as far right as possible
    vehicle = vehicle_count - 1
    right_bay = bay_count
    bay = bay_count - 1
    while vehicle > i:
        if chargerTypes[bay] == vehicleNeeds[vehicle]:
            right_bay = bay
            vehicle -= 1
        bay -= 1

    max_gap = max(max_gap, right_bay - left_bay - 1)
```

**Why it is still slow:** every pair scans the bays again. That is `O(vehicle_count * bay_count)` time, which means billions of steps when both strings have 200,000 letters.

---

## Approach 3: Two Scans (Optimal)

The scans in Approach 2 keep repeating the same work.

When we push vehicles to the left, vehicle 0 always lands in the same bay, vehicle 1 always lands in the same bay, and so on. The value of `i` only decides where we stop. The scan from the right behaves the same way.

So we scan **once from each side** and save the bay of every vehicle:

- `leftmost[i]`: the leftmost bay vehicle `i` can ever use
- `rightmost[i]`: the rightmost bay vehicle `i` can ever use

We keep them in two plain lists, because later we read the value of any vehicle by its index, and lists give that instantly.

Now the best gap for the pair `(i, i + 1)` is just:

```
gap = rightmost[i + 1] - leftmost[i] - 1
```

The answer is the biggest of these gaps. With only one vehicle there are no pairs, so the answer stays `0`.

### Walkthrough

Example 1: `vehicleNeeds = "ctc"`, `chargerTypes = "cctaaac"`

| Bay  | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
|------|---|---|---|---|---|---|---|
| Type | c | c | t | a | a | a | c |

Scan from the left: `c` takes bay 0, `t` takes bay 2, `c` takes bay 6.

Scan from the right: the last `c` takes bay 6, `t` takes bay 2, the first `c` takes bay 1.

| Vehicle | Needs | leftmost | rightmost |
|---------|-------|----------|-----------|
| 0       | c     | 0        | 1         |
| 1       | t     | 2        | 2         |
| 2       | c     | 6        | 6         |

| Pair   | rightmost[i + 1] - leftmost[i] - 1 |
|--------|------------------------------------|
| (0, 1) | 2 - 0 - 1 = 1                      |
| (1, 2) | 6 - 2 - 1 = 3                      |

The biggest gap is **3**.

### Python Code

```python
class EVChargingBayPlanner:
    def __init__(self):
        pass

    def maximumFreeBayGap(self, vehicleNeeds: str, chargerTypes: str) -> int:
        """Return the largest number of free bays that can appear
        between two consecutive vehicles in any valid assignment."""
        vehicle_count = len(vehicleNeeds)
        bay_count = len(chargerTypes)

        # leftmost[i] = leftmost bay that vehicle i can ever use.
        # Scan left to right and give each vehicle the first matching bay.
        leftmost = [0] * vehicle_count
        vehicle = 0
        for bay in range(bay_count):
            if vehicle < vehicle_count and chargerTypes[bay] == vehicleNeeds[vehicle]:
                leftmost[vehicle] = bay
                vehicle += 1

        # rightmost[i] = rightmost bay that vehicle i can ever use.
        # Scan right to left and give each vehicle, starting from the last one,
        # the first matching bay.
        rightmost = [0] * vehicle_count
        vehicle = vehicle_count - 1
        for bay in range(bay_count - 1, -1, -1):
            if vehicle >= 0 and chargerTypes[bay] == vehicleNeeds[vehicle]:
                rightmost[vehicle] = bay
                vehicle -= 1

        # For every pair of neighbors, push the left vehicle fully left
        # and the right vehicle fully right, then measure the free bays.
        max_gap = 0
        for i in range(vehicle_count - 1):
            gap = rightmost[i + 1] - leftmost[i] - 1
            max_gap = max(max_gap, gap)
        return max_gap
```

### Complexity

- **Time:** `O(vehicle_count + bay_count)`. Each scan walks over the bays once, and the last loop walks over the vehicles once.
- **Space:** `O(vehicle_count)` for the two lists.