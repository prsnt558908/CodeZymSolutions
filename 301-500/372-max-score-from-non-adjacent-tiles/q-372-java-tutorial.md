# Maximum Score from Non-Adjacent Tiles in Java

#### Problem Statement
[https://codezym.com/question/372-max-score-from-non-adjacent-tiles](https://codezym.com/question/372-max-score-from-non-adjacent-tiles)

## Overview

This problem asks us to pick tiles so the total score is as high as possible, while never picking two tiles that sit right next to each other. It comes in two versions: tiles arranged in a straight line, and tiles arranged in a circle where the first and last tile also count as neighbors.


The core thinking is simple. At every tile we only have two choices, take it or skip it. If we take it, the next tile becomes off limits. This same small decision repeats at every position, and many of those smaller decisions overlap with each other. Dynamic Programming just means we solve each small decision once, remember the answer, and reuse it instead of recalculating it from scratch. Once we can solve the straight line version this way, the circular version turns out to be nothing more than solving the line version twice, with a small twist.

## Straight-Line Tiles

We build up to the final solution in two steps. First a brute force approach to see the problem clearly, then an optimized version that fixes its main weakness.

### Approach 1: Try Every Choice

At each tile, we either skip it and move to the next one, or take its score and jump two tiles ahead, since the tile right next to it becomes off limits. Trying both choices at every tile, and keeping whichever path scores higher, eventually checks every valid combination of tiles.

```java
private int bruteForce(List<Integer> tileScores, int index) {
    if (index >= tileScores.size()) {
        return 0;
    }

    // Choice 1: skip the current tile.
    int skip = bruteForce(tileScores, index + 1);

    // Choice 2: take the current tile, so the next one is off limits.
    int take = tileScores.get(index) + bruteForce(tileScores, index + 2);

    return Math.max(skip, take);
}
```

For a small example like `[5, 11, 4, 9, 2]`, this works fine. But every call branches into two more calls, so the number of calls roughly doubles with every extra tile. With up to 100 tiles, this number grows so fast that it would never finish in a reasonable time. It also solves the exact same smaller decision many times over. For example, "what is the best score from index 3 onward" gets recomputed again and again from different earlier paths, even though the answer never changes.

### Approach 2: Remember Instead of Repeating

Since the answer for "best score from this tile onward" never changes once computed, we can build it from the bottom up instead of top down, and store just enough of it to avoid repeating work.

Define the best score achievable using tiles up to some index `i`. At index `i`, we again only have two choices, skip tile `i` and keep whatever the best score was up to `i - 1`, or take tile `i` and add it to the best score up to `i - 2`, since `i - 1` is now off limits. We keep whichever is larger.

The nice part is that computing the answer at index `i` only ever needs the results from `i - 1` and `i - 2`. There is no need for a full array or any other data structure to store every previous answer, two plain integer variables are enough.

```java
public int maximumLineScore(List<Integer> tileScores) {
    return bestNonAdjacentScore(tileScores, 0, tileScores.size() - 1);
}

/**
 * Best score obtainable from tileScores[start..end] without picking two
 * neighboring tiles. Builds the answer bottom-up using two variables
 * instead of a full array, since only the previous two results matter.
 */
private int bestNonAdjacentScore(List<Integer> tileScores, int start, int end) {
    int bestTwoBack = 0; // best score ending before the previous tile
    int bestOneBack = 0; // best score ending at the previous tile

    for (int i = start; i <= end; i++) {
        int includeCurrent = bestTwoBack + tileScores.get(i);
        int excludeCurrent = bestOneBack;
        int best = Math.max(includeCurrent, excludeCurrent);

        bestTwoBack = bestOneBack;
        bestOneBack = best;
    }

    return bestOneBack;
}
```

Walking through `[5, 11, 4, 9, 2]`, this takes tile `11`, skips tile `4`, takes tile `9`, and skips tile `2`, giving `11 + 9 = 20`, which matches the expected answer.

This approach looks at each tile exactly once, so it runs in O(n) time, and it only ever keeps a couple of variables around, so it needs O(1) extra space.

## Circular Tiles

Now the first and last tile are also neighbors. This one extra rule breaks the simple version above, since by the time we decide whether to take the last tile, we would also need to remember whether the first tile was already taken.

Instead of tracking that extra piece of information, we can sidestep the problem entirely. Since the first and last tile can never both be picked, one of them is always left out of the answer. So we try both possibilities separately.

Skip the last tile entirely, and solve the remaining tiles as a plain straight line.

Skip the first tile entirely, and solve the remaining tiles as a plain straight line.

Whichever of the two gives a higher score is the answer, since both cases correctly avoid picking the first and last tile together.

There is one exception. If there is only a single tile, it has no neighbor at all, so it can always be taken.

```java
public int maximumCircleScore(List<Integer> tileScores) {
    int n = tileScores.size();

    // A single tile has no neighbor, so it can always be taken.
    if (n == 1) {
        return tileScores.get(0);
    }

    // The first and last tiles are neighbors in a circle, so at most one
    // of them can ever be picked. Try both cases as plain line problems
    // and keep the better answer.
    int skipLastTile = bestNonAdjacentScore(tileScores, 0, n - 2);
    int skipFirstTile = bestNonAdjacentScore(tileScores, 1, n - 1);

    return Math.max(skipLastTile, skipFirstTile);
}
```

Walking through `[9, 2, 7, 4]`, skipping the last tile leaves `[9, 2, 7]`, whose best line score is `9 + 7 = 16`. Skipping the first tile leaves `[2, 7, 4]`, whose best line score is `7`, since `2 + 4 = 6` is worse than just taking `7`. The larger of the two is `16`, which matches the expected answer.

This reuses the exact same `bestNonAdjacentScore` helper from the line version, just called on two different ranges, one skipping the first tile and one skipping the last. It still runs in O(n) time and O(1) extra space, since each call is only one pass over part of the list.

## Full Solution

```java
import java.util.*;

public class MaximumScoreFromNonAdjacentTiles {

    public MaximumScoreFromNonAdjacentTiles() {

    }

    public int maximumLineScore(List<Integer> tileScores) {
        return bestNonAdjacentScore(tileScores, 0, tileScores.size() - 1);
    }

    public int maximumCircleScore(List<Integer> tileScores) {
        int n = tileScores.size();

        // A single tile has no neighbor, so it can always be taken.
        if (n == 1) {
            return tileScores.get(0);
        }

        // The first and last tiles are neighbors in a circle, so at most one
        // of them can ever be picked. Try both cases as plain line problems
        // and keep the better answer.
        int skipLastTile = bestNonAdjacentScore(tileScores, 0, n - 2);
        int skipFirstTile = bestNonAdjacentScore(tileScores, 1, n - 1);

        return Math.max(skipLastTile, skipFirstTile);
    }

    /**
     * Best score obtainable from tileScores[start..end] without picking two
     * neighboring tiles. Builds the answer bottom-up using two variables
     * instead of a full array, since only the previous two results matter.
     */
    private int bestNonAdjacentScore(List<Integer> tileScores, int start, int end) {
        int bestTwoBack = 0; // best score ending before the previous tile
        int bestOneBack = 0; // best score ending at the previous tile

        for (int i = start; i <= end; i++) {
            int includeCurrent = bestTwoBack + tileScores.get(i);
            int excludeCurrent = bestOneBack;
            int best = Math.max(includeCurrent, excludeCurrent);

            bestTwoBack = bestOneBack;
            bestOneBack = best;
        }

        return bestOneBack;
    }
}
```