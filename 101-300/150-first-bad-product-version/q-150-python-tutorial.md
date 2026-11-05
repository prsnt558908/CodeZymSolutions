# First Bad Product Version in Python

#### Problem Statement

[https://codezym.com/question/150-first-bad-product-version](https://codezym.com/question/150-first-bad-product-version)

The versions have a useful order: all good versions come before all bad versions. Binary search uses this order to find the first bad version. We check the middle of the remaining range, then keep only the half that can contain the answer.


## 1. Start with a simple scan

Check versions from `1` onward and stop at the first bad one.

```python
for version in range(1, n + 1):
    if self.isBadVersion(version):
        return version
```

This works because a bad version is guaranteed to exist. However, if only the last version is bad, we make `n` API calls. We can avoid checking every version.


## 2. Use binary search

Keep two boundaries, `left = 1` and `right = n`. The first bad version must stay inside this range.

For each middle version `mid`:

- If it is bad, keep `mid` and everything before it in the current range. Set `right = mid`.
- If it is good, every version up to `mid` is also good. Set `left = mid + 1`.

Do not use `right = mid - 1` when `mid` is bad. That would discard `mid`, even though it could be the first bad version.

Stop when `left == right`. This is the only remaining candidate.


### Compute an integer midpoint

Use integer division to keep the midpoint a version number:

```python
mid = left + (right - left) // 2
```

Python integers do not have a fixed 32-bit limit, so the sum of the boundaries does not overflow. This difference-based formula also works safely with fixed-width integers, which addresses the problem's overflow follow-up.


## 3. Walk through an example

Suppose `n = 5` and the first bad version is `4`.

1. Start with `[1, 5]`. Check version `3`. It is good, so move `left` to `4`.
2. The range is now `[4, 5]`. Check version `4`. It is bad, so move `right` to `4`.
3. Both boundaries are `4`. Return `4`.

Only two API calls are needed. When `n = 1`, the loop makes no calls because the only version is guaranteed to be bad.


## 4. Why it works and what it costs

Initially, the range contains the answer. A good middle version rules out itself and everything before it. A bad middle version rules out everything after it as the first bad version. Both updates keep the answer inside the range and make the range smaller. When one candidate remains, it must be the answer.

Each check reduces the candidate count to at most half, rounded up. The solution uses **O(log n) time and API calls**, assuming each API call takes constant time, and **O(1) extra space** under the given 32-bit input limit. At the largest allowed `n`, it needs at most **31 calls**.

This is optimal in the worst case. Each API response has two possible outcomes, so distinguishing among `n` possible answers requires at least `ceil(log2(n))` calls.


## 5. Complete Python solution

`FirstBadVersion` matches the supplied starter class. The constructor value represents the first bad version. We name it `bad` for clarity, separate from the total version count `n` passed to `firstBadVersion`. The dataclass creates the constructor that stores this value.

`isBadVersion` simulates the provided API. The search never reads `bad` directly. It learns about versions only through API calls. No collection is needed, only two boundaries and a midpoint.

```python
from dataclasses import dataclass


@dataclass
class FirstBadVersion:
    bad: int

    def firstBadVersion(self, n: int) -> int:
        left = 1
        right = n

        while left < right:
            # Use integer division to keep mid a version number.
            mid = left + (right - left) // 2

            if self.isBadVersion(mid):
                # mid may itself be the first bad version.
                right = mid
            else:
                # All versions through mid are good.
                left = mid + 1

        return left

    def isBadVersion(self, version: int) -> bool:
        # Simulate the API using the constructor's hidden value.
        return version >= self.bad
```
