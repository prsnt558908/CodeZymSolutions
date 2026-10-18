# Highest Average Student Score in Java

#### Problem Statement
[https://codezym.com/question/110-highest-average-student-score](https://codezym.com/question/110-highest-average-student-score)

Keep each student's total score and number of scores in two maps. Then compare their averages using cross multiplication. This gives an exact answer without rounding, and a name comparison settles ties.

## Start with a simple approach

For each distinct student, scan the entire list to calculate their average. With `n` entries and `k` students, this takes `O(n * k)` time, which can become `O(n²)`.

We can avoid these repeated scans by collecting everyone's totals and counts together.

## Collect totals and counts

Use two hash maps keyed by the student's name.

- `totals` stores the sum of that student's scores.
- `counts` stores how many scores that student has.

Split each entry at its comma, convert the score to an integer, and update both maps. Keep the name unchanged, including any spaces.

There is no need to store individual scores or sort the students.

## Compare averages exactly

Suppose the current student has total `total` and count `count`. The current winner has `bestTotal` and `bestCount`.

Instead of dividing, compare `total * bestCount` with `bestTotal * count`. Both counts are positive, so this preserves the order even when totals are negative.

If the products are equal, choose the smaller name using `compareTo`.

The statement gives no upper bound on score values. `BigInteger` is needed here to keep totals and products exact without overflow. The maps and the rest of the logic stay simple.

Begin with no winner. This lets the first student become the winner even if every average is negative.

## Small walkthrough

For `["Zoe,21", "Zoe,22", "Alice,21"]`, Zoe has total `43` and count `2`. Alice has total `21` and count `1`.

Compare `43 * 1` with `21 * 2`. Since `43 > 42`, return `"Zoe"`. Rounding both averages down would incorrectly make this a tie.

If Alice and Bob both average `-5`, return `"Alice"` because her name comes first.

## Why it works

The first pass adds every score to the correct total and increments the corresponding count. Each stored fraction therefore represents that student's exact average.

The second pass keeps the best student seen so far. Each comparison selects the higher average, or the smaller name when averages tie. After every student is checked, the stored winner is the required answer.

## Complexity

For `n` entries and `k` students, expected time is `O(n)` and extra space is `O(k)` when names and numbers have bounded length. Longer strings and larger integers also add parsing, comparison, and arithmetic costs.

## Java code

```java
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HighestAverageScore {

    public HighestAverageScore() {
    }

    public String highestAverageScore(List<String> marks) {
        Map<String, BigInteger> totals = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        // Collect each student's total and number of scores.
        for (String mark : marks) {
            int comma = mark.indexOf(',');
            String name = mark.substring(0, comma);
            BigInteger score = new BigInteger(mark.substring(comma + 1));

            totals.put(name, totals.getOrDefault(name, BigInteger.ZERO).add(score));
            counts.put(name, counts.getOrDefault(name, 0) + 1);
        }

        String bestName = null;
        BigInteger bestTotal = BigInteger.ZERO;
        int bestCount = 1;

        for (String name : totals.keySet()) {
            BigInteger total = totals.get(name);
            int count = counts.get(name);

            // Compare the two averages without division or rounding.
            BigInteger candidateProduct = total.multiply(BigInteger.valueOf(bestCount));
            BigInteger bestProduct = bestTotal.multiply(BigInteger.valueOf(count));
            int comparison = candidateProduct.compareTo(bestProduct);

            if (bestName == null || comparison > 0
                    || (comparison == 0 && name.compareTo(bestName) < 0)) {
                bestName = name;
                bestTotal = total;
                bestCount = count;
            }
        }

        return bestName;
    }
}
```
