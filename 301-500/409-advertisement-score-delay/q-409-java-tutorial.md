# Advertisement Score Delay in Java

## Problem Statement

[https://codezym.com/question/409-advertisement-score-delay](https://codezym.com/question/409-advertisement-score-delay)

Keep eligible advertisements in one priority queue and delayed advertisements in another. The first queue finds the best score, with content breaking ties. The second queue tells us when an advertisement can return. Both insertion methods use this same logic, with a delay of `1` for `insertAd`.

## 1. Start Simple: Scan All Advertisements

Store every advertisement in a list. Before each selection, increase a counter that counts only `getAd` calls. Scan the list and choose the best advertisement whose `nextEligibleCall` has arrived.

This snippet assumes the counter has already been increased.

```java
Advertisement best = null;
for (Advertisement ad : advertisements) {
    if (ad.nextEligibleCall > getAdCalls) {
        continue;
    }

    if (best == null
            || ad.score > best.score
            || (ad.score == best.score
                && ad.content.compareTo(best.content) < 0)) {
        best = ad;
    }
}
```

After choosing an advertisement, reduce its score and update its next eligible call. This works, but scanning `n` advertisements for each request takes `O(n)` time. With up to `100,000` method calls, repeated scans can become expensive.

## 2. Improve It: Use Two Priority Queues

A priority queue keeps its highest-priority item at the front without sorting the entire collection after every change. Java provides it as `PriorityQueue`.

**`ready` stores eligible advertisements.** Order it by higher score first, then lexicographically smaller content. Its front is exactly the advertisement we should return.

**`waiting` stores delayed advertisements.** Order it by `nextEligibleCall`, smallest first. At the start of a request, move every advertisement whose delay has ended into `ready`.

A normal FIFO queue cannot handle different delays. An advertisement chosen later can become eligible sooner than one chosen earlier.

The `Advertisement` class keeps each advertisement's content, score, delay, and next eligible call together. Each advertisement belongs to exactly one queue, so we do not need a separate map.

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

A negative score does not make an advertisement ineligible. The queue can still select it.

Remove an advertisement before changing fields used by a queue's ordering. Changing those fields while the advertisement is still inside a priority queue can leave its order incorrect.

## 5. Walk Through an Example

```java
AdvertisementServer server = new AdvertisementServer();
server.insertAdWithDelay("alpha", 5, 2);
server.insertAd("beta", 5);

server.getAd(); // "alpha", score becomes 4, next eligible call is 4.
server.getAd(); // "beta", score becomes 4, next eligible call is 4.
server.getAd(); // "", both advertisements are delayed.
server.getAd(); // "alpha", both are eligible and alpha wins the score tie.
```

Both advertisements become eligible together on call `4`. Moving all expired delays before choosing an advertisement ensures that both take part in the comparison.

## 6. Why This Works

Before each selection, `ready` contains every eligible advertisement and `waiting` contains the advertisements still delayed. This holds initially and for every insertion.

Moving all expired entries restores this rule for the current call. The ordering of `ready` then selects the required score and content. Moving the chosen advertisement to `waiting` with `c + d + 1` blocks exactly the required future calls.

If `ready` is empty after restoring expired entries, no advertisement is eligible, so returning `""` is correct.

## 7. Complexity

Let `n` be the number of stored advertisements.

Insertion takes `O(log n)` time. A `getAd` call that releases `k` advertisements takes `O((k + 1) log n)` time. A single request can release many advertisements at once.

Each selection adds one waiting entry, and that entry is released at most once before another selection. Across a sequence of `m` method calls, the total time is `O(m log n)`, or `O(log n)` amortized per call. Queue storage is `O(n)`.

These bounds count string comparisons as constant time. If the maximum content length is `L`, tied-score comparisons can take `O(L)`, giving `O(m L log n)` total time in the worst case. Including stored text, space is `O(n L)`.

## 8. Complete Java Code

The method names match the supplied starter code. Both insertion methods are supported on the same server.

```java
import java.util.PriorityQueue;

class Advertisement {
    String content;
    int score;
    int delay;
    int nextEligibleCall;

    Advertisement(String content, int score, int delay) {
        this.content = content;
        this.score = score;
        this.delay = delay;
        nextEligibleCall = 0;
    }
}

public class AdvertisementServer {
    PriorityQueue<Advertisement> ready;
    PriorityQueue<Advertisement> waiting;
    int getAdCalls;

    public AdvertisementServer() {
        ready = new PriorityQueue<>((a, b) -> {
            if (a.score != b.score) {
                return Integer.compare(b.score, a.score);
            }
            return a.content.compareTo(b.content);
        });

        waiting = new PriorityQueue<>((a, b) ->
            Integer.compare(a.nextEligibleCall, b.nextEligibleCall)
        );
        getAdCalls = 0;
    }

    public void insertAd(String adContent, int score) {
        // The basic version skips one subsequent getAd call.
        insertAdWithDelay(adContent, score, 1);
    }

    public void insertAdWithDelay(String adContent, int score, int delay) {
        // New advertisements are eligible immediately.
        ready.offer(new Advertisement(adContent, score, delay));
    }

    public String getAd() {
        // Every getAd call counts, including one that returns an empty string.
        getAdCalls++;

        // Restore every advertisement whose delay has ended.
        while (!waiting.isEmpty()
                && waiting.peek().nextEligibleCall <= getAdCalls) {
            ready.offer(waiting.poll());
        }

        if (ready.isEmpty()) {
            return "";
        }

        Advertisement ad = ready.poll();

        // Change heap ordering fields only after removing the advertisement.
        ad.score--;
        ad.nextEligibleCall = getAdCalls + ad.delay + 1;
        waiting.offer(ad);

        return ad.content;
    }
}
```
