from typing import List


class MaximumSequentialWorkloadPerMachine:
    def __init__(self):
        pass

    def getMaxWorkloadDone(self, performance: List[int], workload: List[int]) -> List[int]:
        n = len(workload)

        # Prefix sums (Python int is unbounded, acts like Java long here)
        prefix = [0] * (n + 1)
        s = 0
        for i, w in enumerate(workload):
            s += int(w)
            prefix[i + 1] = s

        # Segment tree storing max on ranges of workload, to find first index with workload[idx] > cap in O(log n)
        size = 1
        while size < n:
            size <<= 1

        seg = [0] * (2 * size)
        for i in range(n):
            seg[size + i] = int(workload[i])
        for i in range(size - 1, 0, -1):
            seg[i] = seg[2 * i] if seg[2 * i] > seg[2 * i + 1] else seg[2 * i + 1]

        def first_index_greater_than(cap: int) -> int:
            if n == 0:
                return 0
            if seg[1] <= cap:
                return n  # all jobs fit

            idx = 1
            l, r = 0, size
            while idx < size:
                left = idx * 2
                mid = (l + r) // 2
                if seg[left] > cap:
                    idx = left
                    r = mid
                else:
                    idx = left + 1
                    l = mid
            pos = idx - size
            return pos if pos < n else n

        ans: List[int] = []
        for cap in performance:
            j = first_index_greater_than(int(cap))
            ans.append(prefix[j])
        return ans
