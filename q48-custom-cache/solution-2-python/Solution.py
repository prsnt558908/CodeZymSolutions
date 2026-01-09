from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Iterable, Optional, Protocol


class EvictionPolicy(Protocol):
    """Strategy interface to select a key for eviction based on current keys."""

    def select_key_to_evict(self, keys: Iterable[str]) -> str:
        """Return the key to evict from the given keys. keys will be non-empty."""
        ...


@dataclass(frozen=True)
class RemoveLargestPolicy:
    """
    Evict the entry with the largest key length.
    Tie-break: lexicographically smallest key.
    """

    def select_key_to_evict(self, keys: Iterable[str]) -> str:
        # choose max by (len, then inverse lex so that lexicographically smallest wins on tie)
        best: Optional[str] = None
        best_len = -1
        for k in keys:
            lk = len(k)
            if best is None:
                best = k
                best_len = lk
                continue

            if lk > best_len:
                best = k
                best_len = lk
            elif lk == best_len and k < best:
                best = k

        # keys is non-empty, so best is never None
        return best  # type: ignore[return-value]


@dataclass(frozen=True)
class RemoveHeaviestPolicy:
    """
    Evict the entry with the maximum key weight.
    Weight: sum(a=1..z=26).
    Tie-break: lexicographically smallest key.
    """

    def select_key_to_evict(self, keys: Iterable[str]) -> str:
        best: Optional[str] = None
        best_weight = -1
        for k in keys:
            w = self._weight(k)
            if best is None:
                best = k
                best_weight = w
                continue

            if w > best_weight:
                best = k
                best_weight = w
            elif w == best_weight and k < best:
                best = k

        return best  # type: ignore[return-value]

    @staticmethod
    def _weight(key: str) -> int:
        total = 0
        for ch in key:
            # spec says a-z only, but keep it safe
            if "a" <= ch <= "z":
                total += (ord(ch) - ord("a") + 1)
        return total


class CacheBuilder:
    def __init__(self, capacity, evictionPolicy):
        self.capacity = capacity
        self.evictionPolicy = evictionPolicy
        self._cache: Dict[str, str] = {}
        self._policy: EvictionPolicy = self._create_policy(evictionPolicy)

    def _create_policy(self, evictionPolicy: str) -> EvictionPolicy:
        if evictionPolicy == "REMOVE-LARGEST-POLICY":
            return RemoveLargestPolicy()
        if evictionPolicy == "REMOVE-HEAVIEST-POLICY":
            return RemoveHeaviestPolicy()
        raise ValueError(f"Unknown evictionPolicy: {evictionPolicy}")

    def get(self, key):
        return self._cache.get(key, "")

    def put(self, key, value):
        existed = key in self._cache

        # Insert/update first (always)
        self._cache[key] = value

        # Eviction only for NEW keys, and only if size > capacity after insertion
        if (not existed) and (len(self._cache) > self.capacity):
            to_evict = self._policy.select_key_to_evict(self._cache.keys())
            # Guaranteed non-empty keys here
            if to_evict in self._cache:
                del self._cache[to_evict]

    def nextEvictionKey(self):
        if not self._cache:
            return ""
        return self._policy.select_key_to_evict(self._cache.keys())

    def remove(self, key):
        if key in self._cache:
            del self._cache[key]
            return True
        return False
