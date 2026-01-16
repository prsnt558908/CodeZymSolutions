import random
from typing import List, Optional


class CartRemovalsWithinBudget:
    """
    For each prefix [0..i], keep item i, and remove the minimum number of earlier items
    so that the remaining sum <= budget.

    Approach (no Fenwick):
    - Maintain a randomized Treap (BST by key, heap by priority) over previous item values.
    - Each node stores:
        - key (value), cnt (frequency)
        - size (total count in subtree), sum (total sum in subtree)
    - For each i:
        capacity = budget - cartItems[i]
        kept = maximum count of smallest previous items with sum <= capacity
        removals = i - kept
        insert cartItems[i] into treap
    """

    def __init__(self):
        self._treap = _Treap(seed=1)

    def getMinRemovalsWithinBudget(self, budget: int, cartItems: List[int]) -> List[int]:
        ans: List[int] = []

        for i, v in enumerate(cartItems):
            cap = budget - v

            # Per constraints, v <= budget, so cap >= 0. Defensive fallback anyway.
            if cap < 0:
                ans.append(i)
            else:
                kept_from_prev = self._treap.max_count_within_sum(cap)
                ans.append(i - kept_from_prev)

            self._treap.insert(v)

        return ans


class _Node:
    __slots__ = ("key", "prio", "cnt", "size", "sum", "left", "right")

    def __init__(self, key: int, prio: int):
        self.key = key
        self.prio = prio
        self.cnt = 1
        self.size = 1
        self.sum = key
        self.left: Optional["_Node"] = None
        self.right: Optional["_Node"] = None

    def recalc(self) -> None:
        left_size = self.left.size if self.left else 0
        right_size = self.right.size if self.right else 0
        left_sum = self.left.sum if self.left else 0
        right_sum = self.right.sum if self.right else 0

        self.size = left_size + self.cnt + right_size
        self.sum = left_sum + self.key * self.cnt + right_sum


class _Treap:
    def __init__(self, seed: int = 1):
        self._rnd = random.Random(seed)
        self._root: Optional[_Node] = None

    def insert(self, key: int) -> None:
        # Split into <= key and > key
        left, right = self._split_le(self._root, key)
        # Split left into <= key-1 and == key
        ll, eq = self._split_le(left, key - 1)

        if eq is None:
            eq = _Node(key, self._rnd.randint(-(1 << 30), (1 << 30) - 1))
        else:
            eq.cnt += 1
            eq.recalc()

        self._root = self._merge(self._merge(ll, eq), right)

    def max_count_within_sum(self, cap: int) -> int:
        """
        Return maximum number of smallest elements with total sum <= cap.
        Greedy over increasing keys is optimal since all values are positive.
        """
        kept = 0
        cur = self._root
        remaining = cap

        while cur is not None:
            left_sum = cur.left.sum if cur.left else 0
            left_size = cur.left.size if cur.left else 0

            if left_sum > remaining:
                cur = cur.left
                continue

            # Take entire left subtree
            remaining -= left_sum
            kept += left_size

            # Take from current node duplicates
            if cur.key <= 0:
                # Not expected given constraints (prices positive)
                break

            can_take = remaining // cur.key
            if can_take <= 0:
                break

            take = min(can_take, cur.cnt)
            kept += take
            remaining -= take * cur.key

            if take < cur.cnt:
                break

            cur = cur.right

        return kept

    def _split_le(self, node: Optional[_Node], key: int):
        """Split by key: returns (<= key, > key)."""
        if node is None:
            return None, None

        if node.key <= key:
            a, b = self._split_le(node.right, key)
            node.right = a
            node.recalc()
            return node, b
        else:
            a, b = self._split_le(node.left, key)
            node.left = b
            node.recalc()
            return a, node

    def _merge(self, a: Optional[_Node], b: Optional[_Node]) -> Optional[_Node]:
        if a is None:
            return b
        if b is None:
            return a

        if a.prio > b.prio:
            a.right = self._merge(a.right, b)
            a.recalc()
            return a
        else:
            b.left = self._merge(a, b.left)
            b.recalc()
            return b
