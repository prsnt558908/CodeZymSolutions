from typing import List, Dict, Tuple


class MinimumStorageCost:
    def __init__(self):
        pass

    def getMinimumStorageCost(self, n: int, encCost: int, flatCost: int, sensitiveFiles: List[int]) -> int:
        """
        Returns the minimum total storage cost following the rules:
        - Cost of a batch of size M with X sensitive files:
            * flatCost if X == 0
            * M * X * encCost if X > 0
        - If M is even, you may either store whole or split into two equal contiguous halves.
        """
        if n <= 0:
            return 0

        # Mark sensitive files (ignore out-of-range values defensively)
        is_sens = [0] * (n + 1)  # 1-indexed
        for f in sensitiveFiles:
            if 1 <= f <= n:
                is_sens[f] = 1

        # Prefix sums to query #sensitive in any interval [l..r] in O(1)
        pref = [0] * (n + 1)
        for i in range(1, n + 1):
            pref[i] = pref[i - 1] + is_sens[i]

        def sensitive_count(l: int, r: int) -> int:
            return pref[r] - pref[l - 1]

        memo: Dict[Tuple[int, int], int] = {}

        def dfs(l: int, length: int) -> int:
            key = (l, length)
            if key in memo:
                return memo[key]

            r = l + length - 1
            x = sensitive_count(l, r)

            # Cost if we store this batch as-is
            if x == 0:
                whole = flatCost
            else:
                whole = length * x * encCost

            # If length is odd, we cannot split
            if length % 2 == 1:
                memo[key] = whole
                return whole

            half = length // 2
            split_cost = dfs(l, half) + dfs(l + half, half)
            ans = whole if whole < split_cost else split_cost
            memo[key] = ans
            return ans

        return dfs(1, n)
