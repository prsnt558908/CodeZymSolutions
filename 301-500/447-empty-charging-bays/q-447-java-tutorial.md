# Maximum Empty Charging Bays Between Vehicles in Java

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

```java
int maxGap = 0;

// vehicle: next vehicle to park
// lastBay: bay used by the previous vehicle
// bestSoFar: largest gap in the current assignment
void tryAll(String vehicleNeeds, String chargerTypes, int vehicle, int lastBay, int bestSoFar) {
    if (vehicle == vehicleNeeds.length()) {
        maxGap = Math.max(maxGap, bestSoFar);
        return;
    }
    for (int bay = lastBay + 1; bay < chargerTypes.length(); bay++) {
        if (chargerTypes.charAt(bay) == vehicleNeeds.charAt(vehicle)) {
            // bays before the first vehicle are not counted as a gap
            int gap = (vehicle == 0) ? 0 : bay - lastBay - 1;
            tryAll(vehicleNeeds, chargerTypes, vehicle + 1, bay, Math.max(bestSoFar, gap));
        }
    }
}
```

Start the search with `tryAll(vehicleNeeds, chargerTypes, 0, -1, 0)`.

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

```java
int vehicleCount = vehicleNeeds.length();
int bayCount = chargerTypes.length();

int maxGap = 0;
for (int i = 0; i + 1 < vehicleCount; i++) {
    // Park vehicles 0 to i as far left as possible
    int vehicle = 0;
    int leftBay = -1;
    for (int bay = 0; vehicle <= i; bay++) {
        if (chargerTypes.charAt(bay) == vehicleNeeds.charAt(vehicle)) {
            leftBay = bay;
            vehicle++;
        }
    }

    // Park vehicles from the last one down to i + 1 as far right as possible
    vehicle = vehicleCount - 1;
    int rightBay = bayCount;
    for (int bay = bayCount - 1; vehicle > i; bay--) {
        if (chargerTypes.charAt(bay) == vehicleNeeds.charAt(vehicle)) {
            rightBay = bay;
            vehicle--;
        }
    }

    maxGap = Math.max(maxGap, rightBay - leftBay - 1);
}
```

**Why it is still slow:** every pair scans the bays again. That is `O(vehicleCount * bayCount)` time, which means billions of steps when both strings have 200,000 letters.

---

## Approach 3: Two Scans (Optimal)

The scans in Approach 2 keep repeating the same work.

When we push vehicles to the left, vehicle 0 always lands in the same bay, vehicle 1 always lands in the same bay, and so on. The value of `i` only decides where we stop. The scan from the right behaves the same way.

So we scan **once from each side** and save the bay of every vehicle:

- `leftmost[i]`: the leftmost bay vehicle `i` can ever use
- `rightmost[i]`: the rightmost bay vehicle `i` can ever use

We keep them in two plain `int[]` arrays, because later we read the value of any vehicle by its index, and arrays give that instantly.

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

### Java Code

```java
public class EVChargingBayPlanner {

    public EVChargingBayPlanner() {
    }

    /**
     * Returns the largest number of free bays that can appear
     * between two consecutive vehicles in any valid assignment.
     */
    public int maximumFreeBayGap(String vehicleNeeds, String chargerTypes) {
        int vehicleCount = vehicleNeeds.length();
        int bayCount = chargerTypes.length();

        // leftmost[i] = leftmost bay that vehicle i can ever use.
        // Scan left to right and give each vehicle the first matching bay.
        int[] leftmost = new int[vehicleCount];
        int vehicle = 0;
        for (int bay = 0; bay < bayCount; bay++) {
            if (vehicle < vehicleCount && chargerTypes.charAt(bay) == vehicleNeeds.charAt(vehicle)) {
                leftmost[vehicle] = bay;
                vehicle++;
            }
        }

        // rightmost[i] = rightmost bay that vehicle i can ever use.
        // Scan right to left and give each vehicle, starting from the last one,
        // the first matching bay.
        int[] rightmost = new int[vehicleCount];
        vehicle = vehicleCount - 1;
        for (int bay = bayCount - 1; bay >= 0; bay--) {
            if (vehicle >= 0 && chargerTypes.charAt(bay) == vehicleNeeds.charAt(vehicle)) {
                rightmost[vehicle] = bay;
                vehicle--;
            }
        }

        // For every pair of neighbors, push the left vehicle fully left
        // and the right vehicle fully right, then measure the free bays.
        int maxGap = 0;
        for (int i = 0; i + 1 < vehicleCount; i++) {
            int gap = rightmost[i + 1] - leftmost[i] - 1;
            maxGap = Math.max(maxGap, gap);
        }
        return maxGap;
    }
}
```

### Complexity

- **Time:** `O(vehicleCount + bayCount)`. Each scan walks over the bays once, and the last loop walks over the vehicles once.
- **Space:** `O(vehicleCount)` for the two arrays.