# Bitwise XOR Subsequences in Java

#### Problem Statement

[https://codezym.com/question/116-bitwise-xor-subsequences](https://codezym.com/question/116-bitwise-xor-subsequences)

We process the list from left to right. For a value `x`, only a valid subsequence ending in `x ^ k` can be extended. We use dynamic programming with a hash map to remember the longest length for each ending value. This gives an expected `O(n)` solution.

## Understand the condition

Find the longest subsequence in which every adjacent pair has XOR equal to `k`. We can skip elements, but we must keep their original order.

For example, with `arr = [4, 6, 4]` and `k = 2`, the answer is `3` because both `4 ^ 6` and `6 ^ 4` equal `2`.

A single element is always valid. We check adjacent pairs, not the XOR of the entire subsequence.

## Start with a simple approach

Let `dp[i]` be the longest valid subsequence ending at index `i`.

Start with length `1`. Check every earlier index `j`. If `(arr[j] ^ arr[i]) == k`, we can extend that subsequence and consider length `dp[j] + 1`.

This takes `O(n²)` time and `O(n)` extra space. Checking all earlier elements is too slow when the list can contain `100,000` elements.

## Replace the scan with a hash map

If the current value is `x`, its previous value must be `x ^ k`. This follows from the XOR identity: if `previous ^ x = k`, then `previous = x ^ k`.

Store `best[value]`, the longest valid subsequence ending in `value` among the elements already processed. We only need one length per value because subsequences with the same last value can accept exactly the same next values.

For each `x`:

1. Find its required previous value, `partner = x ^ k`.
2. Read `best[partner]`, using `0` when it is absent. Add `1` for `x`.
3. Update `best[x]` with the larger of its previous length and this new length.
4. Update the overall answer.

A missing partner gives length `1`, which represents choosing `x` alone.

Read the partner's length before updating the map. When `k = 0`, the partner is `x` itself. Reading first makes sure we extend an earlier occurrence and use the current element only once.

## Walk through an example

Take `arr = [4, 4, 6, 9, 4]` and `k = 2`.

- First `4`: its partner is `6`, which is absent. Store `best[4] = 1`.
- Second `4`: `6` is still absent. Keep `best[4] = 1`.
- `6`: its partner is `4`. Store `best[6] = 2`.
- `9`: its partner is `11`, which is absent. Store `best[9] = 1`.
- Last `4`: its partner is `6`. Store `best[4] = 3`.

The answer is `3`, from the subsequence `[4, 6, 4]`.

## Why this works

Before processing `x`, the map contains the best lengths using only earlier elements. Every valid subsequence that uses `x` as its last element either contains only `x` or has a previous element equal to `x ^ k`.

The map gives the longest subsequence with that required ending value. Appending `x` therefore gives the longest valid subsequence using the current element. Keeping the larger length preserves any earlier best result for `x`. Taking the maximum across all ending values gives the answer.

## Java code

`HashMap` stores one length per distinct value. All working data stays inside the method, so separate calls on the same object are independent.

```java
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BitwiseXorSubsequences {

    public BitwiseXorSubsequences() {
    }

    public int longestValidSubsequence(List<Integer> arr, int k) {
        // best[value] is the longest valid length ending in value so far.
        Map<Integer, Integer> best = new HashMap<>();
        int answer = 0;

        for (int value : arr) {
            int partner = value ^ k;

            // Read before updating, especially when k is zero.
            int length = best.getOrDefault(partner, 0) + 1;
            length = Math.max(length, best.getOrDefault(value, 0));

            best.put(value, length);
            answer = Math.max(answer, length);
        }

        return answer;
    }
}
```

## Complexity

For `n` elements and `d` distinct values, the expected time is `O(n)` because each element performs a constant number of hash map operations. Extra space is `O(d)`, which is at most `O(n)`.

The same code handles `k = 0`, repeated values, a single element, and inputs with no matching pair.
