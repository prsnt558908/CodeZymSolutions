from typing import List
import heapq


class MinimizeTotalOverhead:
    def __init__(self):
        pass

    def determineOptimalSamples(self, samples: str) -> str:
        """
        Replace all '?' with lowercase letters to minimize total overhead.

        Key observation:
        Total overhead equals sum over letters of C(freq(letter), 2),
        because each occurrence contributes the number of same letters before it.

        To minimize this, we should distribute '?' among letters with the smallest current frequency
        (convex cost). For ties, pick the smallest letter to enable lexicographically smallest result.

        After choosing the multiset of replacement letters, to get the lexicographically smallest
        final string among all minimum-overhead solutions, sort the chosen letters and fill '?' from
        left to right with that sorted list.
        """
        n = len(samples)
        freq = [0] * 26
        q_positions: List[int] = []

        for i, ch in enumerate(samples):
            if ch == "?":
                q_positions.append(i)
            else:
                freq[ord(ch) - 97] += 1

        # Min-heap of (current_frequency, letter_index). Tie by smaller letter_index automatically.
        heap = [(freq[i], i) for i in range(26)]
        heapq.heapify(heap)

        chosen: List[int] = []
        for _ in range(len(q_positions)):
            f, li = heapq.heappop(heap)
            chosen.append(li)
            f += 1
            heapq.heappush(heap, (f, li))

        chosen.sort()

        arr = list(samples)
        for pos, li in zip(q_positions, chosen):
            arr[pos] = chr(97 + li)

        return "".join(arr)
