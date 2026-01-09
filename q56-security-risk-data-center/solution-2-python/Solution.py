from typing import List

class SecurityRiskCalculator:
    MOD = 1_000_000_007

    def __init__(self) -> None:
        pass

    def calculateTotalRisk(self, security: List[int]) -> int:
        """
        Sum over all subarrays: (length of subarray) * (maximum element in subarray), modulo 1e9+7.

        For each index i, count subarrays where security[i] is the chosen maximum (tie-broken):
        - left boundary uses previous STRICTLY greater element (>)
        - right boundary uses next GREATER OR EQUAL element (>=)
        This assigns each subarray to the rightmost occurrence among equal maxima.
        """
        n = len(security)
        a = security

        prev_greater = [-1] * n   # previous index with value > a[i]
        next_ge = [n] * n         # next index with value >= a[i]

        # prev_greater: maintain strictly decreasing stack by value
        st = []
        for i in range(n):
            while st and a[st[-1]] <= a[i]:
                st.pop()
            prev_greater[i] = st[-1] if st else -1
            st.append(i)

        # next_ge: from right, maintain stack where top is >= current
        st.clear()
        for i in range(n - 1, -1, -1):
            while st and a[st[-1]] < a[i]:
                st.pop()
            next_ge[i] = st[-1] if st else n
            st.append(i)

        ans = 0
        MOD = self.MOD

        for i in range(n):
            L = i - prev_greater[i]      # choices for left end
            R = next_ge[i] - i           # choices for right end

            # Sum of lengths over all subarrays where i is chosen maximum:
            # sum_{x=1..L} sum_{y=1..R} (x + y - 1)
            # = (L*(L+1)/2)*R + (R*(R+1)/2)*L - L*R
            triL = (L * (L + 1) // 2) % MOD
            triR = (R * (R + 1) // 2) % MOD

            term1 = (triL * (R % MOD)) % MOD
            term2 = (triR * (L % MOD)) % MOD
            term3 = ((L % MOD) * (R % MOD)) % MOD

            len_sum = (term1 + term2 - term3) % MOD
            ans = (ans + (a[i] % MOD) * len_sum) % MOD

        return ans

