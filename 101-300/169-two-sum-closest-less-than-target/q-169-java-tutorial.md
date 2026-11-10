# Two Sum Closest To And Less Than Target in Java

#### Problem Statement
[https://codezym.com/question/169-two-sum-closest-less-than-target](https://codezym.com/question/169-two-sum-closest-less-than-target)

---

## Core Idea

We need to pick two numbers whose sum is as big as possible, but still strictly less than `target`.

The simplest way is to try every pair. It works, but it gets slow as the list grows.

The faster way splits the job into two easy steps:

1. **Find the best sum.** Sort a copy of the list and use two pointers to find the largest pair sum below `target`.
2. **Find the pair that makes this sum.** Go back to the original list and use a hash map, just like in classic two sum, to pick the correct pair.

Two pointers are great at finding the best sum. A hash map is great at finding two numbers that add up to an exact value. So we let each tool do the part it is good at.

---

## The Tricky Part: Ties

Many pairs can have the same best sum. In that case:

- Pick the pair with the smaller first index `i`.
- If there is still a tie, pick the smaller second index `j`.

We will use this example throughout:

`numbers = [5, 5, 4, 6]`, `target = 11`

The best valid sum is `10`, and two pairs make it:

- Indices `(0, 1)`: `5 + 5`
- Indices `(2, 3)`: `4 + 6`

Index `0` comes before index `2`, so the answer is `"5,5"`.

---

## Approach 1: Brute Force

Check every pair `(i, j)` with `i < j`. If the sum is less than `target` and bigger than the best sum so far, remember that pair.

```java
int bestSum = -1;
int first = -1;
int second = -1;

for (int i = 0; i < numbers.size(); i++) {
    for (int j = i + 1; j < numbers.size(); j++) {
        int sum = numbers.get(i) + numbers.get(j);

        // Update only on a strictly bigger sum, so earlier pairs win ties.
        if (sum < target && sum > bestSum) {
            bestSum = sum;
            first = i;
            second = j;
        }
    }
}

if (bestSum == -1) {
    return "-1";
}
return numbers.get(first) + "," + numbers.get(second);
```

**How are ties handled?** Pairs are visited with smaller `i` first, then smaller `j`. We update only when the sum is strictly bigger. So a later pair with the same sum can never replace an earlier one.

In our example, `(0, 1)` gives `10` first. Later `(2, 3)` also gives `10`, but that is not bigger, so we keep `(0, 1)`.

**The problem:** We check about `n² / 2` pairs, so this takes **O(n²)** time.

With at most 100 numbers this is still fast. But with 100,000 numbers it would be about 5 billion checks. Let us do better.

---

## Approach 2: Sorting + Two Pointers + Hash Map

### Step 1: Find the best sum with two pointers

Sort a **copy** of the list. Put `left` at the start (smallest number) and `right` at the end (biggest number). Then look at `sorted[left] + sorted[right]`:

- **Sum is less than `target`:** It is valid, so save it if it is the best so far. Move `left` one step right to try a bigger sum.
- **Sum is `target` or more:** It is too big. Move `right` one step left to make the sum smaller.

Stop when the two pointers meet.

**Why can we move `left` after a valid sum?** `sorted[right]` is the biggest number we can still pair with. So `sorted[left]` cannot make a bigger valid sum than this one.

**Why can we move `right` when the sum is too big?** `sorted[left]` is the smallest number we can still pair with. If even that makes the sum too big, every other choice will be too big as well.

**Why sort a copy?** Step 2 needs the original order. Also, the input can be a read only list like `List.of(...)`, which cannot be sorted in place.

### Why not just return the pair from Step 1?

Sorting loses the original positions, so the sorted list cannot apply the tie rule.

In our example, the sorted copy is `[4, 5, 5, 6]`. Two pointers find `4 + 6` first. But the correct answer is `"5,5"`, because a `5` sits at index `0` in the original list.

So Step 1 only gives us the correct **sum**. Step 2 finds the correct **pair**.

### Step 2: Find the earliest pair with this exact sum

Now we know `bestSum`. Finding two numbers that add up to exactly `bestSum` is the classic two sum problem.

For the number at index `i`, its partner must be `bestSum - numbers[i]`.

We walk the original list **from right to left** and keep a hash map:

`value -> smallest index of that value on the right side of i`

At each index `i`:

1. If the partner is in the map, save `i` and the partner's index as the current answer.
2. Put `numbers[i]` in the map with index `i`. We do this after the check, so a number never pairs with itself.

**Why right to left?** We overwrite the answer every time we find a match. Since we are moving left, the last overwrite happens at the smallest `i` that has a partner.

**Why does the map give the smallest `j`?** The map only holds indices to the right of `i`. When a value shows up again, its old index is replaced by the new, smaller one. So the map always points to the closest copy of each value on the right, which is the smallest `j`.

**Why a hash map?** It tells us in O(1) time on average whether the partner exists on the right, and where. Without it we would need an inner loop again, which brings back O(n²).

### Dry Run

**Step 1** on the sorted copy `[4, 5, 5, 6]`, `target = 11`

| sorted[left] | sorted[right] | sum | action |
|---|---|---|---|
| 4 | 6 | 10 | valid, bestSum = 10, move left |
| 5 | 6 | 11 | too big, move right |
| 5 | 5 | 10 | valid, bestSum stays 10, move left |

The pointers meet, so `bestSum = 10`.

**Step 2** on the original list `[5, 5, 4, 6]`, from right to left

| i | numbers[i] | partner | found in map? | answer so far | map after this step |
|---|---|---|---|---|---|
| 3 | 6 | 4 | no | none | {6: 3} |
| 2 | 4 | 6 | yes, index 3 | (2, 3) | {6: 3, 4: 2} |
| 1 | 5 | 5 | no | (2, 3) | {6: 3, 4: 2, 5: 1} |
| 0 | 5 | 5 | yes, index 1 | (0, 1) | {6: 3, 4: 2, 5: 0} |

The final pair is `(0, 1)`, so we return `"5,5"`.

### Complexity

- **Time:** O(n log n) for sorting. Two pointers and the hash map pass are O(n) each.
- **Space:** O(n) for the sorted copy and the hash map.

### Java Code

```java
import java.util.*;

public class TwoSumClosestLessThanTarget {

    public TwoSumClosestLessThanTarget() {
    }

    /**
     * Returns the two numbers whose sum is the largest sum strictly less than target,
     * as "firstNumber,secondNumber". Returns "-1" if no such pair exists.
     */
    public String maximumPairLessThanK(List<Integer> numbers, int target) {
        int bestSum = findBestSum(numbers, target);
        if (bestSum == -1) {
            return "-1";
        }
        return findEarliestPair(numbers, bestSum);
    }

    /**
     * Step 1: Finds the largest pair sum strictly less than target
     * using a sorted copy and two pointers. Returns -1 if there is none.
     */
    int findBestSum(List<Integer> numbers, int target) {
        // Sort a copy: the input may be read only, and Step 2 needs the original order.
        List<Integer> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);

        int bestSum = -1;
        int left = 0;
        int right = sorted.size() - 1;

        while (left < right) {
            int sum = sorted.get(left) + sorted.get(right);
            if (sum < target) {
                // Valid sum. Move left to try a bigger one.
                bestSum = Math.max(bestSum, sum);
                left++;
            } else {
                // Too big. Move right to make the sum smaller.
                right--;
            }
        }
        return bestSum;
    }

    /**
     * Step 2: Finds indices i < j with numbers[i] + numbers[j] == bestSum,
     * picking the smallest i first and then the smallest j.
     */
    String findEarliestPair(List<Integer> numbers, int bestSum) {
        // value -> smallest index of that value on the right side of i
        Map<Integer, Integer> smallestIndexOnRight = new HashMap<>();
        int first = -1;
        int second = -1;

        // Walk right to left, so the last match found has the smallest i.
        for (int i = numbers.size() - 1; i >= 0; i--) {
            int partner = bestSum - numbers.get(i);
            if (smallestIndexOnRight.containsKey(partner)) {
                first = i;
                second = smallestIndexOnRight.get(partner);
            }
            // Added after the check, so a number never pairs with itself.
            // Overwrites any older, bigger index for this value.
            smallestIndexOnRight.put(numbers.get(i), i);
        }
        return numbers.get(first) + "," + numbers.get(second);
    }
}
```