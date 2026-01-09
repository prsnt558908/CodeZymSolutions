class StringSortOperations:
    def __init__(self):
        pass

    def getMinimumOperations(self, strValue):
        n = len(strValue)
        if n <= 1:
            return 0

        # 0 operations if already sorted (non-decreasing)
        for i in range(n - 1):
            if strValue[i] > strValue[i + 1]:
                break
        else:
            return 0

        # Count letters (only lowercase a-z)
        freq = [0] * 26
        for ch in strValue:
            freq[ord(ch) - 97] += 1

        min_idx = next(i for i in range(26) if freq[i] > 0)
        max_idx = next(i for i in range(25, -1, -1) if freq[i] > 0)
        min_ch = chr(97 + min_idx)
        max_ch = chr(97 + max_idx)

        # If either end already has the correct extreme, 1 operation suffices:
        # - If first char is global minimum: sort substring [1..n-1]
        # - If last  char is global maximum: sort substring [0..n-2]
        if strValue[0] == min_ch or strValue[-1] == max_ch:
            return 1

        # Otherwise both ends are "wrong". It's 3 iff:
        # - global minimum appears only at the last position, AND
        # - global maximum appears only at the first position
        if (
            strValue[-1] == min_ch and freq[min_idx] == 1 and
            strValue[0] == max_ch and freq[max_idx] == 1
        ):
            return 3

        # In all other cases, 2 operations suffice.
        return 2
