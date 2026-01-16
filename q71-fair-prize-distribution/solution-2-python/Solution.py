from collections import Counter
from typing import List, Dict


class FairPrizeDistribution:
    def __init__(self):
        pass

    def findFairDistribution(self, points: List[int], values: List[int]) -> List[int]:
        """
        Returns the lexicographically smallest fair prize distribution.

        Rules:
        - Same score => same prize value (so if a score occurs k times, the chosen value must have >= k copies).
        - Higher score => strictly higher prize value.
        - Each element in 'values' can be used at most once.

        Strategy (greedy, correct):
        - Count frequency of each score group, sort scores ascending.
        - Count occurrences of each prize value, sort distinct prize values ascending.
        - For each score group in increasing score order, pick the smallest prize value (in increasing order)
          that has enough copies for that group's size. Move to the next larger prize value for the next group.
        - If at any point no such prize value exists, return [].
        """
        n = len(points)
        m = len(values)

        if n == 0:
            return []
        if m < n:
            return []

        score_freq: Counter[int] = Counter(points)
        sorted_scores = sorted(score_freq.keys())
        needs = [score_freq[s] for s in sorted_scores]

        value_counts: Counter[int] = Counter(values)
        sorted_value_items = sorted(value_counts.items())  # (value, count) by increasing value

        # Greedy assignment of score -> prize value
        score_to_prize: Dict[int, int] = {}
        p = 0  # pointer over sorted_value_items

        for i, score in enumerate(sorted_scores):
            req = needs[i]
            while p < len(sorted_value_items) and sorted_value_items[p][1] < req:
                p += 1
            if p == len(sorted_value_items):
                return []
            chosen_value = sorted_value_items[p][0]
            score_to_prize[score] = chosen_value
            p += 1  # next score must map to a strictly larger prize value

        # Build result in original participant order
        return [score_to_prize[s] for s in points]
