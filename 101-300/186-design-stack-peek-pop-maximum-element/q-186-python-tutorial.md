# Design Stack With Peek And Pop Maximum Element in Python

#### Problem Statement
[https://codezym.com/question/186-design-stack-peek-pop-maximum-element](https://codezym.com/question/186-design-stack-peek-pop-maximum-element)


A normal stack already handles `push()`, `pop()` and `top()` in O(1). The hard part is the maximum.


The core idea is to keep every pushed value in **two places**: a **stack** that remembers the push order, and a **max heap** that always knows the biggest value.


When a value is removed from one of them, we don't search for it in the other one. We just **mark it as removed** and throw it away later, when it reaches the top. This simple trick is called **lazy deletion**, and it brings `peekMax()` and `popMax()` down from O(n) to O(log n).


We will first build a simple brute force solution, see where it is slow, and then fix it step by step.


## Solution 1: Brute Force With a Single List


Keep all values in one list. The end of the list is the top of the stack.


`push()`, `pop()` and `top()` only touch the end of the list, so they are O(1). For the maximum, we scan the whole list.

```python
def __init__(self):
    self.stack = []  # plain list of values, the last value is the top

# O(n): check every value
def peekMax(self) -> int:
    return max(self.stack)

# O(n): find the max closest to the top and remove it
def popMax(self) -> int:
    max_value = max(self.stack)
    # walk from the top, so the first match is the one closest to the top
    for i in range(len(self.stack) - 1, -1, -1):
        if self.stack[i] == max_value:
            self.stack.pop(i)
            break
    return max_value
```


### Problems With This Approach


- `peekMax()` and `popMax()` look at every value, so each call is O(n).
- `popMax()` also removes from the middle of the list, which shifts all values after it.


You may know the **Min Stack** trick of keeping a second stack of running maximums. It makes `peekMax()` O(1). But `popMax()` still has to dig the max out from the middle of the stack, so it stays O(n).


## Solution 2: Stack + Max Heap With Lazy Deletion


### Step 1: A Max Heap for the Maximum


A max heap (priority queue) always keeps the biggest value on top. Reading it is O(1) and removing it is O(log n).


We still need a stack for `push()`, `pop()` and `top()`. So every pushed value goes into **both** the stack and the heap.


### Step 2: The Removal Problem


Now each value lives in two places.

- `pop()` removes a value from the stack, but the same value is still somewhere inside the heap.
- `popMax()` removes a value from the heap, but the same value is still somewhere inside the stack.


Finding it in the middle of the other structure is O(n), which is exactly what we wanted to avoid.


### Step 3: Lazy Deletion


Notice that the stack only ever looks at its **top** item. The heap also only looks at its **top** item.


So we don't need to delete the other copy right away. We just **mark it as removed**.


Later, whenever a removed item shows up on top of the stack or the heap, we throw it away and look at the next one. Each item is thrown away at most once from each structure, so this cleanup stays cheap.


### Step 4: Equal Maximum Values


If the max appears many times, `popMax()` must remove the one closest to the top.


So every pushed item gets an `id`: 0, 1, 2 and so on. An item pushed later has a bigger `id`, which means it is closer to the top.


The heap orders items like this:

1. Bigger value comes out first.
2. For equal values, bigger `id` comes out first.


### Why We Need the `StackItem` Class


`StackItem` holds three things:

- `value`: the pushed number.
- `id`: the push order, used to break ties in the heap.
- `removed`: becomes `True` when the item is removed by `pop()` or `popMax()`.


The **same object** is added to both the stack and the heap. So when `pop()` sets `removed = True`, the heap sees it too, and the other way round. We don't need any extra set or map to remember removed items.


Python's `heapq` always takes out the **smallest** item first. To make it work like a max heap, `StackItem` defines `__lt__` (the "less than" check).


In `__lt__`, an item counts as "smaller" if it should come out first: a bigger value, or the same value with a bigger `id`.


### Dry Run (Example 1)


`4#2` means value `4` with id `2`. A ~~crossed~~ item is marked removed but not thrown away yet.

| Operation | Stack (bottom → top) | Heap (first out → last out) | Output |
|---|---|---|---|
| `push(4)` | 4#0 | 4#0 | |
| `push(2)` | 4#0, 2#1 | 4#0, 2#1 | |
| `push(4)` | 4#0, 2#1, 4#2 | 4#2, 4#0, 2#1 | |
| `top()` | 4#0, 2#1, 4#2 | 4#2, 4#0, 2#1 | 4 |
| `popMax()` | 4#0, 2#1, ~~4#2~~ | 4#0, 2#1 | 4 |
| `top()` | 4#0, 2#1 | 4#0, 2#1 | 2 |
| `peekMax()` | 4#0, 2#1 | 4#0, 2#1 | 4 |
| `pop()` | 4#0 | 4#0, ~~2#1~~ | 2 |
| `top()` | 4#0 | 4#0, ~~2#1~~ | 4 |


`popMax()` picks `4#2` over `4#0` because it was pushed later. The crossed `4#2` is thrown away from the stack only when the next `top()` needs the real top.


### How Each Operation Works


- `push(x)`: create a `StackItem` with the next id. Add it to the stack and the heap.
- `pop()`: clean the stack top, pop the item and mark it removed.
- `top()`: clean the stack top and return its value.
- `peekMax()`: clean the heap top and return its value.
- `popMax()`: clean the heap top, pop the item from the heap and mark it removed.


"Clean the top" means: while the top item is marked removed, throw it away.


### Code

```python
import heapq


class StackItem:
    """
    One pushed value.
    The same object is stored in both the stack and the heap,
    so marking it removed in one place is seen by the other.
    """

    def __init__(self, value: int, item_id: int):
        self.value = value
        self.id = item_id     # push order, a bigger id means closer to the top
        self.removed = False  # True once removed by pop() or popMax()

    def __lt__(self, other: "StackItem") -> bool:
        # heapq always pops the "smallest" item first.
        # So here "smaller" means "should come out of the heap first".
        if self.value != other.value:
            return self.value > other.value  # bigger value comes out first
        # same value: the item pushed later is closer to the top, so it comes out first
        return self.id > other.id


class MaxStack:

    def __init__(self):
        self.stack = []     # items in push order, the last item is the top
        self.max_heap = []  # the same items, the max value is always at index 0
        self.next_id = 0    # id for the next pushed item

    def push(self, x: int) -> None:
        item = StackItem(x, self.next_id)
        self.next_id += 1
        self.stack.append(item)
        heapq.heappush(self.max_heap, item)

    def pop(self) -> int:
        self.cleanStackTop()
        item = self.stack.pop()
        item.removed = True  # the heap will throw it away later
        return item.value

    def top(self) -> int:
        self.cleanStackTop()
        return self.stack[-1].value

    def peekMax(self) -> int:
        self.cleanHeapTop()
        return self.max_heap[0].value

    def popMax(self) -> int:
        self.cleanHeapTop()
        item = heapq.heappop(self.max_heap)
        item.removed = True  # the stack will throw it away later
        return item.value

    def cleanStackTop(self) -> None:
        """Throws away items on top of the stack that were already removed by popMax()."""
        while self.stack and self.stack[-1].removed:
            self.stack.pop()

    def cleanHeapTop(self) -> None:
        """Throws away items on top of the heap that were already removed by pop()."""
        while self.max_heap and self.max_heap[0].removed:
            heapq.heappop(self.max_heap)
```


## Complexity


Let n be the number of pushed values.

| Operation | Time |
|---|---|
| `push()` | O(log n) |
| `pop()` | O(1) amortized |
| `top()` | O(1) amortized |
| `peekMax()` | O(log n) amortized |
| `popMax()` | O(log n) amortized |


**Amortized** means a single call can sometimes do extra cleanup. But each item is thrown away at most once from each structure, so the total cleanup work stays small when spread over all calls.


**Space:** O(n). A removed item may stay inside the other structure for a while, but each item is stored at most once in each.