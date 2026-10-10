# Minimum and Maximum Count Data Structure in Python


#### Problem Statement
[https://codezym.com/question/467-min-max-count-structure](https://codezym.com/question/467-min-max-count-structure)


Group keys that have the same count. Keep these groups in a doubly linked list, ordered by count. Inside each group, keep keys in arrival order to resolve ties. A dictionary finds each key's group directly. Since every update changes a count by just one, we only need to check a neighboring group. This gives O(1) average time per method.


## Start with a dictionary


A simple solution stores each key's count in a dictionary. It also records when the key last entered that count. Every update gets a new timestamp.


Updates take O(1) average time. But finding the smallest or largest count requires scanning all K stored keys. Ties use the earliest timestamp. Each getter takes O(K), which does not meet the requirement.


We need to keep the smallest and largest count groups directly accessible.


## Keep ordered count groups


Use three pieces:


- **A dictionary named `key_to_bucket`.** It finds the group containing a key. The group's count is also the key's count.
- **A doubly linked list of `Bucket` objects.** Real groups have increasing counts. The first group holds the minimum. The last holds the maximum. Each group has links to the group before and after it.
- **An `OrderedDict` inside each group.** It supports average O(1) insertion and removal. Its linked order also gives O(1) access to the first key through an iterator. That key wins a tie. Values are unused, so we store `None`.


For example, groups with counts 1 and 4 can be adjacent. We do not keep empty groups for counts 2 and 3.


A set would not preserve the required tie order. A list preserves order, but removing a particular key can require a scan. `OrderedDict` gives us both properties we need.


Two empty endpoint objects, `head` and `tail`, simplify the links. They never hold real keys.


## Why one neighbor is enough


Suppose a key has count c. An increment moves it to c + 1. If that group exists, it must be the next group. There is no integer count between c and c + 1.


If the next group has a larger count, or is `tail`, create the missing group immediately after the current one.


A decrement works the same way with the previous group and count c - 1. We never search through the groups or sort them.


## Update and read rules


- **New key.** Add it to the end of the count-1 group. Create that group after `head` if needed.
- **Increment an existing key.** Find or create the next count group. Move the key to its end and update the dictionary.
- **Decrement a key.** Move it to the end of the previous count group. If its count becomes zero, remove it instead. The input guarantees that this key exists.
- **After a removal or move.** Unlink the old group if it becomes empty.
- **Get the minimum or maximum.** Read the first key in the first or last real group. Return `""` when there are no keys. Reading never changes the order.


Appending on every arrival also handles a key that leaves a count and later returns. Its old position is gone.


## Walk through a tie


1. `inc("plum")`, then `inc("kiwi")`. Count 1 contains `[plum, kiwi]`. Both getters return `"plum"`.
2. `inc("kiwi")`. Count 1 contains `[plum]`. Count 2 contains `[kiwi]`.
3. `inc("plum")`. Count 2 contains `[kiwi, plum]`. Both getters return `"kiwi"`.
4. `dec("plum")`, then `dec("kiwi")`. Count 1 contains `[plum, kiwi]`. Both getters return `"plum"`.


The winner depends on arrival at the current count. It does not depend on alphabetical order or the key's first insertion into the structure.


## Why it works and what it costs


Every key belongs to exactly one group, and the dictionary points to that group. Moving only to a neighboring count keeps groups sorted. Removing empty groups keeps the endpoints correct. Appending keys on arrival keeps the tie order correct.


Each method touches only a fixed number of links and hash entries. Updates take O(1) average time. Both getters and the constructor take O(1) time.


For K active keys, the structure stores O(K) key entries and group objects. Hash containers may retain allocated capacity after removals.


## Python code


`Bucket` is a dataclass for one count group. `field(default_factory=OrderedDict)` gives every group its own ordered dictionary. `eq=False` keeps node comparisons based on identity. `AllOne` manages the dictionary, group links, and public operations.


```python
from collections import OrderedDict
from dataclasses import dataclass, field
from typing import Dict, Optional


@dataclass(eq=False)
class Bucket:
    """Holds keys with the same count, in their arrival order."""

    count: int
    keys: OrderedDict = field(default_factory=OrderedDict)
    prev: Optional["Bucket"] = None
    next: Optional["Bucket"] = None


class AllOne:
    def __init__(self):
        self.key_to_bucket: Dict[str, Bucket] = {}

        # Dummy endpoints simplify insertion and removal.
        self.head = Bucket(0)
        self.tail = Bucket(0)
        self.head.next = self.tail
        self.tail.prev = self.head

    def inc(self, key: str) -> None:
        current = self.key_to_bucket.get(key)

        if current is None:
            if self.head.next is self.tail or self.head.next.count != 1:
                self.insert_after(self.head, 1)

            target = self.head.next
            target.keys[key] = None
            self.key_to_bucket[key] = target
            return

        target = current.next
        if target is self.tail or target.count != current.count + 1:
            target = self.insert_after(current, current.count + 1)

        self.move_key(key, current, target)

    def dec(self, key: str) -> None:
        # The problem guarantees that this key exists.
        current = self.key_to_bucket[key]

        if current.count == 1:
            del current.keys[key]
            del self.key_to_bucket[key]
            self.remove_if_empty(current)
            return

        target = current.prev
        if target is self.head or target.count != current.count - 1:
            target = self.insert_after(current.prev, current.count - 1)

        self.move_key(key, current, target)

    def getMaxKey(self) -> str:
        if self.tail.prev is self.head:
            return ""
        return next(iter(self.tail.prev.keys))

    def getMinKey(self) -> str:
        if self.head.next is self.tail:
            return ""
        return next(iter(self.head.next.keys))

    def insert_after(self, left: Bucket, count: int) -> Bucket:
        bucket = Bucket(count)
        right = left.next

        bucket.prev = left
        bucket.next = right
        left.next = bucket
        right.prev = bucket
        return bucket

    def move_key(self, key: str, current: Bucket, target: Bucket) -> None:
        del current.keys[key]

        # Entering a count puts the key last in that count's order.
        target.keys[key] = None
        self.key_to_bucket[key] = target
        self.remove_if_empty(current)

    def remove_if_empty(self, bucket: Bucket) -> None:
        if not bucket.keys:
            bucket.prev.next = bucket.next
            bucket.next.prev = bucket.prev
```
