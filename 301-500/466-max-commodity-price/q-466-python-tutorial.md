# Max Commodity Price in Python

#### Problem Statement
[https://codezym.com/question/466-max-commodity-price](https://codezym.com/question/466-max-commodity-price)


We get a stream of price updates. Each update says: at this timestamp, the price is now X. At any moment, we must return the highest price among the current records.

The core idea is to use two simple data structures together. A dictionary remembers the current price of every timestamp. A max heap holds the prices and always keeps the biggest one on top.

The only tricky part is an update. When a price changes, its old value is still inside the heap. Instead of searching for it, we leave it there. We throw it away later, only when it reaches the top. This trick is called **lazy removal**.

We will start with a simple brute force solution, see why it is slow, and then improve it.


## Understanding the Problem

Each timestamp holds exactly one price: the latest one we received for it.

So `upsertCommodityPrice()` either adds a new timestamp or replaces the old price of an existing one.

`getMaxCommodityPrice()` returns the biggest price among these current records.

A few things to notice:

1. **Out of order events do not matter.** We never compare timestamps with each other. We only care about the latest price of each timestamp.

2. **The maximum can go down.** In Example 1, the price at timestamp `14` drops from `70` to `40`. So the max falls to `62`.

3. **Two timestamps can have the same price.** In Example 2, timestamps `8` and `2` both have `36`. Lowering the price at timestamp `8` must not affect timestamp `2`.

Point 2 is the hardest part of this problem.


## Solution 1: Brute Force

### Idea

Store the price of every timestamp in a dictionary.

To find the max, look at every stored price and pick the biggest one.

### Why a dictionary?

A dictionary keeps one entry per timestamp.

Assigning `price_at_timestamp[timestamp] = commodityPrice` adds a new timestamp, or replaces the old price if the timestamp already exists. That is exactly what "upsert" means. So the replace rule comes for free.

### Code

```python
class RunningCommodityPrice:
    def __init__(self):
        # timestamp -> current price (the latest one received for that timestamp)
        self.price_at_timestamp = {}

    def upsertCommodityPrice(self, timestamp, commodityPrice):
        # adds a new timestamp or replaces its old price
        self.price_at_timestamp[timestamp] = commodityPrice

    def getMaxCommodityPrice(self):
        # check every stored price and return the largest one
        return max(self.price_at_timestamp.values())
```

### Complexity

- `upsertCommodityPrice()`: O(1)
- `getMaxCommodityPrice()`: O(n), where n is the number of stored timestamps
- Space: O(n)

### Problem with this approach

Every max query scans all the prices.

There can be up to 100,000 calls. Say the first 50,000 calls add new timestamps and the next 50,000 ask for the max. That is 50,000 x 50,000 = 2.5 billion steps. Far too slow.


## Can One Max Variable Fix It?

Let's try keeping a single `max_price` variable.

When a new price is bigger, we just update `max_price`. That part is easy.

But what if the max goes down? In Example 1, the price at timestamp `14` drops from `70` to `40`. Our variable still says `70`, but the real max is now `62`.

Where do we get `62` from? We would need to scan every price again.

So one variable is not enough. We need a data structure that can always give us the biggest price quickly, even right after the old max changes. A **max heap** does exactly this.


## Solution 2: Dictionary + Max Heap with Lazy Removal

### Idea

We use two data structures together:

- `price_at_timestamp` (dictionary): the current price of each timestamp. It is always correct.
- `max_heap` (a list used as a heap): `(price, timestamp)` pairs, with the biggest price on top. It may also hold old, outdated pairs.

**Upsert:** save the price in the dictionary and push a new pair into the heap. Do not touch the old pair.

**Get max:** look at the top pair. If the dictionary still has this price for this timestamp, it is the answer. If not, the pair is outdated. Pop it and check the new top.

We never run out of pairs while doing this. The newest pair of every timestamp always matches the dictionary, so it is never popped. And the problem promises at least one upsert before the first max query.

### Why a max heap?

A max heap always keeps its biggest item on top. Adding an item or removing the top takes only O(log m) time, where m is the heap size.

Python's `heapq` module turns a plain list into a heap. But it keeps the smallest item on top. So we store each price as a negative number. The biggest price becomes the smallest number and lands on top. For example, `-70` is smaller than `-62`, so price `70` comes before price `62`.

Python compares tuples item by item, starting with the first one. So the heap orders the pairs by price.

### Why not remove the old pair right away?

A heap can remove its top quickly. But it cannot quickly remove an item from the middle. `heapq` has no function for that. We would have to search the whole list to find the pair and then fix the heap. That is O(m) time.

Luckily, an outdated pair below the top does no harm, because we only ever return the top. So we remove an outdated pair only when it reaches the top. That is the lazy removal idea.

### Why store the timestamp in the heap?

The timestamp lets us check if a pair is still current.

Look at Example 2. The heap holds `(36, 8)` and `(36, 2)`. Then the price at timestamp `8` changes to `21`.

Now `(36, 8)` is outdated, but `(36, 2)` is still valid. With prices alone, we could not tell these two apart.

### Walkthrough of Example 1

Here `upsert` and `getMax` are short for the two methods. Pairs are written as `(price, timestamp)`. The code stores each price as a negative number, but here we show the real price to keep it easy to read.

| Call | Heap after the call, biggest first | Returns |
|---|---|---|
| `upsert(14, 70)` | `(70, 14)` | |
| `upsert(3, 55)` | `(70, 14)`, `(55, 3)` | |
| `upsert(9, 62)` | `(70, 14)`, `(62, 9)`, `(55, 3)` | |
| `getMax()` | `(70, 14)`, `(62, 9)`, `(55, 3)` | `70` |
| `upsert(14, 40)` | `(70, 14)`, `(62, 9)`, `(55, 3)`, `(40, 14)` | |
| `getMax()` | `(62, 9)`, `(55, 3)`, `(40, 14)` | `62` |

In the first `getMax()`, the top pair `(70, 14)` matches the dictionary. So we return `70`.

In the last `getMax()`, the top pair is still `(70, 14)`. But the dictionary says timestamp `14` now has `40`. So this pair is outdated, and we pop it. The new top `(62, 9)` matches the dictionary. So we return `62`.

### Code

```python
import heapq


class RunningCommodityPrice:
    def __init__(self):
        # timestamp -> current price (the latest one received for that timestamp)
        self.price_at_timestamp = {}

        # Max heap of (-price, timestamp) pairs.
        # heapq is a min heap, so we store -price to keep the biggest price on top.
        # It can also hold outdated pairs. We skip them in getMaxCommodityPrice().
        self.max_heap = []

    def upsertCommodityPrice(self, timestamp, commodityPrice):
        self.price_at_timestamp[timestamp] = commodityPrice

        # The old pair of this timestamp (if any) stays in the heap.
        # It gets removed later, only when it reaches the top.
        heapq.heappush(self.max_heap, (-commodityPrice, timestamp))

    def getMaxCommodityPrice(self):
        # throw away outdated pairs sitting on top of the heap
        while self.is_outdated(self.max_heap[0]):
            heapq.heappop(self.max_heap)
        return -self.max_heap[0][0]

    def is_outdated(self, pair):
        # a pair is outdated if its timestamp now stores a different price
        negative_price, timestamp = pair
        return self.price_at_timestamp[timestamp] != -negative_price
```

### Complexity

Let m be the number of upsert calls. The heap never holds more than m pairs.

- `upsertCommodityPrice()`: O(log m), for one push into the heap.
- `getMaxCommodityPrice()`: O(log m) on average across all calls.
- Space: O(m), since outdated pairs stay in the heap until they are popped.

Why "on average"? One call may pop many outdated pairs. But every pair is pushed once and popped at most once. So all the popping together never costs more than all the pushing.

For 100,000 calls, this is only a few million steps.


## Complexity Summary

| Solution | upsertCommodityPrice | getMaxCommodityPrice | Space |
|---|---|---|---|
| Brute Force | O(1) | O(n) | O(n) |
| Dictionary + Max Heap | O(log m) | O(log m) on average | O(m) |

n is the number of stored timestamps. m is the number of upsert calls.