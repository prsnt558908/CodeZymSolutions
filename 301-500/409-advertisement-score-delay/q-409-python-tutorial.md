# Advertisement Score Delay in Python

## Problem Statement

[https://codezym.com/question/409-advertisement-score-delay](https://codezym.com/question/409-advertisement-score-delay)

Keep eligible advertisements in one priority queue and delayed advertisements in another. The first queue finds the best score, with content breaking ties. The second queue tells us when an advertisement can return. Both insertion methods use this same logic, with a delay of `1` for `insertAd`.

## 1. Start Simple: Scan All Advertisements

Store every advertisement in a list. Before each selection, increase a counter that counts only `getAd` calls. Scan the list and choose the best advertisement whose `nextEligibleCall` has arrived.

This snippet assumes the counter has already been increased.

```python
best = None
for ad in advertisements:
    if ad.nextEligibleCall > getAdCalls:
        continue

    if (best is None
            or ad.score > best.score
            or (ad.score == best.score and ad.content < best.content)):
        best = ad
```

After choosing an advertisement, reduce its score and update its next eligible call. This works, but scanning `n` advertisements for each request takes `O(n)` time. With up to `100,000` method calls, repeated scans can become expensive.

## 2. Improve It: Use Two Priority Queues

A priority queue keeps its highest-priority item at the front without sorting the entire collection after every change. Python's `heapq` provides a min-heap, where the smallest item comes first.

**`ready` stores eligible advertisements.** Store tuples of `(-score, content, ad)`. A larger score produces a smaller negative score, so the highest score comes first. Equal scores are resolved by the content string.

**`waiting` stores delayed advertisements.** Store tuples of `(nextEligibleCall, content, ad)`. The earliest eligible call comes first. At the start of a request, move every advertisement whose delay has ended into `ready`.

A normal FIFO queue cannot handle different delays. An advertisement chosen later can become eligible sooner than one chosen earlier.

The `Advertisement` dataclass keeps each advertisement's content, score, delay, and next eligible call together. Each advertisement belongs to exactly one heap, so we do not need a separate dictionary.

Content is unique, so tuple comparisons never need to compare the `Advertisement` objects. For the visible ASCII input in this problem, Python string ordering matches the required case-sensitive ordering.

## 3. Get the Delay Boundary Right

Number `getAd` calls starting from `1`. If an advertisement is returned on call `c` with delay `d`, it must skip calls `c + 1` through `c + d`.

Its next eligible call is:

```text
nextEligibleCall = c + d + 1
```

For example, returning an advertisement on call `1` with delay `2` blocks calls `2` and `3`. It becomes eligible on call `4`.

Every `getAd` call advances the counter, including calls returning `""`. Insertions do not advance it, and newly inserted advertisements are immediately eligible.

## 4. Process Each Request

1. Increase the `getAd` counter.
2. Move all advertisements with `nextEligibleCall <= getAdCalls` from `waiting` to `ready`.
3. If `ready` is empty, return `""`.
4. Otherwise, remove its best advertisement, decrease its score, calculate its next eligible call, and put it in `waiting`.

A negative score does not make an advertisement ineligible. The heap can still select it.

After changing an advertisement's score, create a fresh tuple when adding it back to `ready`. The old tuple's numeric priority would not update automatically.

## 5. Walk Through an Example

```python
server = AdvertisementServer()
server.insertAdWithDelay("alpha", 5, 2)
server.insertAd("beta", 5)

server.getAd()  # "alpha", score becomes 4, next eligible call is 4.
server.getAd()  # "beta", score becomes 4, next eligible call is 4.
server.getAd()  # "", both advertisements are delayed.
server.getAd()  # "alpha", both are eligible and alpha wins the score tie.
```

Both advertisements become eligible together on call `4`. Moving all expired delays before choosing an advertisement ensures that both take part in the comparison.

## 6. Why This Works

Before each selection, `ready` contains every eligible advertisement and `waiting` contains the advertisements still delayed. This holds initially and for every insertion.

Moving all expired entries restores this rule for the current call. The ordering of `ready` then selects the required score and content. Moving the chosen advertisement to `waiting` with `c + d + 1` blocks exactly the required future calls.

If `ready` is empty after restoring expired entries, no advertisement is eligible, so returning `""` is correct.

## 7. Complexity

Let `n` be the number of stored advertisements.

Insertion takes `O(log n)` time. A `getAd` call that releases `k` advertisements takes `O((k + 1) log n)` time. A single request can release many advertisements at once.

Each selection adds one waiting entry, and that entry is released at most once before another selection. Across a sequence of `m` method calls, the total time is `O(m log n)`, or `O(log n)` amortized per call. Heap storage is `O(n)`.

These bounds count string comparisons as constant time. If the maximum content length is `L`, tied-score comparisons can take `O(L)`, giving `O(m L log n)` total time in the worst case. Including stored text, space is `O(n L)`.

## 8. Complete Python Code

The method names match the supplied starter code. Both insertion methods are supported on the same server.

```python
from dataclasses import dataclass
import heapq


@dataclass
class Advertisement:
    content: str
    score: int
    delay: int
    nextEligibleCall: int = 0


class AdvertisementServer:
    def __init__(self):
        self.ready = []
        self.waiting = []
        self.getAdCalls = 0

    def insertAd(self, adContent, score):
        # The basic version skips one subsequent getAd call.
        self.insertAdWithDelay(adContent, score, 1)

    def insertAdWithDelay(self, adContent, score, delay):
        # New advertisements are eligible immediately.
        ad = Advertisement(adContent, score, delay)
        heapq.heappush(self.ready, (-ad.score, ad.content, ad))

    def getAd(self):
        # Every getAd call counts, including one that returns an empty string.
        self.getAdCalls += 1

        # Restore every advertisement whose delay has ended.
        while self.waiting and self.waiting[0][0] <= self.getAdCalls:
            _, _, ad = heapq.heappop(self.waiting)
            heapq.heappush(self.ready, (-ad.score, ad.content, ad))

        if not self.ready:
            return ""

        _, _, ad = heapq.heappop(self.ready)

        # Change heap ordering fields only after removing the advertisement.
        ad.score -= 1
        ad.nextEligibleCall = self.getAdCalls + ad.delay + 1
        heapq.heappush(
            self.waiting, (ad.nextEligibleCall, ad.content, ad)
        )

        return ad.content
```
