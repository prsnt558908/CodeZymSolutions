class MaxMinMedianSubsequences:
    def __init__(self):
        pass

    # Returns [maxMedian, minMedian]
    def getMaxMinMedian(self, values, k):
        if values is None:
            raise ValueError("values is None")
        n = len(values)
        if k < 1 or k > n:
            raise ValueError("k must be in [1, n]")

        a = sorted(values)

        # Minimum possible median among all length-k subsequences
        min_median = a[(k - 1) // 2]

        # Maximum possible median among all length-k subsequences
        # Need at least (floor(k/2) + 1) elements >= median in the chosen subsequence.
        need_on_right = (k // 2) + 1
        max_median = a[n - need_on_right]

        return [max_median, min_median]
