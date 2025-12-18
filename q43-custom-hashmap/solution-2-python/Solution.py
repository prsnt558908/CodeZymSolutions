class CustomHashMap:
    class _Entry:
        __slots__ = ("key", "value", "next")

        def __init__(self, key: str, value: str, nxt: "CustomHashMap._Entry" = None):
            self.key = key
            self.value = value
            self.next = nxt

    def __init__(self, minLoadFactor: float, maxLoadFactor: float):
        # store after rounding to 2 decimals, as "hundredths" ints to avoid float issues
        self._min_lf100 = self._round2_to_int(minLoadFactor)
        self._max_lf100 = self._round2_to_int(maxLoadFactor)

        # initial bucketsCount = 2
        self._buckets = [None, None]  # type: list[CustomHashMap._Entry | None]
        self._size = 0

    def put(self, key: str, value: str):
        idx = self._bucket_index(key, len(self._buckets))

        # update existing key
        cur = self._buckets[idx]
        while cur is not None:
            if cur.key == key:
                cur.value = value
                self._adjust_buckets_if_needed()  # check after each put
                return
            cur = cur.next

        # insert new at head
        self._buckets[idx] = self._Entry(key, value, self._buckets[idx])
        self._size += 1
        self._adjust_buckets_if_needed()

    def get(self, key: str) -> str:
        idx = self._bucket_index(key, len(self._buckets))
        cur = self._buckets[idx]
        while cur is not None:
            if cur.key == key:
                return cur.value
            cur = cur.next
        return ""

    def remove(self, key: str) -> str:
        idx = self._bucket_index(key, len(self._buckets))

        prev = None
        cur = self._buckets[idx]

        while cur is not None:
            if cur.key == key:
                removed_value = cur.value
                if prev is None:
                    self._buckets[idx] = cur.next
                else:
                    prev.next = cur.next

                self._size -= 1
                self._adjust_buckets_if_needed()  # check after each remove
                return removed_value

            prev = cur
            cur = cur.next

        # spec says "after each remove()", so still run the check
        self._adjust_buckets_if_needed()
        return ""

    def getBucketKeys(self, bucketIndex: int) -> list:
        if bucketIndex < 0 or bucketIndex >= len(self._buckets):
            return []

        keys = []
        cur = self._buckets[bucketIndex]
        while cur is not None:
            keys.append(cur.key)
            cur = cur.next

        keys.sort()
        return keys

    def size(self) -> int:
        return self._size

    def bucketsCount(self) -> int:
        return len(self._buckets)

    # ----------------- Internal helpers -----------------

    def _adjust_buckets_if_needed(self):
        while True:
            lf100 = self._load_factor100()

            if lf100 > self._max_lf100:
                self._rehash(len(self._buckets) * 2)
                continue

            if lf100 < self._min_lf100 and len(self._buckets) > 2:
                self._rehash(len(self._buckets) // 2)
                continue

            break

    def _rehash(self, new_buckets_count: int):
        if new_buckets_count < 2:
            new_buckets_count = 2

        old = self._buckets
        neu = [None] * new_buckets_count

        for head in old:
            cur = head
            while cur is not None:
                nxt = cur.next

                idx = self._bucket_index(cur.key, new_buckets_count)
                cur.next = neu[idx]
                neu[idx] = cur

                cur = nxt

        self._buckets = neu

    def _load_factor100(self) -> int:
        # LoadFactor = round2(size / bucketsCount) -> store as int hundredths
        return self._round2_to_int(self._size / len(self._buckets))

    @staticmethod
    def _round2_to_int(value: float) -> int:
        # Equivalent to round2(value) * 100, where round2(x)=Math.round(x*100.0)/100.0
        # Python round uses bankers rounding; we want "Math.round" behavior.
        # Math.round(x) == floor(x + 0.5) for non-negative x.
        # Here value is non-negative (size/bucketsCount, and load factors are expected positive).
        return int(value * 100.0 + 0.5)

    @staticmethod
    def _custom_hash(key: str) -> int:
        ln = len(key)
        s = 0
        for ch in key:  # key contains only a-z
            s += (ord(ch) - ord("a") + 1)
        return (ln * ln) + s

    @classmethod
    def _bucket_index(cls, key: str, buckets_count: int) -> int:
        h = cls._custom_hash(key)
        idx = h % buckets_count
        return idx
