# Maximum Employees Earning Equal Pay After Raises in Java

#### Problem Statement
[https://codezym.com/question/478-equal-pay-after-raises](https://codezym.com/question/478-equal-pay-after-raises)

Sort the salaries, then use a sliding window to find the largest group we can raise to the same salary. The highest salary in the window is the target. A running sum lets us check the cost quickly, and we remove the smallest salaries whenever the cost exceeds the budget.


## Start with a simpler solution

After sorting, try each salary as the target. Start with that employee and scan left, adding the cost of raising each smaller salary to the target. Stop when adding another employee would exceed the budget.

This works, but it can repeat the same work for many targets. It takes `O(n²)` time in the worst case, which is too slow for `100,000` employees.


## Why sorting helps

For any chosen group, the cheapest target is its highest existing salary. A lower target would require a pay cut. A higher target would spend more without adding employees to that group.

For a fixed target and group size, choose the salaries closest to the target from below, including salaries already equal to it. Replacing a smaller salary with a larger eligible salary never increases the cost. These choices form a consecutive window in the sorted list.

So we only need to consider sorted windows whose employees are raised to the salary at the right end.


## Improve it with a sliding window

Keep two positions, `left` and `right`, and the sum of the salaries between them.

For a window of `count` employees, raising everyone to `target` costs:

```text
cost = target * count - windowSum
```

Move `right` forward to include the next salary. While the cost exceeds `raiseBudget`, remove the salary at `left` from the sum and move `left` forward. Record the largest affordable window.

As the target increases, a previously unaffordable left boundary cannot become affordable. Existing employees need at least as much money to reach the new target. This lets `left` move only forward.


## Walk through an example

For `salaries = [52, 47, 50, 58, 49]` and `raiseBudget = 6`, sorting gives `[47, 49, 50, 52, 58]`.

The window `[47, 49, 50]` costs `50 * 3 - 146 = 4`, so three employees can earn `50`.

Adding `52` makes the cost `52 * 4 - 198 = 10`. Remove `47`, leaving `[49, 50, 52]`. Its cost is `52 * 3 - 151 = 5`, so the group size remains three.

When `58` is added, the window must shrink to `[52, 58]`, which costs `6`. The largest affordable group found is `3`.


## Why the answer is correct

An optimal group can be represented by a consecutive sorted window with its highest salary as the target.

For each right end, the algorithm removes leftmost salaries only while the window is too expensive. Every removed boundary also stays too expensive for future, higher targets. The remaining window is therefore the largest affordable window ending at that position.

Checking every right end and keeping the largest size gives the answer.


## Data structures and complexity

`MaxEqualPayGroup` keeps the required constructor and method. Each call uses local variables, so results from earlier calls do not affect later calls.

An `ArrayList<Integer>` stores a sorted copy of the salaries and gives fast access at both window positions. The caller's list keeps its original order.

Use `long` for the running sum and cost calculation. With `100,000` salaries of up to `100,000`, the sum and target-times-count product can reach `10,000,000,000`. Casting `target` to `long` before multiplication prevents integer overflow.

Sorting takes `O(n log n)` time. The window scan takes `O(n)` because each position moves forward at most `n` times. Total time is `O(n log n)`, and extra space is `O(n)` for the copy and sorting.


## Java solution

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MaxEqualPayGroup {

    public MaxEqualPayGroup() {
    }

    /**
     * Returns the largest group that can reach equal pay within the budget.
     */
    public int maxEqualPayGroup(List<Integer> salaries, int raiseBudget) {
        // Sort a copy so the caller's list keeps its original order.
        ArrayList<Integer> sortedSalaries = new ArrayList<>(salaries);
        Collections.sort(sortedSalaries);

        int left = 0;
        int best = 1;
        long windowSum = 0;

        for (int right = 0; right < sortedSalaries.size(); right++) {
            int target = sortedSalaries.get(right);
            windowSum += target;

            // Raise the window to target, removing salaries until it fits.
            while ((long) target * (right - left + 1) - windowSum > raiseBudget) {
                windowSum -= sortedSalaries.get(left);
                left++;
            }

            best = Math.max(best, right - left + 1);
        }

        return best;
    }
}
```
