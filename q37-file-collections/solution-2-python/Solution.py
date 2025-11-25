from dataclasses import dataclass
from typing import Dict, Set, List, Optional


@dataclass
class FileRecord:
    """Per-file metadata."""
    size: int                 # in KB
    collections: Set[str]     # deduped collection names


@dataclass
class CollectionStats:
    """Per-collection aggregates."""
    total_size: int = 0       # sum of sizes of member files
    file_count: int = 0       # number of member files


class FileCollectionsTracker:
    """
    In-memory tracker for files and their collection memberships.

    Supports:
      - addFile(fileName, fileSize, collections)
      - getTotalFileSize()
      - getTopCollections(strategy)
    """

    def __init__(self) -> None:
        # fileName -> record
        self._files: Dict[str, FileRecord] = {}
        # collectionName -> stats
        self._collection_stats: Dict[str, CollectionStats] = {}
        # global total size of all files
        self._total_file_size: int = 0

    def addFile(
        self,
        fileName: str,
        fileSize: int,
        collections: Optional[List[str]]
    ) -> None:
        """
        Add or update a file and its collections.

        - fileName: absolute Unix path, non-empty.
        - fileSize: positive int (KB).
        - collections: list of collection names (may be empty or None).
        """
        # as per constraints, should not happen; ignore defensively
        if not fileName:
            return
        # constraints say positive; ignore defensively
        if fileSize <= 0:
            return

        # Normalize collections to a deduped set (treat None as empty)
        new_collections: Set[str] = set()
        if collections is not None:
            for c in collections:
                if c:  # ignore null/empty names
                    new_collections.add(c)

        old_record = self._files.get(fileName)

        if old_record is not None:
            # Updating an existing file
            old_size = old_record.size
            old_collections = old_record.collections

            # Update global total
            self._total_file_size += fileSize - old_size

            # Remove old contributions
            for col in old_collections:
                stats = self._collection_stats.get(col)
                if stats is not None:
                    stats.total_size -= old_size
                    stats.file_count -= 1
                    if stats.file_count == 0:
                        # Drop empty collections
                        self._collection_stats.pop(col, None)

            # Add new contributions
            for col in new_collections:
                stats = self._collection_stats.get(col)
                if stats is None:
                    stats = CollectionStats()
                    self._collection_stats[col] = stats
                stats.total_size += fileSize
                stats.file_count += 1

            # Replace old record
            old_record.size = fileSize
            old_record.collections = new_collections

        else:
            # New file
            self._files[fileName] = FileRecord(size=fileSize, collections=new_collections)
            self._total_file_size += fileSize

            for col in new_collections:
                stats = self._collection_stats.get(col)
                if stats is None:
                    stats = CollectionStats()
                    self._collection_stats[col] = stats
                stats.total_size += fileSize
                stats.file_count += 1

    def getTotalFileSize(self) -> int:
        """
        Return the total size (in KB) of all files currently in the system.

        If no files exist, returns 0.
        Clamps to 32-bit int max if it overflows that range (to mirror Java int).
        """
        if self._total_file_size <= 0:
            return 0
        # Simulate Java int overflow protection for safety in tests
        INT_MAX = 2_147_483_647
        if self._total_file_size > INT_MAX:
            return INT_MAX
        return self._total_file_size

    def getTopCollections(self, strategy: int) -> List[str]:
        """
        Return top 10 collections depending on strategy:

        - strategy == 0: sort by total size (desc), then name (lex asc).
        - strategy == 1: sort by file count (desc), then name (lex asc).

        If fewer than 10 collections exist, return all in sorted order.
        """
        names = list(self._collection_stats.keys())

        if strategy == 0:
            # sort by total size desc, then name asc
            def key_func(name: str):
                stats = self._collection_stats.get(name)
                total_size = stats.total_size if stats is not None else 0
                return (-total_size, name)
        else:
            # strategy == 1: sort by file count desc, then name asc
            def key_func(name: str):
                stats = self._collection_stats.get(name)
                file_count = stats.file_count if stats is not None else 0
                return (-file_count, name)

        names.sort(key=key_func)
        return names[:10]
