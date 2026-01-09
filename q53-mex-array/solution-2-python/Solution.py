import heapq
from typing import List

class MaximumMexArray:
    def __init__(self):
        pass

    def getMaxArray(self, data_packets: List[int]) -> List[int]:
        """
        Greedy:
        - Let S be the remaining suffix.
        - The first output should be mex(S) (can't do better with any prefix).
        - If mex(S)=m>0: take the smallest prefix that contains all 0..m-1 at least once.
        - If mex(S)=0: every prefix has mex 0, so take k=1 repeatedly to maximize length.
        """
        n = len(data_packets)
        a = data_packets

        max_val = n + 1  # mex is never > n, tracking up to n+1 is safe

        # freq only for values in [0..max_val]; ignore bigger values (they can't affect mex)
        freq = [0] * (max_val + 1)
        for x in a:
            if 0 <= x <= max_val:
                freq[x] += 1

        # Min-heap of missing numbers in [0..max_val]
        # We'll lazily discard values that aren't missing anymore.
        missing = []
        for v in range(max_val + 1):
            if freq[v] == 0:
                heapq.heappush(missing, v)

        def current_mex() -> int:
            while missing and freq[missing[0]] != 0:
                heapq.heappop(missing)
            return missing[0]  # always exists because we track up to n+1

        res: List[int] = []
        seen_at = [0] * (max_val + 1)  # timestamp trick for segment-local seen
        seg_id = 0

        i = 0
        while i < n:
            mex = current_mex()

            if mex == 0:
                # 0 absent in suffix => every prefix mex is 0, maximize length => k=1 repeatedly
                while i < n:
                    res.append(0)
                    x = a[i]
                    if 0 <= x <= max_val:
                        freq[x] -= 1
                        if freq[x] == 0:
                            heapq.heappush(missing, x)
                    i += 1
                break

            # Build smallest prefix starting at i that contains all numbers 0..mex-1
            seg_id += 1
            need = mex
            j = i
            while need > 0:
                v = a[j]
                if 0 <= v < mex and seen_at[v] != seg_id:
                    seen_at[v] = seg_id
                    need -= 1
                j += 1

            res.append(mex)

            # Remove [i..j-1] from suffix
            while i < j:
                x = a[i]
                if 0 <= x <= max_val:
                    freq[x] -= 1
                    if freq[x] == 0:
                        heapq.heappush(missing, x)
                i += 1

        return res
