# First Bad Product Version in Java

#### Problem Statement

[https://codezym.com/question/150-first-bad-product-version](https://codezym.com/question/150-first-bad-product-version)

The versions have a useful order: all good versions come before all bad versions. Binary search uses this order to find the first bad version. We check the middle of the remaining range, then keep only the half that can contain the answer.


## 1. Start with a simple scan

Check versions from `1` onward and stop at the first bad one.

```java
for (int version = 1; version <= n; version++) {
    if (isBadVersion(version)) {
        return version;
    }
}
```

This works because a bad version is guaranteed to exist. However, if only the last version is bad, we make `n` API calls. We can avoid checking every version.


## 2. Use binary search

Keep two boundaries, `left = 1` and `right = n`. The first bad version must stay inside this range.

For each middle version `mid`:

- If it is bad, keep `mid` and everything before it in the current range. Set `right = mid`.
- If it is good, every version up to `mid` is also good. Set `left = mid + 1`.

Do not use `right = mid - 1` when `mid` is bad. That would discard `mid`, even though it could be the first bad version.

Stop when `left == right`. This is the only remaining candidate.


### Avoid midpoint overflow

The constraint allows `n` to reach `2,147,483,647`. Adding `left + right` can overflow an `int`, even when both boundaries are valid.

Use this instead:

```java
int mid = left + (right - left) / 2;
```

The difference is nonnegative and fits in an `int`. Adding half that difference to `left` produces a value no larger than `right`.


## 3. Walk through an example

Suppose `n = 5` and the first bad version is `4`.

1. Start with `[1, 5]`. Check version `3`. It is good, so move `left` to `4`.
2. The range is now `[4, 5]`. Check version `4`. It is bad, so move `right` to `4`.
3. Both boundaries are `4`. Return `4`.

Only two API calls are needed. When `n = 1`, the loop makes no calls because the only version is guaranteed to be bad.


## 4. Why it works and what it costs

Initially, the range contains the answer. A good middle version rules out itself and everything before it. A bad middle version rules out everything after it as the first bad version. Both updates keep the answer inside the range and make the range smaller. When one candidate remains, it must be the answer.

Each check reduces the candidate count to at most half, rounded up. The solution uses **O(log n) time and API calls**, assuming each API call takes constant time, and **O(1) extra space**. At the largest allowed `n`, it needs at most **31 calls**.

This is optimal in the worst case. Each API response has two possible outcomes, so distinguishing among `n` possible answers requires at least `ceil(log2(n))` calls.


## 5. Complete Java solution

`FirstBadVersion` matches the supplied starter class. Its constructor stores `bad` so that `isBadVersion` can simulate the provided API. The search never reads `bad` directly. It learns about versions only through API calls.

No collection is needed. The search uses only two boundaries and a midpoint.

```java
public class FirstBadVersion {
    int bad;

    public FirstBadVersion(int bad) {
        this.bad = bad;
    }

    public int firstBadVersion(int n) {
        int left = 1;
        int right = n;

        while (left < right) {
            // Compute the midpoint without overflowing an int.
            int mid = left + (right - left) / 2;

            if (isBadVersion(mid)) {
                // mid may itself be the first bad version.
                right = mid;
            } else {
                // All versions through mid are good.
                left = mid + 1;
            }
        }

        return left;
    }

    public boolean isBadVersion(int version) {
        // Simulate the API using the constructor's hidden value.
        return version >= bad;
    }
}
```
