class MinOperationsToUnbias:
    def __init__(self):
        pass

    def minOperationsToUnbias(self, n, data1, data2):
        """
        We may delete:
          - rightmost characters from data1 (i.e., keep a prefix of data1)
          - leftmost characters from data2 (i.e., keep a suffix of data2)

        After deletions, we merge the remaining strings by concatenation.
        Goal: total number of '0' equals total number of '1' in the merged result.

        Map: '1' -> +1, '0' -> -1. Then unbiased means total sum == 0.

        Remaining = prefix(data1, length p) + suffix(data2, starting at index j)
        Operations = (len(data1) - p) + j

        Time:  O(n)
        Space: O(n)
        """
        if data1 is None:
            data1 = ""
        if data2 is None:
            data2 = ""

        # Use only first n chars if longer; (constraints suggest length==n, but be robust)
        a = data1[: min(n, len(data1))]
        b = data2[: min(n, len(data2))]
        m, k = len(a), len(b)

        INF = 10**18

        # best_j[sum] = minimal j (deletions from left of b) such that suffix sum of b[j:] == sum
        # suffix sums range in [-k, k]
        offset = k
        best_j = [INF] * (2 * k + 1)

        # Empty suffix (delete all) has sum 0 with j = k
        best_j[offset] = k

        suff_sum = 0
        # compute sums of suffixes b[j:]
        for j in range(k - 1, -1, -1):
            suff_sum += 1 if b[j] == "1" else -1
            idx = suff_sum + offset
            if 0 <= idx < len(best_j):
                if j < best_j[idx]:
                    best_j[idx] = j

        ans = m + k  # always feasible by deleting everything

        pref_sum = 0
        # choose prefix length p from a
        for p in range(0, m + 1):
            if p > 0:
                pref_sum += 1 if a[p - 1] == "1" else -1

            target = -pref_sum
            if target < -k or target > k:
                continue

            j = best_j[target + offset]
            if j == INF:
                continue

            ops = (m - p) + j
            if ops < ans:
                ans = ops

        return int(ans)
