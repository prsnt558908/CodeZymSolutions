from __future__ import annotations

from dataclasses import dataclass, field
from typing import Deque, Dict, List, Set
from collections import deque
import heapq


@dataclass
class ConnectionPool:
    """
    In-memory ConnectionPool with:
    - Fixed capacity of connections [0..capacity-1]
    - Lowest-index allocation when free connections exist
    - FIFO wait-queue when pool is full
    - expireRequest removes a queued request (no-op otherwise)
    - getRequestsWithConnection returns "requestId-connectionId" sorted lexicographically by requestId

    Assumption (per statement): acquireConnection is never called with a duplicate requestId.
    """
    capacity: int
    _free: List[int] = field(default_factory=list)          # min-heap of free connection ids
    _wait_q: Deque[str] = field(default_factory=deque)      # FIFO queue (lazy deletion)
    _waiting: Set[str] = field(default_factory=set)         # membership for queued requests
    _holding: Dict[str, int] = field(default_factory=dict)  # requestId -> connectionId

    def __post_init__(self) -> None:
        self._free = list(range(self.capacity))
        heapq.heapify(self._free)

    def acquireConnection(self, requestId: str) -> int:
        # Per statement, requestId is unique and non-empty; no duplicate calls for acquire.
        if self._free:
            conn_id = heapq.heappop(self._free)
            self._holding[requestId] = conn_id
            return conn_id

        # Pool full -> enqueue and return -1
        self._wait_q.append(requestId)
        self._waiting.add(requestId)
        return -1

    def releaseConnection(self, requestId: str) -> bool:
        if requestId not in self._holding:
            return False  # invalid or not holding

        conn_id = self._holding.pop(requestId)

        # Assign immediately to oldest non-expired queued request, if any.
        next_req = self._poll_next_valid_waiting()
        if next_req is not None:
            self._holding[next_req] = conn_id
            return True

        # Otherwise, connection becomes free again.
        heapq.heappush(self._free, conn_id)
        return True

    def expireRequest(self, requestId: str) -> None:
        # Only affects queued requests; no-op if not queued or if holding.
        if requestId in self._waiting:
            self._waiting.remove(requestId)
            # Lazy removal from _wait_q; actual skip happens in _poll_next_valid_waiting()

    def getRequestsWithConnection(self) -> List[str]:
        # Sorted lexicographically by requestId
        return [f"{req}-{self._holding[req]}" for req in sorted(self._holding.keys())]

    def _poll_next_valid_waiting(self) -> str | None:
        while self._wait_q:
            req = self._wait_q.popleft()
            if req in self._waiting:
                self._waiting.remove(req)
                return req
            # else expired already, skip
        return None
