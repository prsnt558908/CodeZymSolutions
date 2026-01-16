class LongestContiguousArithmeticProgression:
    def __init__(self):
        pass

    def getLongestArithmeticSubarray(self, deviation):
        n = len(deviation)
        if n <= 2:
            return n

        a = [int(x) for x in deviation]

        # left[i] = length of the longest arithmetic subarray ending at i (no changes)
        left = [0] * n
        left[0] = 1
        left[1] = 2
        for i in range(2, n):
            d1 = a[i] - a[i - 1]
            d2 = a[i - 1] - a[i - 2]
            left[i] = left[i - 1] + 1 if d1 == d2 else 2

        # right[i] = length of the longest arithmetic subarray starting at i (no changes)
        right = [0] * n
        right[n - 1] = 1
        right[n - 2] = 2
        for i in range(n - 3, -1, -1):
            d1 = a[i + 1] - a[i]
            d2 = a[i + 2] - a[i + 1]
            right[i] = right[i + 1] + 1 if d1 == d2 else 2

        ans = max(left)  # no change

        # Change first or last element to extend a progression by 1
        ans = max(ans, right[1] + 1)
        ans = max(ans, left[n - 2] + 1)

        # Try changing each middle element
        for i in range(1, n - 1):
            # Extend the arithmetic run on the left or on the right by changing a[i]
            ans = max(ans, left[i - 1] + 1)
            ans = max(ans, right[i + 1] + 1)

            # Try to merge by changing a[i] so that both sides share the same difference d
            num = a[i + 1] - a[i - 1]
            if num % 2 == 0:  # need integer midpoint
                d = num // 2

                l = 1  # at least include a[i-1]
                if i >= 2 and (a[i - 1] - a[i - 2]) == d:
                    l = left[i - 1]

                r = 1  # at least include a[i+1]
                if i + 2 < n and (a[i + 2] - a[i + 1]) == d:
                    r = right[i + 1]

                ans = max(ans, l + 1 + r)

        return min(ans, n)
