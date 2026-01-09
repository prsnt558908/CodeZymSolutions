from dataclasses import dataclass
from typing import List


@dataclass
class CountProductiveTeams:
    def __init__(self):
        pass

    """
    Counts number of quadruples (x, y, z, w) such that:
      - x < y < z < w
      - level[x] < level[z]
      - level[y] > level[w]

    Time:  O(n^2)
    Space: O(n)

    NOTE: For n up to 3000, the count can exceed 32-bit int.
    Python int is unbounded, so we return the exact value.
    """
    def countProductiveTeams(self, level: List[int]) -> int:
        n = len(level)
        rank = self._compress_to_ranks(level)  # ranks in [1..n], preserves < and >

        freq = [0] * (n + 2)   # freq[valueRank] among indices x < y
        pref = [0] * (n + 2)   # prefix sums of freq
        ans = 0

        for y in range(n):
            # Build prefix sums so we can query: #x<y with rank[x] < someRank in O(1)
            pref[0] = 0
            for v in range(1, n + 1):
                pref[v] = pref[v - 1] + freq[v]

            rank_y = rank[y]

            # Scan z from right to left, maintaining:
            # c = #w in (z..n-1] such that rank[w] < rank_y  (i.e., level[y] > level[w])
            c = 0
            for z in range(n - 2, y, -1):  # z = n-2 .. y+1
                add_rank = rank[z + 1]
                if add_rank < rank_y:
                    c += 1

                # left = #x < y such that rank[x] < rank[z]  (i.e., level[x] < level[z])
                left = pref[rank[z] - 1]

                ans += left * c

            # Add current y value into prefix multiset for next iterations
            freq[rank_y] += 1

        return ans

    # Coordinate compression to ranks 1..n (preserves comparisons, works even if values aren't exactly 1..n)
    def _compress_to_ranks(self, a: List[int]) -> List[int]:
        sorted_vals = sorted(a)
        pos_map = {val: i + 1 for i, val in enumerate(sorted_vals)}  # 1..n (all distinct)
        return [pos_map[x] for x in a]
