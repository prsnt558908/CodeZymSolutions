class MaximumStorageEfficiency:
    def __init__(self):
        pass

    def getMaximumStorageEfficiency(self, numSegments, m):
        """
        Binary search the answer x (minimum segments per storage unit).
        For a given x, task with s segments can form at most floor(s / x) units
        (each unit has at least x segments). We need total units >= m.
        """
        if not numSegments:
            return 0  # not expected per constraints

        lo, hi = 1, min(numSegments)
        ans = 1

        while lo <= hi:
            mid = (lo + hi) // 2

            units = 0
            for s in numSegments:
                units += s // mid
                if units >= m:  # early stop
                    break

            if units >= m:
                ans = mid
                lo = mid + 1
            else:
                hi = mid - 1

        return ans
