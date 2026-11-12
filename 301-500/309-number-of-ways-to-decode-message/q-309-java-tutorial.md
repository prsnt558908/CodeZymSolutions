# Total Number of Ways to Decode a Message in Java

#### Problem Statement

[https://codezym.com/question/309-number-of-ways-to-decode-message](https://codezym.com/question/309-number-of-ways-to-decode-message)

Each letter consumes either one digit or two digits. Try both valid choices and add the ways to decode what remains. Dynamic programming avoids repeating the same work. We can calculate from right to left using just two counts, giving `O(n)` time and `O(1)` extra space.


## Start simple: try every valid choice

Let `countWays(s, index)` count the decodings starting at `index`. Reaching the end returns `1`, because one complete decoding has been found. Starting with `0` returns `0`.

Otherwise, take one digit. Also try two digits when their value is from `10` to `26`. This helper belongs inside `DecodeWays` and starts with `countWays(s, 0)`.

```java
public int countWays(String s, int index) {
    if (index == s.length()) {
        return 1;
    }
    if (s.charAt(index) == '0') {
        return 0;
    }

    int ways = countWays(s, index + 1);
    if (index + 1 < s.length()) {
        int pair = (s.charAt(index) - '0') * 10
                + (s.charAt(index + 1) - '0');
        if (pair >= 10 && pair <= 26) {
            ways += countWays(s, index + 2);
        }
    }
    return ways;
}
```

For `"1111"`, several branches reach the same remaining substring. Recomputing it makes the recursion exponential, with an `O(2^n)` upper bound and `O(n)` stack space.


## Improve it: reuse the next two counts

Imagine an array where `dp[i]` stores the ways to decode the substring starting at `i`. Set `dp[n] = 1` for an empty remainder.

Moving right to left:

- A `0` at `i` gives `dp[i] = 0`.
- Otherwise, taking one digit contributes `dp[i + 1]`.
- If the next two digits form `10` through `26`, also add `dp[i + 2]`.

Only the next two entries are needed. Replace the array with `nextWays` for `dp[i + 1]` and `nextNextWays` for `dp[i + 2]`.

Start with `nextWays = 1`. Set `nextNextWays = 0` as an unused placeholder. After each position, shift the old `nextWays` into `nextNextWays`, then put the new count into `nextWays`.


## Check zeroes before counting

A zero must be the second digit of `10` or `20`. Reject a leading zero, or any zero whose previous digit is neither `1` nor `2`. This rejects `"06"`, `"301"`, and `"100"`.

If the check passes, at least one decoding exists. Pair each zero with its previous digit and decode the other digits individually.

This also keeps `int` arithmetic safe. After this check, every suffix starting with a nonzero digit has a decodable prefix before it. Joining one fixed prefix decoding to each suffix decoding gives distinct full decodings. Each suffix count therefore fits within the guaranteed 32-bit answer.


## Walk through `"11106"`

The empty remainder has `1` way. Moving left, `"6"` has `1` way and `"06"` has `0` ways.

For `"106"`, taking `1` leaves the invalid `"06"`. Taking `10` leaves `"6"`, so the count is `1`.

For `"1106"`, taking `1` contributes `1`. Taking `11` leaves `"06"`, adding `0`. The count stays `1`.

For `"11106"`, taking `1` leaves `"1106"` and taking `11` leaves `"106"`. Each contributes `1`, giving **2** decodings: `1,1,10,6` and `11,10,6`.


## Why it works

Every valid decoding starts with a valid one-digit or two-digit group. These choices have different first letters, so their counts do not overlap. Adding the already computed counts of their remainders includes every valid decoding exactly once.

The zero check and counting pass each take `O(n)` time. Only a few counters are stored, so extra space is `O(1)` under the given bounds.


## Complete Java code

```java
public class DecodeWays {

    public DecodeWays() {
    }

    /** Returns the number of valid decodings of the complete message. */
    public int numDecodings(String s) {
        int n = s.length();

        if (s.charAt(0) == '0') {
            return 0;
        }

        // Every zero must be the second digit of 10 or 20.
        for (int index = 1; index < n; index++) {
            if (s.charAt(index) == '0'
                    && s.charAt(index - 1) != '1'
                    && s.charAt(index - 1) != '2') {
                return 0;
            }
        }

        int nextWays = 1; // One way to finish an empty remainder.
        int nextNextWays = 0;

        for (int index = n - 1; index >= 0; index--) {
            int currentWays = 0;

            if (s.charAt(index) != '0') {
                currentWays = nextWays;

                if (index + 1 < n) {
                    int pair = (s.charAt(index) - '0') * 10
                            + (s.charAt(index + 1) - '0');
                    if (pair >= 10 && pair <= 26) {
                        currentWays += nextNextWays;
                    }
                }
            }

            // Shift both counts for the next position to the left.
            nextNextWays = nextWays;
            nextWays = currentWays;
        }

        return nextWays;
    }
}
```
