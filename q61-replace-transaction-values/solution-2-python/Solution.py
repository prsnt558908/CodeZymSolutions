from collections import Counter
from typing import List


class ReplaceValuesAfterEachTransaction:
    def __init__(self):
        pass

    def getSumsAfterTransactions(self, entries: List[int], transactions: List[List[int]]) -> List[int]:
        """
        Maintain:
        - freq[v] = how many times value v currently appears in entries
        - total_sum = current sum of entries

        For each [old_v, new_v]:
        - if old_v not present or old_v == new_v: sum unchanged
        - else replace all occurrences in O(1) using counts:
              total_sum += (new_v - old_v) * freq[old_v]
              freq[new_v] += freq[old_v]
              delete freq[old_v]
        """
        freq = Counter(entries)
        total_sum = 0
        for x in entries:
            total_sum += x

        ans: List[int] = []
        for old_v, new_v in transactions:
            if old_v != new_v:
                c = freq.get(old_v, 0)
                if c:
                    total_sum += (new_v - old_v) * c
                    freq[new_v] = freq.get(new_v, 0) + c
                    del freq[old_v]
            ans.append(total_sum)

        return ans
