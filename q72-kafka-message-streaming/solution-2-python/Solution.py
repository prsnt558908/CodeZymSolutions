from dataclasses import dataclass
from threading import Lock
from typing import Dict, List, Optional, Tuple


@dataclass(frozen=True)
class _CursorKey:
    topicName: str
    consumerId: str
    partitionId: int


class _Partition:
    """
    Append-only log of messages.
    Offsets are assigned in publish order (0-based, monotonic).
    """
    def __init__(self):
        self._log: List[str] = []
        self._lock = Lock()

    def append(self, message: str) -> int:
        with self._lock:
            offset = len(self._log)
            self._log.append(message)
            return offset

    def read_from(self, start_offset: int, max_messages: int) -> List[str]:
        if start_offset < 0:
            raise ValueError("start_offset must be >= 0")

        with self._lock:
            size = len(self._log)
            if start_offset >= size:
                return []
            end = min(size, start_offset + max_messages)
            # Slice preserves partition order
            return list(self._log[start_offset:end])


class _Topic:
    def __init__(self, name: str, partition_count: int):
        self.name = name
        self.partition_count = partition_count
        self.partitions: List[_Partition] = [_Partition() for _ in range(partition_count)]

    def get_partition(self, partition_id: int) -> _Partition:
        if partition_id < 0 or partition_id >= self.partition_count:
            raise ValueError(
                f"Invalid partitionId {partition_id} for topic '{self.name}' "
                f"(partitionCount={self.partition_count})"
            )
        return self.partitions[partition_id]


class MessageStreamingService:
    def __init__(self):
        self._topics: Dict[str, _Topic] = {}
        self._topics_lock = Lock()

        # Stores "next offset to read" for each (topicName, consumerId, partitionId)
        self._cursors: Dict[_CursorKey, int] = {}
        # Per-cursor locks to serialize consume() for the same (topic, consumer, partition)
        self._cursor_locks: Dict[_CursorKey, Lock] = {}
        self._cursor_meta_lock = Lock()

    def createTopic(self, topicName, partitionCount):
        if topicName is None or len(topicName) == 0:
            raise ValueError("topicName must be non-empty")
        if partitionCount < 1:
            raise ValueError("partitionCount must be >= 1")

        with self._topics_lock:
            if topicName in self._topics:
                return False
            self._topics[topicName] = _Topic(topicName, partitionCount)
            return True

    def publish(self, topicName, partitionId, message):
        if message is None or len(message) == 0:
            raise ValueError("message must be non-empty")

        with self._topics_lock:
            topic = self._topics.get(topicName)
        if topic is None:
            raise ValueError(f"Topic does not exist: {topicName}")

        partition = topic.get_partition(partitionId)
        offset = partition.append(message)
        return f"p{partitionId}:{offset}"

    def consume(self, topicName, consumerId, partitionId, maxMessages):
        if consumerId is None or len(consumerId) == 0:
            raise ValueError("consumerId must be non-empty")
        if maxMessages < 1:
            raise ValueError("maxMessages must be >= 1")

        with self._topics_lock:
            topic = self._topics.get(topicName)
        if topic is None:
            raise ValueError(f"Topic does not exist: {topicName}")

        partition = topic.get_partition(partitionId)
        key = _CursorKey(topicName=topicName, consumerId=consumerId, partitionId=partitionId)

        # Ensure per-key lock exists
        with self._cursor_meta_lock:
            lock = self._cursor_locks.get(key)
            if lock is None:
                lock = Lock()
                self._cursor_locks[key] = lock

        # Serialize consume() for the same cursor key to keep cursor advancement consistent.
        with lock:
            next_offset = self._cursors.get(key, 0)
            batch = partition.read_from(next_offset, maxMessages)
            self._cursors[key] = next_offset + len(batch)
            return batch
